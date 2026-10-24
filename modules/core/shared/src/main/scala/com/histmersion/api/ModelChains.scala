package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Model chains and allowances (docs/research-model-providers.md §3, docs/research-embedded-agents.md
  * §15): per step role an ORDERED list of models, and per model (or per provider, `claude-code:*`) a
  * set of limits — tokens and/or USD per ROLLING window — with a reserve. Before every LLM call the
  * engine takes the first model of the role's chain whose every limit still has more than its reserve
  * left; when none has, the run pauses until the earliest window frees enough.
  *
  * Pure: parsing (the config grammar, which is also what the CLI accepts), and the usage maths over a
  * list of spends. The server reads the spends from `agent_steps`; nothing here does I/O. */
object ModelChains:
  import Agents.{ModelRef, ProviderKind}

  // ── chains ─────────────────────────────────────────────────────────────────────────────────────

  /** `"claude-code:sonnet > anthropic:claude-sonnet-4-5 > local:qwen3"` — tried in that order. One
    * model is a chain of one (every `roles.*` written before chains existed still reads). Blank = no
    * model. A model id never contains `>`. */
  def parseChain(s: String, known: Set[String] = Set.empty): Either[String, List[ModelRef]] =
    val parts = s.split('>').toList.map(_.trim)
    if parts.forall(_.isEmpty) then Right(Nil)
    else parseModels(parts, known)

  /** A chain given as separate model references (the REST body, the CLI's arguments). */
  def parseModels(refs: List[String], known: Set[String] = Set.empty): Either[String, List[ModelRef]] =
    val parsed = refs.map(_.trim).filter(_.nonEmpty).map(ModelRef.parse(_, known))
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val ms = parsed.collect { case Right(m) => m }
        val dup = ms.diff(ms.distinct).map(_.wire).distinct
        if dup.nonEmpty then Left(s"a chain names a model twice: ${dup.mkString(", ")}") else Right(ms)
      case errs => Left(errs.mkString("; "))

  def chainText(ms: List[ModelRef]): String = ms.map(_.wire).mkString(" > ")

  // ── allowances ─────────────────────────────────────────────────────────────────────────────────

  /** What a limit counts: tokens (input + output, cached input included — the same total the run's
    * token budget counts) or USD (priced by `agents.prices`). */
  enum Measure(val wire: String):
    case Tokens extends Measure("tokens")
    case Usd    extends Measure("usd")

  object Measure:
    def parse(s: String): Option[Measure] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
    given WireEnumValues[Measure]:
      def all: Array[Measure] = Measure.values
      def wire(e: Measure): String = e.wire
      def fromWire(s: String): Option[Measure] = parse(s)
    given io.circe.Encoder[Measure] = WireEnum.encoder
    given io.circe.Decoder[Measure] = WireEnum.decoder
    given Schema[Measure]           = WireEnum.schema

  /** A ROLLING window: the spend of the last `seconds` counts. `hour`, `day`, `week`, `month` (30 days)
    * or `<n>m|h|d|w` (`5h` — a Claude plan's session window, `7d`). */
  final case class Window(seconds: Long):
    /** The shortest exact spelling: `5h`, `7d`, `90m`; a whole number of weeks stays in days (`7d`). */
    def text: String =
      if seconds % 86400 == 0 then s"${seconds / 86400}d"
      else if seconds % 3600 == 0 then s"${seconds / 3600}h"
      else s"${seconds / 60}m"

  object Window:
    private val Unit = Map('m' -> 60L, 'h' -> 3600L, 'd' -> 86400L, 'w' -> 604800L)
    private val Names = Map("hour" -> 3600L, "day" -> 86400L, "week" -> 604800L, "month" -> 30L * 86400L)
    /** The longest window a limit may have: the spend sums read at most this far back. */
    val MaxSeconds: Long = 90L * 86400L

    def parse(s: String): Either[String, Window] =
      val t = s.trim.toLowerCase(java.util.Locale.ROOT)
      val secs = Names.get(t).orElse {
        Option.when(t.length >= 2)(t.last).flatMap(Unit.get).flatMap(u => t.init.toLongOption.filter(_ > 0).map(_ * u))
      }
      secs match
        case None => Left(s"'$s' is not a window (hour, day, week, month, or <n>m|h|d|w such as 5h or 7d)")
        case Some(x) if x < 60 || x > MaxSeconds => Left(s"window '$s' must be between 1 minute and 90 days")
        case Some(x) => Right(Window(x))

  /** At most `amount` of `measure` in any `window`. */
  final case class Limit(amount: BigDecimal, measure: Measure, window: Window):
    def text: String = measure match
      case Measure.Tokens => s"${Limit.compact(amount)} tokens/${window.text}"
      case Measure.Usd    => s"${amount.bigDecimal.stripTrailingZeros.toPlainString} usd/${window.text}"
    def spec: LimitSpec = LimitSpec(amount.toDouble, measure, window.text)

  object Limit:
    /** `2000000` → `2M`, `500000` → `500k` (exact only); anything else as digits. */
    def compact(a: BigDecimal): String =
      val units = List(BigDecimal(1000000000) -> "G", BigDecimal(1000000) -> "M", BigDecimal(1000) -> "k")
      units.collectFirst { case (u, s) if a >= u && (a / u).isWhole => s"${(a / u).toBigInt}$s" }
        .getOrElse(a.bigDecimal.stripTrailingZeros.toPlainString)

    private def amount(raw: String): Either[String, BigDecimal] =
      val t = raw.trim.stripPrefix("$")
      val (num, mult) = t.lastOption match
        case Some('k' | 'K') => (t.init, BigDecimal(1000))
        case Some('m' | 'M') => (t.init, BigDecimal(1000000))
        case Some('g' | 'G') => (t.init, BigDecimal(1000000000))
        case _               => (t, BigDecimal(1))
      scala.util.Try(BigDecimal(num.trim)).toOption.map(_ * mult).filter(_ > 0)
        .toRight(s"'$raw' is not a positive amount")

    /** `500k tokens/5h`, `2M tokens/week`, `5 usd/day`, `$5/day`. */
    def parse(s: String): Either[String, Limit] =
      val t = s.trim
      t.lastIndexOf('/') match
        case -1 => Left(s"'$t' is not <amount> tokens|usd/<window> (e.g. 500k tokens/5h, 5 usd/day)")
        case i =>
          val (lhs, win) = (t.substring(0, i).trim, t.substring(i + 1))
          val words = lhs.split("\\s+").toList.filter(_.nonEmpty)
          val parsed: Either[String, (BigDecimal, Measure)] = words match
            case List(a) if a.startsWith("$") => amount(a).map(_ -> Measure.Usd)
            case List(a, m) => Measure.parse(m).toRight(s"'$m' is neither tokens nor usd").flatMap(me => amount(a).map(_ -> me))
            case _ => Left(s"'$t' is not <amount> tokens|usd/<window> (e.g. 500k tokens/5h, 5 usd/day)")
          for
            am <- parsed
            w  <- Window.parse(win)
            _  <- Either.cond(am._2 == Measure.Usd || am._1.isWhole, (), s"'$t': a token count is a whole number")
          yield Limit(am._1, am._2, w)

    def fromSpec(s: LimitSpec): Either[String, Limit] =
      for
        w <- Window.parse(s.window)
        a <- Either.cond(s.amount > 0 && !s.amount.isNaN && !s.amount.isInfinite, BigDecimal(s.amount), s"a limit's amount must be positive, not ${s.amount}")
        _ <- Either.cond(s.measure == Measure.Usd || a.isWhole, (), s"a token count is a whole number, not ${s.amount}")
      yield Limit(a, s.measure, w)

  /** What an allowance covers: one model (`anthropic:claude-sonnet-4-5`) or every model of one provider
    * (`claude-code:*` — a Claude plan is one pool, whichever model spends it). */
  enum Target:
    case Model(ref: ModelRef)
    case Provider(kind: ProviderKind)

    def wire: String = this match
      case Model(r)    => r.wire
      case Provider(k) => s"${k.wire}:*"

    def covers(m: ModelRef): Boolean = this match
      case Model(r)    => r == m
      case Provider(k) => m.provider == k

  object Target:
    /** `known`: the names plugin providers are registered under now. */
    def parse(s: String, known: Set[String] = Set.empty): Either[String, Target] =
      val t = s.trim
      if t.endsWith(":*") then
        val p = t.dropRight(2)
        ProviderKind.parse(p, known).map(Provider(_)).toRight(s"unknown provider '$p' in '$t' (one of: ${ProviderKind.names(known)})")
      else ModelRef.parse(t, known).map(Model(_))

  /** Pause using what `target` covers once less than `reservePct` % of any limit is left. */
  final case class Allowance(target: Target, limits: List[Limit], reservePct: Int):
    def text: String =
      (limits.map(_.text) ++ Option.when(reservePct > 0)(s"reserve $reservePct%")).mkString(", ")

  object Allowance:
    def of(target: Target, limits: List[Limit], reservePct: Int): Either[String, Allowance] =
      if reservePct < 0 || reservePct > 99 then Left(s"a reserve is 0 to 99 %, not $reservePct")
      else
        val windows = limits.groupBy(l => (l.measure, l.window.seconds)).collect { case (k, ls) if ls.size > 1 => ls.head }
        if windows.nonEmpty then Left(s"${target.wire}: two limits on the same measure and window (${windows.map(_.text).mkString(", ")})")
        else Right(Allowance(target, limits, reservePct))

  /** `agents.allowances`: `"<target> = <limit>, <limit>, reserve <n>%; <target> = …"`, e.g.
    * `"claude-code:* = 2M tokens/5h, 20M tokens/7d, reserve 10%; anthropic:claude-sonnet-4-5 = 5 usd/day"`.
    * Every malformed entry is reported — a typo must not lift a cap. */
  def parseAllowances(s: String, known: Set[String] = Set.empty): Either[String, List[Allowance]] =
    val entries = s.split(';').toList.map(_.trim).filter(_.nonEmpty)
    val parsed = entries.map { e =>
      e.indexOf('=') match
        case -1 => Left(s"'$e' is not <provider:model or provider:*> = <limits>")
        case i  => parseAllowance(e.substring(0, i), e.substring(i + 1).split(',').toList, known)
    }
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val as = parsed.collect { case Right(a) => a }
        val dup = as.groupBy(_.target.wire).collect { case (t, xs) if xs.size > 1 => t }
        if dup.nonEmpty then Left(s"an allowance is given twice for ${dup.mkString(", ")}") else Right(as)
      case errs => Left(errs.mkString("; "))

  /** One allowance from its target and its items (`500k tokens/5h`, `reserve 10%`). */
  def parseAllowance(target: String, items: List[String], known: Set[String] = Set.empty): Either[String, Allowance] =
    val ts = items.map(_.trim).filter(_.nonEmpty)
    val (reserves, limits) = ts.partition(_.toLowerCase(java.util.Locale.ROOT).startsWith("reserve"))
    for
      t  <- Target.parse(target, known)
      rs <- reserves match
              case Nil      => Right(0)
              case r :: Nil => parseReserve(r.drop("reserve".length))
              case _        => Left(s"${t.wire}: more than one reserve")
      ls <- limits.map(Limit.parse).partitionMap(identity) match
              case (Nil, ok) => Right(ok)
              case (errs, _) => Left(s"${t.wire}: ${errs.mkString("; ")}")
      a  <- Allowance.of(t, ls, rs)
    yield a

  def parseReserve(s: String): Either[String, Int] =
    s.trim.stripPrefix("=").trim.stripSuffix("%").trim.toIntOption.toRight(s"'${s.trim}' is not a reserve percentage")

  // ── usage maths ────────────────────────────────────────────────────────────────────────────────

  /** One recorded spend: when (epoch ms), how many tokens, how many USD. */
  final case class Spend(at: Long, tokens: Long, usd: BigDecimal)

  /** One limit against the spends: `used` in its window, `remaining`, the `reserve` (in the limit's
    * own unit), whether the model may still be used (`used < amount − reserve`), and — when it may
    * not — `freesAt`: the first moment enough of the window's spend has aged out (None = never, a
    * reserve as large as the limit). */
  final case class LimitState(limit: Limit, used: BigDecimal, remaining: BigDecimal, reserve: BigDecimal,
      usable: Boolean, freesAt: Option[Long]):
    /** What may still be spent before the reserve is reached (0 when it is). */
    def headroom: BigDecimal = (limit.amount - reserve - used).max(0)

  private def measureOf(s: Spend, m: Measure): BigDecimal = m match
    case Measure.Tokens => BigDecimal(s.tokens)
    case Measure.Usd    => s.usd

  def limitState(limit: Limit, reservePct: Int, spends: Seq[Spend], now: Long): LimitState =
    val windowMs  = limit.window.seconds * 1000L
    val inWindow  = spends.filter(s => s.at > now - windowMs && s.at <= now).sortBy(_.at)
    val used      = inWindow.map(measureOf(_, limit.measure)).sum
    val reserve   = limit.amount * reservePct / 100
    val threshold = limit.amount - reserve
    val usable    = used < threshold
    // the window rolls: the oldest spend leaves it first. Drop spends in order until what is left is
    // under the threshold; that spend's age-out time is when the limit frees.
    val freesAt =
      if usable || threshold <= 0 then None
      else
        inWindow.scanLeft((used, Option.empty[Long])) { case ((left, _), s) =>
          (left - measureOf(s, limit.measure), Some(s.at + windowMs))
        }.collectFirst { case (left, Some(at)) if left < threshold => at }
    LimitState(limit, used, (limit.amount - used).max(0), reserve, usable, freesAt)

  /** Every limit of an allowance at `now`. */
  def states(a: Allowance, spends: Seq[Spend], now: Long): List[LimitState] =
    a.limits.map(limitState(_, a.reservePct, spends, now))

  /** Whether a set of limit states lets the model run, and if not when it may again: the LATEST of the
    * exhausted limits' free times (all of them must free); None when one never frees. */
  def verdict(ss: List[LimitState]): (Boolean, Option[Long]) =
    val blocked = ss.filterNot(_.usable)
    if blocked.isEmpty then (true, None)
    else (false, if blocked.exists(_.freesAt.isEmpty) then None else Some(blocked.flatMap(_.freesAt).max))

  /** What one LLM call may spend before any limit reaches its reserve: the smallest headroom per
    * measure (None = that measure is not limited). */
  def headroom(ss: List[LimitState]): (Option[Long], Option[BigDecimal]) =
    val tok = ss.filter(_.limit.measure == Measure.Tokens).map(_.headroom).minOption.map(_.toLong)
    val usd = ss.filter(_.limit.measure == Measure.Usd).map(_.headroom).minOption
    (tok, usd)

  // ── wire DTOs ──────────────────────────────────────────────────────────────────────────────────

  /** A limit on the wire (and in `agent_allowances.limits`): `{amount: 500000, measure: "tokens",
    * window: "5h"}`. */
  final case class LimitSpec(amount: Double, measure: Measure, window: String) derives KindlingsCodecAsObject
  object LimitSpec:
    given Schema[LimitSpec] = KindlingsSchema.derived[LimitSpec].schema

  /** `PUT /api/agents/models/{role}` — the role's chain, first choice first. */
  final case class ChainSet(models: List[String]) derives KindlingsCodecAsObject
  object ChainSet:
    given Schema[ChainSet] = KindlingsSchema.derived[ChainSet].schema

  /** `PUT /api/agents/allowances` — the allowance of one model or provider (`claude-code:*`). No limit
    * at all = unlimited (a saved empty allowance overrides one from config). */
  final case class AllowanceSet(target: String, limits: List[LimitSpec] = Nil, reservePct: Int = 0)
      derives KindlingsCodecAsObject
  object AllowanceSet:
    given Schema[AllowanceSet] = KindlingsSchema.derived[AllowanceSet].schema

  /** Where a setting comes from: the server's config file / environment, or saved in the database
    * (`agent models set`, the web UI), which wins until it is reset. */
  enum SettingSource(val wire: String):
    case Config extends SettingSource("config")
    case Saved  extends SettingSource("saved")

  /** One limit, as `agent models` shows it. Times are ISO-8601 UTC. */
  final case class LimitStatus(limit: String, measure: Measure, window: String, amount: Double, used: Double,
      remaining: Double, reserve: Double, usable: Boolean, freesAt: Option[String] = None) derives KindlingsCodecAsObject
  object LimitStatus:
    given Schema[LimitStatus] = KindlingsSchema.derived[LimitStatus].schema

  final case class AllowanceStatus(target: String, source: String, reservePct: Int, limits: List[LimitStatus],
      usable: Boolean, freesAt: Option[String] = None, note: Option[String] = None) derives KindlingsCodecAsObject
  object AllowanceStatus:
    given Schema[AllowanceStatus] = KindlingsSchema.derived[AllowanceStatus].schema

  /** One model of a role's chain: `ready` = on a plan pool or priced, and buildable (key present,
    * provider allowed); `usable` = ready AND every allowance covering it has headroom above its
    * reserve; `billing` = how a ready model is paid for (`plan pool: claude-plan`, `price: 3/15 …`). */
  final case class ChainEntry(model: String, ready: Boolean, usable: Boolean, problem: Option[String] = None,
      freesAt: Option[String] = None, billing: Option[String] = None, cooling: Boolean = false) derives KindlingsCodecAsObject
  object ChainEntry:
    given Schema[ChainEntry] = KindlingsSchema.derived[ChainEntry].schema

  /** A role's chain and the model the next call would use (`active`; none = the role would pause). */
  final case class RoleChain(role: String, source: String, models: List[ChainEntry], active: Option[String] = None,
      problem: Option[String] = None) derives KindlingsCodecAsObject
  object RoleChain:
    given Schema[RoleChain] = KindlingsSchema.derived[RoleChain].schema
