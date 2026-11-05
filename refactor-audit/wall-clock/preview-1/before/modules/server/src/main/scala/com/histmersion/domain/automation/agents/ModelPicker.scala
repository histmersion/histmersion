package com.histmersion.domain.automation.agents

import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.Transactor

import com.histmersion.server.agents.{ClaudeCode, PlanPools}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import com.histmersion.api.automation.PlanQuota

/** The model chains and allowances in effect — config (`agents.roles.*`, `agents.allowances`) with what was saved in
  * the database over it — and the choice the engine makes before every LLM call (docs/research-embedded-agents.md §15):
  * the FIRST model of the role's chain that can be built, is priced, and has more than its reserve left under every
  * allowance covering it. Usage is summed from `agent_steps` on each pick, so it is right across restarts and across
  * concurrent runs.
  *
  * `cooldown` holds models whose plan reported its own usage limit (`claude-code`, `codex`): they are passed over until
  * the time it gave, or `limit-cooldown-minutes` — or until a reading of their plan pool taken AFTER the limit was
  * reported says the pool is usable for them (#1827: the plan's limits were reset early, and only a restart brought the
  * model back; `agent quota refresh <pool>` takes such a reading). In memory on purpose — a restart is a fair moment to
  * try again.
  */
final class ModelPicker(
    ds: DataSource,
    cfg: => AgentsConfig,
    resolve: ModelRef => Either[String, (LlmClient, Billing)],
    val pools: PlanPools,
    /** The names plugin AI providers are registered under now (the pinned generation's): what a chain or an allowance
      * may name besides the host's providers.
      */
    known: () => Set[String] = () => Set.empty
):

  /** model wire → (skipped until, when its plan reported the limit) */
  private val cooldown = ConcurrentHashMap[String, (Long, Long)]()

  /** The model hit its plan's limit: skip it until `until` (else the configured cool-down). */
  def cool(model: ModelRef, until: Option[Instant], now: Long = System.currentTimeMillis()): Instant =
    val at = until
      .map(_.toEpochMilli)
      .filter(_ > now)
      .getOrElse(now + math.max(1, cfg.providers.claudeCode.limitCooldownMinutes).toLong * 60000L)
    val _ = cooldown.put(model.wire, (at, now))
    Instant.ofEpochMilli(at)

  /** Until when `model` is skipped, if it is: a cool-down whose time has passed, or that a later reading of its plan
    * pool lifts ([[ModelPicker.lifts]]), is dropped here.
    */
  private def cooling(model: ModelRef, now: Long): Option[Long] =
    Option(cooldown.get(model.wire)).flatMap { (until, since) =>
      val lifted = until > now && pools.poolOf(model).exists { p =>
        ModelPicker.lifts(since, pools.lastObserved(p), pools.verdict(p, Some(model.model), now)._1.usable)
      }
      if until > now && !lifted then Some(until)
      else
        if cooldown.remove(model.wire, (until, since)) && lifted then
          scribe.info(
            s"${model.wire}: cool-down lifted — a reading of its plan taken since its limit says it is usable"
          )
        None
    }

  /** The models skipped now because their plan reported its usage limit, with until when — after lifting those a later
    * reading of their pool clears (`agent quota`, `agent quota refresh`).
    */
  def cooldowns(now: Long = System.currentTimeMillis()): List[(String, Instant)] =
    import scala.jdk.CollectionConverters.*
    cooldown.keySet.asScala.toList.sorted.flatMap(w =>
      ModelRef
        .parse(w, known())
        .toOption
        .flatMap(m => cooling(m, now))
        .map(u => w -> Instant.ofEpochMilli(u))
    )

  // ── what is in effect ──────────────────────────────────────────────────────────────────────────

  /** A role's chain and where it comes from: saved, else `agents.roles.<role>`. */
  def chain(role: Role): Either[String, (List[ModelRef], SettingSource)] =
    Transactor(ds).read(ModelSettingsRepo.chains).get(role.wire) match
      case Some(ms) =>
        parseModels(ms, known()).map(_ -> SettingSource.Saved).left.map(m => s"the saved chain of ${role.wire}: $m")
      case None =>
        parseChain(cfg.role(role.transformInto[com.histmersion.domain.automation.AgentRole]), known())
          .map(_ -> SettingSource.Config)
          .left
          .map(m => s"agents.roles.${role.wire}: $m")

  /** The chain a call uses: the run's own model (`--model`) when it names one, else the role's. */
  def chainFor(role: Role, overrideRef: Option[String]): Either[String, List[ModelRef]] =
    overrideRef.map(_.trim).filter(_.nonEmpty) match
      case Some(ref) => parseChain(ref, known())
      case None      =>
        chain(role).map(_._1).flatMap { ms =>
          Either.cond(
            ms.nonEmpty,
            ms,
            s"no model for the ${role.wire} role — set agents.roles.${role.wire} " +
              s"(HISTMERSION_AGENTS_ROLE_${role.wire.toUpperCase.replace('-', '_')}) to provider:model[ > provider:model …], " +
              s"save one with `histmersion agent models set ${role.wire} <provider:model>…`, or pass one"
          )
        }

  /** Whether `role`'s chain may fall from a plan pool below its floor to a PAID key: saved, else
    * `agents.quota.fallthrough`. Off, the run pauses until the plan's reset instead.
    */
  def fallthrough(role: Role): Boolean =
    Transactor(ds)
      .read(PlanQuotaRepo.fallthrough)
      .getOrElse(
        role.wire,
        cfg.quota.fallthrough.split(',').map(_.trim.toLowerCase(java.util.Locale.ROOT)).contains(role.wire)
      )

  /** One allowance in effect: the parsed allowance (or why it cannot be read — then it BLOCKS what it covers: a broken
    * cap must not read as no cap) and where it comes from.
    */
  final case class Effective(target: String, allowance: Either[String, Allowance], source: SettingSource)

  /** Config's allowances with the saved ones over them (a saved target replaces config's). A config string that does
    * not parse is one problem, and caps nothing it could not read.
    */
  def allowances: (List[Effective], List[String]) =
    val (fromConfig, problem) = parseAllowances(cfg.allowances, known()) match
      case Right(as) => (as.map(a => Effective(a.target.wire, Right(a), SettingSource.Config)), Nil)
      case Left(m)   => (Nil, List(s"agents.allowances: $m"))
    val saved = Transactor(ds).read(ModelSettingsRepo.allowances).map { s =>
      val a = for
        t <- Target.parse(s.target, known())
        ss <- s.limits
        ls <- ss.map(Limit.fromSpec).partitionMap(identity) match
          case (Nil, ok) => Right(ok)
          case (errs, _) => Left(errs.mkString("; "))
        a <- Allowance.of(t, ls, s.reservePct)
      yield a
      Effective(s.target, a, SettingSource.Saved)
    }
    val savedTargets = saved.map(_.target).toSet
    (fromConfig.filterNot(e => savedTargets.contains(e.target)) ++ saved, problem)

  // ── the state of one model ─────────────────────────────────────────────────────────────────────

  /** Every allowance covering `m` with its limits' states at `now`. */
  private def covering(
      m: ModelRef,
      effective: List[Effective],
      now: Long
  ): List[(Effective, Either[String, List[LimitState]])] =
    effective
      .filter(e => e.allowance.fold(_ => Target.parse(e.target, known()).exists(_.covers(m)), _.target.covers(m)))
      .map(e => e -> e.allowance.map(a => states(a, spendsOf(a), now)))

  private def spendsOf(a: Allowance): Vector[Spend] =
    if a.limits.isEmpty then Vector.empty
    else
      val secs = a.limits.map(_.window.seconds).max
      a.target match
        case Target.Model(r)    => Transactor(ds).read(ModelSettingsRepo.spends(List(r.wire), Nil, secs))
        case Target.Provider(k) => Transactor(ds).read(ModelSettingsRepo.spends(Nil, List(k.wire + ":"), secs))

  /** Whether `m` has headroom now; if not, why and when it frees (None = not by itself). `unavailable`: it cannot be
    * offered at all (its plan's runtime has no login) — a configuration problem, not a wait. `refreshed`: what a fresh
    * reading of its pool, taken for this pick, read. `held`: the plan pool it draws on is held by a person (it frees
    * only when released).
    */
  final case class ModelState(
      usable: Boolean,
      why: Option[String],
      freesAt: Option[Long],
      headroomTokens: Option[Long],
      headroomUsd: Option[BigDecimal],
      planBlocked: Boolean = false,
      unavailable: Boolean = false,
      refreshed: Option[String] = None,
      held: Option[PlanQuota.PoolId] = None,
      cooling: Boolean = false
  )

  /** `refresh`: the caller WANTS the model (a pick), so a pool that would wait only on a stale reading, or was never
    * read, takes a fresh one first ([[PlanPools.decide]]); None = just looking.
    */
  def state(
      m: ModelRef,
      effective: List[Effective],
      now: Long,
      refresh: Option[PlanQuota.RefreshTrigger] = None
  ): ModelState =
    val cov = covering(m, effective, now)
    val broken = cov.collect { case (e, Left(why)) => s"${e.target}: $why" }
    val ss = cov.collect { case (_, Right(s)) => s }.flatten
    val (ok, frees) = verdict(ss)
    val cool = cooling(m, now)
    val (ht, hu) = headroom(ss)
    val noLogin = pools.unavailable(m, now)
    // a fresh reading only for a model that could otherwise be used: not one cooling down or capped
    val wanted = refresh.filter(_ => noLogin.isEmpty && broken.isEmpty && cool.isEmpty && ok)
    val plan = pools.poolOf(m).map { p =>
      wanted match
        case Some(t) =>
          val (v, _, r) = pools.decide(p, Some(m.model), t, now)
          (p, v, r.map(x => s"${p.title}: fresh reading (${x.text})"))
        case None => (p, pools.verdict(p, Some(m.model), now)._1, None)
    }
    val refreshed = plan.flatMap(_._3)
    if noLogin.isDefined then ModelState(false, noLogin, None, ht, hu, unavailable = true)
    else if broken.nonEmpty then
      ModelState(false, Some(s"its allowance cannot be read (${broken.mkString("; ")})"), None, ht, hu)
    else if cool.isDefined then
      ModelState(
        false,
        Some(s"${ModelPicker.CoolingWhy}; skipped until ${Instant.ofEpochMilli(cool.get)}"),
        cool,
        ht,
        hu,
        planBlocked = plan.isDefined,
        cooling = true
      )
    else if plan.exists(!_._2.usable) then
      val (p, v, _) = plan.get
      val (why, until) = v match
        case PlanQuota.Verdict.Blocked(w, u) => (w, u)
        case _                               => ("", None)
      ModelState(
        false,
        Some(s"the ${p.title} is below its floor: $why"),
        until,
        ht,
        hu,
        planBlocked = true,
        refreshed = refreshed,
        held = Option.when(pools.held(p))(p)
      )
    else if !ok then
      val which = ss
        .filterNot(_.usable)
        .map(s => s"${s.limit.text} (${fmt(s.used, s.limit.measure)} used, reserve ${fmt(s.reserve, s.limit.measure)})")
      ModelState(false, Some(s"below its allowance: ${which.mkString(", ")}"), frees, ht, hu, refreshed = refreshed)
    else ModelState(true, None, None, ht, hu, refreshed = refreshed)

  private def fmt(v: BigDecimal, m: Measure): String = m match
    case Measure.Tokens => Limit.compact(v.setScale(0, BigDecimal.RoundingMode.HALF_UP))
    case Measure.Usd    =>
      s"${v.setScale(4, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString} USD"

  // ── the choice ─────────────────────────────────────────────────────────────────────────────────

  /** The model for the next call of `role`. `refresh` is why the pick may take a fresh reading of a plan pool
    * ([[state]]); `onRefresh` hears what each one read (the run's trace).
    */
  def pick(
      role: Role,
      overrideRef: Option[String],
      now: Long = System.currentTimeMillis(),
      refresh: Option[PlanQuota.RefreshTrigger] = Some(PlanQuota.RefreshTrigger.Pick),
      onRefresh: String => Unit = _ => ()
  ): Either[PickFailure, Picked] =
    chainFor(role, overrideRef).left.map(PickFailure.Unavailable(_)).flatMap { chain =>
      val (effective, _) = allowances
      // first usable model wins; remember why the earlier ones were passed over
      val mayFallThrough = fallthrough(role)
      // `planHit`: an earlier model drew on a plan pool below its floor — a PAID model after it is
      // passed over unless the role may fall through (a weekly floor must not become an API bill)
      @annotation.tailrec
      def go(rest: List[ModelRef], notReady: List[String], exhausted: List[Exhaust], planHit: Boolean)
          : Either[PickFailure, Picked] =
        rest match
          case Nil =>
            if exhausted.isEmpty then
              Left(PickFailure.Unavailable(s"no model of the ${role.wire} chain can run: ${notReady.mkString("; ")}"))
            else
              val (frees, when) = ModelPicker.whenFrees(exhausted.map(e => (e.model, e.freesAt, e.held)))
              val what = if planHit then "below their plan floor or allowance" else "below their allowance"
              Left(
                PickFailure.Exhausted(
                  s"all models for role ${role.wire} are $what; $when (${(exhausted.map(_.text) ++ notReady).mkString("; ")})",
                  frees.map(Instant.ofEpochMilli),
                  if planHit then PauseReason.PlanQuota else PauseReason.Allowance
                )
              )
          case m :: tail if planHit && !mayFallThrough && pools.paid(m) =>
            go(
              tail,
              notReady :+ s"${m.wire}: not used — the chain does not fall from a plan to a paid key " +
                s"(histmersion agent quota fallthrough ${role.wire} on)",
              exhausted,
              planHit
            )
          case m :: tail =>
            resolve(m) match
              case Left(why)                => go(tail, notReady :+ s"${m.wire}: $why", exhausted, planHit)
              case Right((client, billing)) =>
                val st = state(m, effective, now, refresh)
                st.refreshed.foreach(onRefresh)
                if st.unavailable then
                  go(tail, notReady :+ s"${m.wire}: ${st.why.getOrElse("unavailable")}", exhausted, planHit)
                else if st.usable then
                  Right(Picked(client, billing, st.headroomTokens, st.headroomUsd, (notReady ++ exhausted.map(_.text))))
                else
                  go(
                    tail,
                    notReady,
                    exhausted :+ Exhaust(m, s"${m.wire}: ${st.why.getOrElse("not usable")}", st.freesAt, st.held),
                    planHit || st.planBlocked
                  )
      go(chain, Nil, Nil, planHit = false)
    }

  /** One model of a chain passed over for want of headroom: why, when it frees by itself (None = not by itself), and
    * the held pool it draws on, if any.
    */
  final private case class Exhaust(model: ModelRef, text: String, freesAt: Option[Long], held: Option[PlanQuota.PoolId])

  // ── the picture (`agent models`) ───────────────────────────────────────────────────────────────

  private def iso(ms: Long): String = Instant.ofEpochMilli(ms).toString

  def roleChains(now: Long): List[RoleChain] =
    val (effective, _) = allowances
    Role.values.toList.map { r =>
      chain(r) match
        case Left(m)          => RoleChain(r.wire, SettingSource.Config.wire, Nil, None, Some(m))
        case Right((ms, src)) =>
          val entries = ms.map { m =>
            resolve(m) match
              case Left(why)           => ChainEntry(m.wire, ready = false, usable = false, Some(why))
              case Right((_, billing)) =>
                val st = state(m, effective, now)
                ChainEntry(
                  m.wire,
                  ready = true,
                  st.usable,
                  st.why,
                  st.freesAt.map(iso),
                  Some(billing.describe),
                  cooling = st.cooling
                )
          }
          RoleChain(
            r.wire,
            src.wire,
            entries,
            entries.find(_.usable).map(_.model),
            Option.when(ms.isEmpty)(
              s"no model — set agents.roles.${r.wire} or `histmersion agent models set ${r.wire} …`"
            )
          )
    }

  def allowanceStatuses(now: Long): List[AllowanceStatus] =
    allowances._1.map {
      case Effective(t, Left(why), src) =>
        AllowanceStatus(
          t,
          src.wire,
          0,
          Nil,
          usable = false,
          None,
          Some(s"cannot be read, so it blocks what it covers: $why")
        )
      case Effective(t, Right(a), src) =>
        val ss = states(a, spendsOf(a), now)
        val (ok, frees) = verdict(ss)
        val note = Option.when(a.limits.isEmpty)("no limit: unlimited") orElse
          Option.when(a.target.wire.startsWith(ProviderKind.ClaudeCode.wire + ":"))(
            "counts what claude -p reported; the plan's real remaining usage cannot be seen from here"
          )
        AllowanceStatus(
          t,
          src.wire,
          a.reservePct,
          ss.map(s =>
            LimitStatus(
              s.limit.text,
              s.limit.measure,
              s.limit.window.text,
              s.limit.amount.toDouble,
              s.used.toDouble,
              s.remaining.toDouble,
              s.reserve.toDouble,
              s.usable,
              s.freesAt.map(iso)
            )
          ),
          ok,
          frees.map(iso),
          note
        )
    }

object ModelPicker:

  /** Why a model is skipped after its plan reported its usage limit — the words `agent models` and `agent quota` show,
    * so a stuck cool-down can be told from an allowance.
    */
  val CoolingWhy = "its plan reported its usage limit"

  /** Pure: does a reading of the model's plan pool lift a cool-down set at `since`? Only one OBSERVED after the limit
    * was reported (`observed`, the pool's newest reading) — the reading the limit came with, or an older one, says
    * nothing about a reset — and only when that pool's verdict for the model is usable (#1827).
    */
  def lifts(since: Long, observed: Option[Long], usable: Boolean): Boolean =
    usable && observed.exists(_ > since)

  /** When a chain whose every model is out of headroom gets one back by itself, and the clause the pause message says
    * it with: the EARLIEST time any of its models frees — a model that never frees by itself (a held pool, a reserve as
    * large as its limit) does not keep the others' resets from counting — plus the pools a person holds, which free
    * only when released. None = nothing frees by itself.
    */
  def whenFrees(models: List[(ModelRef, Option[Long], Option[PlanQuota.PoolId])]): (Option[Long], String) =
    val held = models.flatMap(_._3).distinct
    val heldText =
      held.map(p => s"the ${p.title} is held (release it with `histmersion agent quota release ${p.wire}`)")
    val earliest = models.collect { case (m, Some(t), _) => (m, t) }.minByOption(_._2)
    val when = earliest match
      case Some((m, t)) =>
        (s"next reset at ${Instant.ofEpochMilli(t)} (${m.wire}), when the run resumes by itself" :: heldText)
          .mkString("; ")
      case None if held.nonEmpty => s"no window frees by itself: ${heldText.mkString("; ")}"
      case None                  => "no window frees by itself — raise or reset the allowance"
    (earliest.map(_._2), when)

  /** The role a `plan-quota`/`allowance` pause names (the [[PickFailure.Exhausted]] message says "all models for role
    * <role> are …"), so a paused run can be asked about just that role again.
    */
  def pausedRole(error: String): Option[Role] =
    "for role ([a-z-]+) are".r.findFirstMatchIn(error).flatMap(m => Role.parse(m.group(1)))
