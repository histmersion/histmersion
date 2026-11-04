package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.given

/** A plan is spent by every client of the account, and the provider says how much is left only as a percentage of each
  * window plus its reset, so a plan is governed by floors rather than budgets.
  */
object PlanQuota:
  import Agents.{ModelRef, ProviderKind}

  enum PoolId(val wire: String, val title: String):
    case ClaudePlan extends PoolId("claude-plan", "Claude plan")
    case ChatgptPlan extends PoolId("chatgpt-plan", "ChatGPT plan")

  object PoolId:
    def parse(s: String): Option[PoolId] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** A CLI configured to keep an API key in its environment (`use-api-key`) bills the key, not the plan. */
  final case class Logins(
      claudeCodeApiKey: Boolean = false,
      codexApiKey: Boolean = false,
      opencodeChatgpt: Boolean = false
  )

  def poolOf(m: ModelRef, logins: Logins): Option[PoolId] = m.provider match
    case ProviderKind.ClaudeCode if !logins.claudeCodeApiKey                              => Some(PoolId.ClaudePlan)
    case ProviderKind.Codex if !logins.codexApiKey                                        => Some(PoolId.ChatgptPlan)
    case ProviderKind.Opencode if logins.opencodeChatgpt && m.model.startsWith("openai/") => Some(PoolId.ChatgptPlan)
    case _                                                                                => None

  def paid(m: ModelRef, logins: Logins): Boolean =
    m.provider != ProviderKind.Local && poolOf(m, logins).isEmpty

  enum Source(val wire: String):
    case Run extends Source("run")

    case Mod extends Source("mod")

    case Interactive extends Source("interactive")

    case AppServer extends Source("app-server")

    case Limit extends Source("limit")

    case Probe extends Source("probe")

  object Source:
    def parse(s: String): Option[Source] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** Codex names its windows by length (`300m`/`10080m`): its `primary` is not always the 5-hour window. */
  final case class Reading(
      limit: String,
      kind: String,
      usedPct: Double,
      resetsAt: Option[Long],
      rejected: Boolean,
      observedAt: Long,
      source: Source,
      model: Option[String] = None
  )

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
          case n  =>
            val (k, v) = (i.substring(0, n).trim, i.substring(n + 1).trim.stripSuffix("%").trim)
            v.toIntOption
              .filter(p => p >= 0 && p <= 100)
              .toRight(s"'$i': a floor is a percentage 0-100")
              .flatMap(p => Either.cond(k.nonEmpty && !k.exists(_.isWhitespace), k -> p, s"'$i' names no window"))
      }
      parsed.collect { case Left(m) => m } match
        case Nil  => Right(Floors(parsed.collect { case Right(kv) => kv }.toMap))
        case errs => Left(errs.mkString("; "))

  final case class Rules(floors: Floors, defaultFloor: Int, staleMs: Long, marginPct: Int)

  final case class WindowState(
      reading: Reading,
      floor: Int,
      leftPct: Double,
      stale: Boolean,
      usable: Boolean,
      why: Option[String]
  )

  enum Verdict:
    case Usable(note: Option[String])

    case Blocked(reason: String, until: Option[Long])

    def usable: Boolean = this.isInstanceOf[Usable]

  def current(readings: List[Reading], now: Long, model: Option[String] = None): List[Reading] =
    readings
      .filter(r => r.resetsAt.forall(_ > now))
      .filter(r => r.model.isEmpty || model.isEmpty || r.model == model)
      .groupBy(r => (r.limit, r.kind))
      .values
      .map(_.maxBy(_.observedAt))
      .toList
      .sortBy(r => (r.limit, r.kind))

  def windowState(r: Reading, rules: Rules, now: Long): WindowState =
    val floor = rules.floors.of(r.limit, r.kind, rules.defaultFloor)
    val left = (100.0 - r.usedPct).max(0)
    val stale = now - r.observedAt > rules.staleMs
    val pct = (d: Double) =>
      java.math.BigDecimal(d).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros.toPlainString
    val why =
      if r.rejected then Some(s"${r.kind}: the provider refuses it")
      else if !stale && left <= floor then Some(s"${r.kind}: ${pct(left)}% left, floor $floor%")
      else if stale && left < floor + rules.marginPct then
        Some(
          s"${r.kind}: ${pct(left)}% left when last read, floor $floor% (+${rules.marginPct} while the reading is stale)"
        )
      else None
    WindowState(r, floor, left, stale, why.isEmpty, why)

  def verdict(
      readings: List[Reading],
      held: Boolean,
      refusal: Option[(String, Option[Long])],
      rules: Rules,
      now: Long,
      model: Option[String] = None
  ): (Verdict, List[WindowState]) =
    val states = current(readings, now, model).map(windowState(_, rules, now))
    val blocking = states.filterNot(_.usable)
    val earliest = blocking.flatMap(_.reading.resetsAt).minOption
    val v =
      if held then Verdict.Blocked("held: a person paused all agent use of this plan", None)
      else
        refusal.filter(_._2.forall(_ > now)) match
          case Some((why, until))             => Verdict.Blocked(why, until)
          case None if blocking.nonEmpty      => Verdict.Blocked(blocking.flatMap(_.why).mkString("; "), earliest)
          case None if states.isEmpty         => Verdict.Usable(Some("no reading yet: unknown until a run reads it"))
          case None if states.exists(_.stale) => Verdict.Usable(Some("the last reading is stale"))
          case None                           => Verdict.Usable(None)
    (v, states)

  enum RefreshMode(val wire: String):
    case Auto extends RefreshMode("auto")
    case Probe extends RefreshMode("probe")
    case Off extends RefreshMode("off")

  object RefreshMode:
    def parse(s: String): Option[RefreshMode] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  enum Login:
    case Present(detail: String)
    case Missing(why: String)
    case Unknown(why: String)

  enum RefreshNeed(val text: String):
    case StaleOnly extends RefreshNeed("the reading is stale")

    case NoReading extends RefreshNeed("no reading yet")

  enum RefreshTrigger(val wire: String):
    case Pick extends RefreshTrigger("pick")

    case Paused extends RefreshTrigger("paused-run")

    case Command extends RefreshTrigger("command")

    case Schedule extends RefreshTrigger("schedule")

  def refreshNeed(
      readings: List[Reading],
      held: Boolean,
      refusal: Option[(String, Option[Long])],
      rules: Rules,
      now: Long,
      model: Option[String] = None
  ): Option[RefreshNeed] =
    if held || refusal.exists(_._2.forall(_ > now)) then None
    else
      val states = current(readings, now, model).map(windowState(_, rules, now))
      if states.isEmpty then Some(RefreshNeed.NoReading)
      else
        val blocking = states.filterNot(_.usable)
        Option.when(blocking.nonEmpty && blocking.forall(s => s.stale && !s.reading.rejected && s.leftPct > s.floor))(
          RefreshNeed.StaleOnly
        )

  def throttled(lastAttempt: Option[Long], minGapMs: Long, now: Long): Boolean =
    lastAttempt.exists(t => now - t < minGapMs)

  def refreshDecision(
      need: Option[RefreshNeed],
      login: Login,
      enabled: Boolean,
      lastAttempt: Option[Long],
      minGapMs: Long,
      now: Long,
      force: Boolean = false
  ): Either[String, Option[RefreshNeed]] =
    login match
      case _ if !enabled                              => Left("refresh is off for this pool")
      case Login.Missing(why)                         => Left(s"no login: $why")
      case _ if force                                 => Right(need)
      case _ if need.isEmpty                          => Left("the reading decides by itself")
      case _ if throttled(lastAttempt, minGapMs, now) =>
        Left("a fresh reading was taken (or tried) less than stale-minutes ago")
      case _ => Right(need)

  final case class WindowStatus(
      limit: String,
      kind: String,
      usedPct: Double,
      leftPct: Double,
      floor: Int,
      usable: Boolean,
      stale: Boolean,
      resetsAt: Option[String],
      observedAt: String,
      ageSeconds: Long,
      source: String,
      why: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class PoolStatus(
      pool: String,
      title: String,
      kind: String,
      covers: List[String],
      floors: String,
      floorsSource: String,
      held: Boolean,
      usable: Boolean,
      reason: Option[String],
      until: Option[String],
      maxCallsInFlight: Int,
      windows: List[WindowStatus],
      planType: Option[String] = None,
      note: Option[String] = None,
      available: Boolean = true,
      login: Option[String] = None,
      refresh: Option[String] = None,
      refreshesToday: Int = 0,
      lastRefresh: Option[RefreshInfo] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class RefreshInfo(at: String, mode: String, trigger: String, ok: Boolean, detail: String)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class QuotaRefresh(pool: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class PoolRefreshed(pool: String, refreshed: Boolean, detail: String, status: PoolStatus)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class QuotaRefreshed(pools: List[PoolRefreshed], lifted: List[String] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class QuotaInfo(
      plans: List[PoolStatus],
      metered: List[ModelChains.AllowanceStatus],
      fallthrough: List[String],
      now: String,
      cooldowns: List[Cooldown] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Cooldown(model: String, until: String, reason: String)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class PoolSet(hold: Option[Boolean] = None, floors: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class FallthroughSet(fallthrough: Boolean) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  /** A Claude Code mod's report: `$.session.usage().rateLimits` (`{kind, percentUsed, resetsAt}`). */
  final case class ModWindow(kind: String, percentUsed: Double, resetsAt: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class ModReport(source: String, rateLimits: List[ModWindow])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class GuardAnswer(allow: Boolean, reason: Option[String] = None, resetsAt: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
