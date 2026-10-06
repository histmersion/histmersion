package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.ModelChains.*
import com.histmersion.db.{ModelSettingsRepo, PlanQuotaRepo}

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/** A model chosen for one LLM call: the client, how it is paid for (a plan pool or a price), what it may spend before an
  * allowance covering it reaches its reserve (tokens, USD; None = not limited), and the chain's
  * earlier models it passed over, with why. */
final case class Picked(client: LlmClient, billing: Billing, headroomTokens: Option[Long],
    headroomUsd: Option[BigDecimal], skipped: List[String])

/** Why no model of a role's chain can take the next call. `Exhausted` pauses the run until `freesAt`
  * (resumed by itself then); `Unavailable` is a configuration problem (no chain, no priced or buildable
  * model) and fails the step as before chains existed. */
enum PickFailure:
  case Unavailable(message: String)
  case Exhausted(message: String, freesAt: Option[Instant], reason: PauseReason = PauseReason.Allowance)

/** The model chains and allowances in effect — config (`agents.roles.*`, `agents.allowances`) with
  * what was saved in the database over it — and the choice the engine makes before every LLM call
  * (docs/research-embedded-agents.md §15): the FIRST model of the role's chain that can be built, is
  * priced, and has more than its reserve left under every allowance covering it. Usage is summed from
  * `agent_steps` on each pick, so it is right across restarts and across concurrent runs.
  *
  * `cooldown` holds models whose plan reported its own usage limit (`claude-code`): they are passed
  * over until the time it gave, or `limit-cooldown-minutes`. In memory on purpose — a restart is a
  * fair moment to try again. */
final class ModelPicker(ds: DataSource, cfg: AgentsConfig, resolve: ModelRef => Either[String, (LlmClient, Billing)],
    val pools: PlanPools):

  private val cooldown = ConcurrentHashMap[String, Long]()

  /** The model hit its plan's limit: skip it until `until` (else the configured cool-down). */
  def cool(model: ModelRef, until: Option[Instant], now: Long = System.currentTimeMillis()): Instant =
    val at = until.map(_.toEpochMilli).filter(_ > now)
      .getOrElse(now + math.max(1, cfg.providers.claudeCode.limitCooldownMinutes).toLong * 60000L)
    val _ = cooldown.put(model.wire, at)
    Instant.ofEpochMilli(at)

  private def cooling(model: ModelRef, now: Long): Option[Long] =
    Option(cooldown.get(model.wire)).filter(_ > now)

  // ── what is in effect ──────────────────────────────────────────────────────────────────────────

  /** A role's chain and where it comes from: saved, else `agents.roles.<role>`. */
  def chain(role: Role): Either[String, (List[ModelRef], SettingSource)] =
    ModelSettingsRepo.chains(ds).get(role.wire) match
      case Some(ms) => parseModels(ms).map(_ -> SettingSource.Saved).left.map(m => s"the saved chain of ${role.wire}: $m")
      case None     => parseChain(cfg.role(role)).map(_ -> SettingSource.Config)
                         .left.map(m => s"agents.roles.${role.wire}: $m")

  /** The chain a call uses: the run's own model (`--model`) when it names one, else the role's. */
  def chainFor(role: Role, overrideRef: Option[String]): Either[String, List[ModelRef]] =
    overrideRef.map(_.trim).filter(_.nonEmpty) match
      case Some(ref) => parseChain(ref)
      case None =>
        chain(role).map(_._1).flatMap { ms =>
          Either.cond(ms.nonEmpty, ms, s"no model for the ${role.wire} role — set agents.roles.${role.wire} " +
            s"(HISTMERSION_AGENTS_ROLE_${role.wire.toUpperCase.replace('-', '_')}) to provider:model[ > provider:model …], " +
            s"save one with `histmersion agent models set ${role.wire} <provider:model>…`, or pass one")
        }

  /** Whether `role`'s chain may fall from a plan pool below its floor to a PAID key: saved, else
    * `agents.quota.fallthrough`. Off, the run pauses until the plan's reset instead. */
  def fallthrough(role: Role): Boolean =
    PlanQuotaRepo.fallthrough(ds).getOrElse(role.wire,
      cfg.quota.fallthrough.split(',').map(_.trim.toLowerCase(java.util.Locale.ROOT)).contains(role.wire))

  /** One allowance in effect: the parsed allowance (or why it cannot be read — then it BLOCKS what it
    * covers: a broken cap must not read as no cap) and where it comes from. */
  final case class Effective(target: String, allowance: Either[String, Allowance], source: SettingSource)

  /** Config's allowances with the saved ones over them (a saved target replaces config's). A config
    * string that does not parse is one problem, and caps nothing it could not read. */
  def allowances: (List[Effective], List[String]) =
    val (fromConfig, problem) = parseAllowances(cfg.allowances) match
      case Right(as) => (as.map(a => Effective(a.target.wire, Right(a), SettingSource.Config)), Nil)
      case Left(m)   => (Nil, List(s"agents.allowances: $m"))
    val saved = ModelSettingsRepo.allowances(ds).map { s =>
      val a = for
        t  <- Target.parse(s.target)
        ss <- s.limits
        ls <- ss.map(Limit.fromSpec).partitionMap(identity) match
                case (Nil, ok) => Right(ok)
                case (errs, _) => Left(errs.mkString("; "))
        a  <- Allowance.of(t, ls, s.reservePct)
      yield a
      Effective(s.target, a, SettingSource.Saved)
    }
    val savedTargets = saved.map(_.target).toSet
    (fromConfig.filterNot(e => savedTargets.contains(e.target)) ++ saved, problem)

  // ── the state of one model ─────────────────────────────────────────────────────────────────────

  /** Every allowance covering `m` with its limits' states at `now`. */
  private def covering(m: ModelRef, effective: List[Effective], now: Long): List[(Effective, Either[String, List[LimitState]])] =
    effective.filter(e => e.allowance.fold(_ => Target.parse(e.target).exists(_.covers(m)), _.target.covers(m)))
      .map(e => e -> e.allowance.map(a => states(a, spendsOf(a), now)))

  private def spendsOf(a: Allowance): Vector[Spend] =
    if a.limits.isEmpty then Vector.empty
    else
      val secs = a.limits.map(_.window.seconds).max
      a.target match
        case Target.Model(r)    => ModelSettingsRepo.spends(ds, List(r.wire), Nil, secs)
        case Target.Provider(k) => ModelSettingsRepo.spends(ds, Nil, List(k.wire + ":"), secs)

  /** Whether `m` has headroom now; if not, why and when it frees (None = not by itself). */
  final case class ModelState(usable: Boolean, why: Option[String], freesAt: Option[Long],
      headroomTokens: Option[Long], headroomUsd: Option[BigDecimal], planBlocked: Boolean = false)

  def state(m: ModelRef, effective: List[Effective], now: Long): ModelState =
    val cov = covering(m, effective, now)
    val broken = cov.collect { case (e, Left(why)) => s"${e.target}: $why" }
    val ss = cov.collect { case (_, Right(s)) => s }.flatten
    val (ok, frees) = verdict(ss)
    val cool = cooling(m, now)
    val (ht, hu) = headroom(ss)
    val plan = pools.poolOf(m).map(p => p -> pools.verdict(p, Some(m.model), now)._1)
    if broken.nonEmpty then ModelState(false, Some(s"its allowance cannot be read (${broken.mkString("; ")})"), None, ht, hu)
    else if cool.isDefined then
      ModelState(false, Some(s"its plan reported its usage limit; skipped until ${Instant.ofEpochMilli(cool.get)}"), cool, ht, hu,
        planBlocked = plan.isDefined)
    else if plan.exists(!_._2.usable) then
      val (p, v) = plan.get
      val (why, until) = v match
        case PlanQuota.Verdict.Blocked(w, u) => (w, u)
        case _                               => ("", None)
      ModelState(false, Some(s"the ${p.title} is below its floor: $why"), until, ht, hu, planBlocked = true)
    else if !ok then
      val which = ss.filterNot(_.usable).map(s => s"${s.limit.text} (${fmt(s.used, s.limit.measure)} used, reserve ${fmt(s.reserve, s.limit.measure)})")
      ModelState(false, Some(s"below its allowance: ${which.mkString(", ")}"), frees, ht, hu)
    else ModelState(true, None, None, ht, hu)

  private def fmt(v: BigDecimal, m: Measure): String = m match
    case Measure.Tokens => Limit.compact(v.setScale(0, BigDecimal.RoundingMode.HALF_UP))
    case Measure.Usd    => s"${v.setScale(4, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString} USD"

  // ── the choice ─────────────────────────────────────────────────────────────────────────────────

  def pick(role: Role, overrideRef: Option[String], now: Long = System.currentTimeMillis()): Either[PickFailure, Picked] =
    chainFor(role, overrideRef).left.map(PickFailure.Unavailable(_)).flatMap { chain =>
      val (effective, _) = allowances
      // first usable model wins; remember why the earlier ones were passed over
      val mayFallThrough = fallthrough(role)
      // `planHit`: an earlier model drew on a plan pool below its floor — a PAID model after it is
      // passed over unless the role may fall through (a weekly floor must not become an API bill)
      @annotation.tailrec
      def go(rest: List[ModelRef], notReady: List[String], exhausted: List[(String, Option[Long])], planHit: Boolean): Either[PickFailure, Picked] =
        rest match
          case Nil =>
            if exhausted.isEmpty then
              Left(PickFailure.Unavailable(s"no model of the ${role.wire} chain can run: ${notReady.mkString("; ")}"))
            else
              val frees = Option.when(exhausted.forall(_._2.isDefined))(exhausted.flatMap(_._2).min)
              val when  = frees.fold("no window frees by itself — raise or reset the allowance, or release the hold")(t => s"next reset at ${Instant.ofEpochMilli(t)}")
              val what  = if planHit then "below their plan floor or allowance" else "below their allowance"
              Left(PickFailure.Exhausted(
                s"all models for role ${role.wire} are $what; $when (${(exhausted.map(_._1) ++ notReady).mkString("; ")})",
                frees.map(Instant.ofEpochMilli), if planHit then PauseReason.PlanQuota else PauseReason.Allowance))
          case m :: tail if planHit && !mayFallThrough && pools.paid(m) =>
            go(tail, notReady :+ s"${m.wire}: not used — the chain does not fall from a plan to a paid key " +
              s"(histmersion agent quota fallthrough ${role.wire} on)", exhausted, planHit)
          case m :: tail =>
            resolve(m) match
              case Left(why) => go(tail, notReady :+ s"${m.wire}: $why", exhausted, planHit)
              case Right((client, billing)) =>
                val st = state(m, effective, now)
                if st.usable then
                  Right(Picked(client, billing, st.headroomTokens, st.headroomUsd,
                    (notReady ++ exhausted.map(_._1))))
                else go(tail, notReady, exhausted :+ (s"${m.wire}: ${st.why.getOrElse("not usable")}" -> st.freesAt),
                  planHit || st.planBlocked)
      go(chain, Nil, Nil, planHit = false)
    }

  // ── the picture (`agent models`) ───────────────────────────────────────────────────────────────

  private def iso(ms: Long): String = Instant.ofEpochMilli(ms).toString

  def roleChains(now: Long): List[RoleChain] =
    val (effective, _) = allowances
    Role.values.toList.map { r =>
      chain(r) match
        case Left(m) => RoleChain(r.wire, SettingSource.Config.wire, Nil, None, Some(m))
        case Right((ms, src)) =>
          val entries = ms.map { m =>
            resolve(m) match
              case Left(why) => ChainEntry(m.wire, ready = false, usable = false, Some(why))
              case Right((_, billing)) =>
                val st = state(m, effective, now)
                ChainEntry(m.wire, ready = true, st.usable, st.why, st.freesAt.map(iso), Some(billing.describe))
          }
          RoleChain(r.wire, src.wire, entries, entries.find(_.usable).map(_.model),
            Option.when(ms.isEmpty)(s"no model — set agents.roles.${r.wire} or `histmersion agent models set ${r.wire} …`"))
    }

  def allowanceStatuses(now: Long): List[AllowanceStatus] =
    allowances._1.map {
      case Effective(t, Left(why), src) =>
        AllowanceStatus(t, src.wire, 0, Nil, usable = false, None, Some(s"cannot be read, so it blocks what it covers: $why"))
      case Effective(t, Right(a), src) =>
        val ss = states(a, spendsOf(a), now)
        val (ok, frees) = verdict(ss)
        val note = Option.when(a.limits.isEmpty)("no limit: unlimited") orElse
          Option.when(a.target.wire.startsWith(ProviderKind.ClaudeCode.wire + ":"))(
            "counts what claude -p reported; the plan's real remaining usage cannot be seen from here")
        AllowanceStatus(t, src.wire, a.reservePct, ss.map(s => LimitStatus(s.limit.text, s.limit.measure, s.limit.window.text,
          s.limit.amount.toDouble, s.used.toDouble, s.remaining.toDouble, s.reserve.toDouble, s.usable, s.freesAt.map(iso))),
          ok, frees.map(iso), note)
    }
