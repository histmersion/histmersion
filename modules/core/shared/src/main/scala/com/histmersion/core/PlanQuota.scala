package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Subscription pools (docs/research-plan-quotas.md §5, docs/research-agent-runtimes.md §2.5): a
  * Claude or ChatGPT plan is spent by every client of the account — the person's own sessions, other
  * machines, Histmersion's runs — and the provider says how much is left only as a percentage of each
  * window plus its reset. So a plan is governed by FLOORS: a run may draw on it only while every known
  * window has more than its floor left, and waits for the reset otherwise. API keys stay `metered`
  * (tokens/USD per rolling window, [[ModelChains]]).
  *
  * Pure: the pools, floors, readings and the verdict. The server stores the readings and asks. */
object PlanQuota:
  import Agents.{ModelRef, ProviderKind}

  /** A subscription pool. Every model a pool covers spends the same windows. */
  enum PoolId(val wire: String, val title: String):
    case ClaudePlan  extends PoolId("claude-plan", "Claude plan")
    case ChatgptPlan extends PoolId("chatgpt-plan", "ChatGPT plan")

  object PoolId:
    def parse(s: String): Option[PoolId] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** How the command-line providers are signed in: `claude-code` and `codex` draw on their plan unless
    * they are configured to keep an API key in their environment (`use-api-key` — the CLI then bills
    * the key); `opencode`'s `openai/…` models draw on the ChatGPT plan when it is signed in with one. */
  final case class Logins(claudeCodeApiKey: Boolean = false, codexApiKey: Boolean = false,
      opencodeChatgpt: Boolean = false)

  /** The plan pool a model draws on, if any: `claude-code:*` the Claude plan, `codex:*` the ChatGPT
    * plan, and `opencode:openai/…` too when opencode is signed in with ChatGPT. A CLI configured to
    * use an API key draws on no pool: it is metered like the API it bills. */
  def poolOf(m: ModelRef, logins: Logins): Option[PoolId] = m.provider match
    case ProviderKind.ClaudeCode if !logins.claudeCodeApiKey => Some(PoolId.ClaudePlan)
    case ProviderKind.Codex if !logins.codexApiKey           => Some(PoolId.ChatgptPlan)
    case ProviderKind.Opencode if logins.opencodeChatgpt && m.model.startsWith("openai/") => Some(PoolId.ChatgptPlan)
    case _ => None

  /** A model that costs money per call when its chain falls to it: an API key (directly, or through a
    * CLI configured to use one), or opencode on one. Only a local model is free and on no plan. */
  def paid(m: ModelRef, logins: Logins): Boolean =
    m.provider != ProviderKind.Local && poolOf(m, logins).isEmpty

  /** Where a reading came from. */
  enum Source(val wire: String):
    /** A `rate_limit_event` in a run's `claude -p` stream. */
    case Run         extends Source("run")
    /** The quota-guard mod of a headless run (`session.measure`). */
    case Mod         extends Source("mod")
    /** The reporting mod in the person's own Claude Code session. */
    case Interactive extends Source("interactive")
    /** `codex app-server` → `account/rateLimits/read`. */
    case AppServer   extends Source("app-server")
    /** A run that ended on the plan's own usage limit. */
    case Limit       extends Source("limit")
    /** A fresh reading taken on purpose: Claude's one-turn `claude -p` probe. */
    case Probe       extends Source("probe")

  object Source:
    def parse(s: String): Option[Source] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** One window as last read: `kind` is `five_hour`/`seven_day` (Claude) or the length, `300m`/`10080m`
    * (Codex — `primary` is not always the 5-hour window); `limit` the provider's bucket (`claude`,
    * `codex`, a fallback model's); `rejected` = the provider itself refuses this window now. Times are
    * epoch milliseconds. */
  final case class Reading(limit: String, kind: String, usedPct: Double, resetsAt: Option[Long], rejected: Boolean,
      observedAt: Long, source: Source, model: Option[String] = None)

  /** Floors per window kind: `five_hour=25, seven_day=25`, `300m=25, 10080m=20`, or limit-qualified
    * (`codex/10080m=20`). The floor is the share that must stay LEFT, in %. */
  final case class Floors(byKind: Map[String, Int]):
    def text: String = byKind.toList.sortBy(_._1).map((k, v) => s"$k=$v").mkString(", ")
    def of(limit: String, kind: String, default: Int): Int =
      byKind.get(s"$limit/$kind").orElse(byKind.get(kind)).getOrElse(default)

  object Floors:
    val Empty: Floors = Floors(Map.empty)
    def parse(s: String): Either[String, Floors] =
      val items = s.split(',').toList.map(_.trim).filter(_.nonEmpty)
      val parsed = items.map { i =>
        i.indexOf('=') match
          case -1 => Left(s"'$i' is not <window>=<percent>")
          case n =>
            val (k, v) = (i.substring(0, n).trim, i.substring(n + 1).trim.stripSuffix("%").trim)
            v.toIntOption.filter(p => p >= 0 && p <= 100).toRight(s"'$i': a floor is a percentage 0-100")
              .flatMap(p => Either.cond(k.nonEmpty && !k.exists(_.isWhitespace), k -> p, s"'$i' names no window"))
      }
      parsed.collect { case Left(m) => m } match
        case Nil  => Right(Floors(parsed.collect { case Right(kv) => kv }.toMap))
        case errs => Left(errs.mkString("; "))

  /** What the verdict needs to know besides the readings. */
  final case class Rules(floors: Floors, defaultFloor: Int, staleMs: Long, marginPct: Int)

  /** One window against its floor. `stale`: read longer ago than the rules allow — then it only lets a
    * run go on with the margin to spare. */
  final case class WindowState(reading: Reading, floor: Int, leftPct: Double, stale: Boolean, usable: Boolean, why: Option[String])

  enum Verdict:
    case Usable(note: Option[String])
    /** `until` = when the pool may be usable again (the earliest reset of a blocking window); None =
      * not by itself (a hold, or a refusal with no reset). */
    case Blocked(reason: String, until: Option[Long])

    def usable: Boolean = this.isInstanceOf[Usable]

  /** The windows that still count at `now`: a window whose reset has passed has started over, so its
    * reading says nothing any more (Claude Code drops it the same way); the newest reading per
    * (limit, kind); for one model, its own buckets and the account-wide ones. */
  def current(readings: List[Reading], now: Long, model: Option[String] = None): List[Reading] =
    readings.filter(r => r.resetsAt.forall(_ > now))
      .filter(r => r.model.isEmpty || model.isEmpty || r.model == model)
      .groupBy(r => (r.limit, r.kind)).values.map(_.maxBy(_.observedAt)).toList
      .sortBy(r => (r.limit, r.kind))

  def windowState(r: Reading, rules: Rules, now: Long): WindowState =
    val floor = rules.floors.of(r.limit, r.kind, rules.defaultFloor)
    val left  = (100.0 - r.usedPct).max(0)
    val stale = now - r.observedAt > rules.staleMs
    val pct   = (d: Double) => java.math.BigDecimal(d).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros.toPlainString
    val why =
      if r.rejected then Some(s"${r.kind}: the provider refuses it")
      else if !stale && left <= floor then Some(s"${r.kind}: ${pct(left)}% left, floor $floor%")
      else if stale && left < floor + rules.marginPct then
        Some(s"${r.kind}: ${pct(left)}% left when last read, floor $floor% (+${rules.marginPct} while the reading is stale)")
      else None
    WindowState(r, floor, left, stale, why.isEmpty, why)

  /** Whether a pool may take the next call. A hold wins over everything; a provider refusal (a
    * rejected window, Codex `ordinaryUsageAllowed: false`) blocks whatever the percentages say; then
    * every current window must keep more than its floor left. No reading at all is "unknown": usable,
    * and said so — the first run's own stream reads it. */
  def verdict(readings: List[Reading], held: Boolean, refusal: Option[(String, Option[Long])], rules: Rules,
      now: Long, model: Option[String] = None): (Verdict, List[WindowState]) =
    val states = current(readings, now, model).map(windowState(_, rules, now))
    val blocking = states.filterNot(_.usable)
    val earliest = blocking.flatMap(_.reading.resetsAt).minOption
    val v =
      if held then Verdict.Blocked("held: a person paused all agent use of this plan", None)
      else refusal.filter(_._2.forall(_ > now)) match
        case Some((why, until)) => Verdict.Blocked(why, until)
        case None if blocking.nonEmpty => Verdict.Blocked(blocking.flatMap(_.why).mkString("; "), earliest)
        case None if states.isEmpty    => Verdict.Usable(Some("no reading yet: unknown until a run reads it"))
        case None if states.exists(_.stale) => Verdict.Usable(Some("the last reading is stale"))
        case None => Verdict.Usable(None)
    (v, states)

  // ── taking a fresh reading (docs/research-plan-quotas.md §8) ─────────────────────────────────────

  /** How the Claude pool takes a fresh reading: `auto` (a zero-cost read when the CLI has one, else the
    * probe), `probe` (one minimal `claude -p` turn), `off` (only runs read it). */
  enum RefreshMode(val wire: String):
    case Auto  extends RefreshMode("auto")
    case Probe extends RefreshMode("probe")
    case Off   extends RefreshMode("off")

  object RefreshMode:
    def parse(s: String): Option[RefreshMode] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** Whether the runtime a pool is read and spent through is signed in. Only `Missing` makes the pool
    * unavailable: a check that could not be made (`Unknown`) must not take a working plan away. */
  enum Login:
    case Present(detail: String)
    case Missing(why: String)
    case Unknown(why: String)

  /** Why a pool would take a fresh reading before its verdict. */
  enum RefreshNeed(val text: String):
    /** It would pass with the stored values, and fails only because the reading is too old. */
    case StaleOnly extends RefreshNeed("the reading is stale")
    /** Nothing was ever read: the first use reads it first. */
    case NoReading extends RefreshNeed("no reading yet")

  /** What made a pool take a fresh reading. */
  enum RefreshTrigger(val wire: String):
    /** A run's model pick wanted the pool. */
    case Pick    extends RefreshTrigger("pick")
    /** The allowance check, for a run paused on the pool. */
    case Paused  extends RefreshTrigger("paused-run")
    /** A person: `agent quota refresh`. */
    case Command extends RefreshTrigger("command")

  /** Whether `p`'s verdict should wait for a fresh reading: it has none at all, or every window that
    * blocks it is blocked ONLY by its age (fresh, with the same values, it would pass). A hold or a
    * provider refusal is never a reason — a new reading would not lift either. */
  def refreshNeed(readings: List[Reading], held: Boolean, refusal: Option[(String, Option[Long])], rules: Rules,
      now: Long, model: Option[String] = None): Option[RefreshNeed] =
    if held || refusal.exists(_._2.forall(_ > now)) then None
    else
      val states = current(readings, now, model).map(windowState(_, rules, now))
      if states.isEmpty then Some(RefreshNeed.NoReading)
      else
        val blocking = states.filterNot(_.usable)
        Option.when(blocking.nonEmpty && blocking.forall(s => s.stale && !s.reading.rejected && s.leftPct > s.floor))(
          RefreshNeed.StaleOnly)

  /** A refresh at most once per `minGapMs` per pool: the last attempt (taken or failed) counts. */
  def throttled(lastAttempt: Option[Long], minGapMs: Long, now: Long): Boolean =
    lastAttempt.exists(t => now - t < minGapMs)

  /** The decision table: take a fresh reading now (with why), or not (with why). `enabled` = the pool
    * has a way to read (Claude's `refresh` not off, Codex's app-server on); `force` = a person asked
    * (`agent quota refresh`): the throttle and the need do not apply, a missing login still does. */
  def refreshDecision(need: Option[RefreshNeed], login: Login, enabled: Boolean, lastAttempt: Option[Long],
      minGapMs: Long, now: Long, force: Boolean = false): Either[String, Option[RefreshNeed]] =
    login match
      case _ if !enabled => Left("refresh is off for this pool")
      case Login.Missing(why) => Left(s"no login: $why")
      case _ if force => Right(need)
      case _ if need.isEmpty => Left("the reading decides by itself")
      case _ if throttled(lastAttempt, minGapMs, now) => Left("a fresh reading was taken (or tried) less than stale-minutes ago")
      case _ => Right(need)

  // ── wire DTOs ──────────────────────────────────────────────────────────────────────────────────

  /** One window as `agent quota` shows it. Times ISO-8601 UTC. */
  final case class WindowStatus(limit: String, kind: String, usedPct: Double, leftPct: Double, floor: Int,
      usable: Boolean, stale: Boolean, resetsAt: Option[String], observedAt: String, ageSeconds: Long, source: String,
      why: Option[String] = None) derives KindlingsCodecAsObject
  object WindowStatus:
    given Schema[WindowStatus] = KindlingsSchema.derived[WindowStatus].schema

  /** One plan pool: its windows, floors (and where they come from), hold, the verdict. */
  final case class PoolStatus(pool: String, title: String, kind: String, covers: List[String], floors: String,
      floorsSource: String, held: Boolean, usable: Boolean, reason: Option[String], until: Option[String],
      maxCallsInFlight: Int, windows: List[WindowStatus], planType: Option[String] = None,
      note: Option[String] = None, available: Boolean = true, login: Option[String] = None,
      refresh: Option[String] = None, refreshesToday: Int = 0, lastRefresh: Option[RefreshInfo] = None)
      derives KindlingsCodecAsObject
  object PoolStatus:
    given Schema[PoolStatus] = KindlingsSchema.derived[PoolStatus].schema

  /** One fresh reading a pool took: when (ISO-8601 UTC), how (`probe`, `app-server`), why (`pick`,
    * `paused-run`, `command`), whether it read anything, and what. */
  final case class RefreshInfo(at: String, mode: String, trigger: String, ok: Boolean, detail: String)
      derives KindlingsCodecAsObject
  object RefreshInfo:
    given Schema[RefreshInfo] = KindlingsSchema.derived[RefreshInfo].schema

  /** `POST /api/agents/quota/refresh` — take a fresh reading of one pool now (None = every pool). */
  final case class QuotaRefresh(pool: Option[String] = None) derives KindlingsCodecAsObject
  object QuotaRefresh:
    given Schema[QuotaRefresh] = KindlingsSchema.derived[QuotaRefresh].schema

  /** What a refresh of one pool did (`refreshed` = a reading was taken and recorded) and the pool after it. */
  final case class PoolRefreshed(pool: String, refreshed: Boolean, detail: String, status: PoolStatus)
      derives KindlingsCodecAsObject
  object PoolRefreshed:
    given Schema[PoolRefreshed] = KindlingsSchema.derived[PoolRefreshed].schema

  /** `lifted`: the models whose cool-down after a reported usage limit the fresh readings lifted. */
  final case class QuotaRefreshed(pools: List[PoolRefreshed], lifted: List[String] = Nil) derives KindlingsCodecAsObject
  object QuotaRefreshed:
    given Schema[QuotaRefreshed] = KindlingsSchema.derived[QuotaRefreshed].schema

  /** `GET /api/agents/quota` — the plan pools and the metered ones (the allowances), and the roles
    * whose chain may fall from a plan to a paid key. */
  final case class QuotaInfo(plans: List[PoolStatus], metered: List[ModelChains.AllowanceStatus], fallthrough: List[String],
      now: String, cooldowns: List[Cooldown] = Nil) derives KindlingsCodecAsObject
  object QuotaInfo:
    given Schema[QuotaInfo] = KindlingsSchema.derived[QuotaInfo].schema

  /** A model skipped because its plan reported its usage limit, until `until` — or until a reading of
    * its pool taken since says the pool is usable for it (`agent quota refresh <pool>`, #1827). */
  final case class Cooldown(model: String, until: String, reason: String) derives KindlingsCodecAsObject
  object Cooldown:
    given Schema[Cooldown] = KindlingsSchema.derived[Cooldown].schema

  /** `PUT /api/agents/quota/{pool}` — hold or release a pool, and/or save its floors (`"five_hour=30,
    * seven_day=25"`; an empty string drops the saved floors, config's apply again). */
  final case class PoolSet(hold: Option[Boolean] = None, floors: Option[String] = None) derives KindlingsCodecAsObject
  object PoolSet:
    given Schema[PoolSet] = KindlingsSchema.derived[PoolSet].schema

  /** `PUT /api/agents/quota/fallthrough/{role}` — whether the role's chain may fall from a plan below
    * its floor to a paid key. */
  final case class FallthroughSet(fallthrough: Boolean) derives KindlingsCodecAsObject
  object FallthroughSet:
    given Schema[FallthroughSet] = KindlingsSchema.derived[FallthroughSet].schema

  /** A Claude Code mod's report: `$.session.usage().rateLimits` (`{kind, percentUsed, resetsAt}`). */
  final case class ModWindow(kind: String, percentUsed: Double, resetsAt: Option[String] = None) derives KindlingsCodecAsObject
  object ModWindow:
    given Schema[ModWindow] = KindlingsSchema.derived[ModWindow].schema

  final case class ModReport(source: String, rateLimits: List[ModWindow]) derives KindlingsCodecAsObject
  object ModReport:
    given Schema[ModReport] = KindlingsSchema.derived[ModReport].schema

  /** What the mod is told: go on, or stop (and when the pool frees). */
  final case class GuardAnswer(allow: Boolean, reason: Option[String] = None, resetsAt: Option[String] = None)
      derives KindlingsCodecAsObject
  object GuardAnswer:
    given Schema[GuardAnswer] = KindlingsSchema.derived[GuardAnswer].schema
