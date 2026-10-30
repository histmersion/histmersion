package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

object ModelChains:
  import Agents.{ModelRef, ProviderKind}

  /** One model is a chain of one, so a `roles.*` setting written before chains existed still reads. A model id never
    * contains `>`.
    */
  def parseChain(s: String, known: Set[String] = Set.empty): Either[String, List[ModelRef]] =
    val parts = s.split('>').toList.map(_.trim)
    if parts.forall(_.isEmpty) then Right(Nil)
    else parseModels(parts, known)

  def parseModels(refs: List[String], known: Set[String] = Set.empty): Either[String, List[ModelRef]] =
    val parsed = refs.map(_.trim).filter(_.nonEmpty).map(ModelRef.parse(_, known))
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val ms = parsed.collect { case Right(m) => m }
        val dup = ms.diff(ms.distinct).map(_.wire).distinct
        if dup.nonEmpty then Left(s"a chain names a model twice: ${dup.mkString(", ")}") else Right(ms)
      case errs => Left(errs.mkString("; "))

  def chainText(ms: List[ModelRef]): String = ms.map(_.wire).mkString(" > ")

  enum Measure(val wire: String):
    case Tokens extends Measure("tokens")
    case Usd extends Measure("usd")

  object Measure:
    def parse(s: String): Option[Measure] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
    given WireEnumValues[Measure]:
      def all: Array[Measure] = Measure.values
      def wire(e: Measure): String = e.wire
      def fromWire(s: String): Option[Measure] = parse(s)
    given io.circe.Encoder[Measure] = WireEnum.encoder
    given io.circe.Decoder[Measure] = WireEnum.decoder
    given Schema[Measure] = WireEnum.schema

  final case class Window(seconds: Long):
    def text: String =
      if seconds % 86400 == 0 then s"${seconds / 86400}d"
      else if seconds % 3600 == 0 then s"${seconds / 3600}h"
      else s"${seconds / 60}m"

  object Window:
    private val Unit = Map('m' -> 60L, 'h' -> 3600L, 'd' -> 86400L, 'w' -> 604800L)
    private val Names = Map("hour" -> 3600L, "day" -> 86400L, "week" -> 604800L, "month" -> 30L * 86400L)

    val MaxSeconds: Long = 90L * 86400L

    def parse(s: String): Either[String, Window] =
      val t = s.trim.toLowerCase(java.util.Locale.ROOT)
      val secs = Names.get(t).orElse {
        Option.when(t.length >= 2)(t.last).flatMap(Unit.get).flatMap(u => t.init.toLongOption.filter(_ > 0).map(_ * u))
      }
      secs match
        case None => Left(s"'$s' is not a window (hour, day, week, month, or <n>m|h|d|w such as 5h or 7d)")
        case Some(x) if x < 60 || x > MaxSeconds => Left(s"window '$s' must be between 1 minute and 90 days")
        case Some(x)                             => Right(Window(x))

  final case class Limit(amount: BigDecimal, measure: Measure, window: Window):
    def text: String = measure match
      case Measure.Tokens => s"${Limit.compact(amount)} tokens/${window.text}"
      case Measure.Usd    => s"${amount.bigDecimal.stripTrailingZeros.toPlainString} usd/${window.text}"
    def spec: LimitSpec = LimitSpec(amount.toDouble, measure, window.text)

  object Limit:
    def compact(a: BigDecimal): String =
      val units = List(BigDecimal(1000000000) -> "G", BigDecimal(1000000) -> "M", BigDecimal(1000) -> "k")
      units
        .collectFirst { case (u, s) if a >= u && (a / u).isWhole => s"${(a / u).toBigInt}$s" }
        .getOrElse(a.bigDecimal.stripTrailingZeros.toPlainString)

    private def amount(raw: String): Either[String, BigDecimal] =
      val t = raw.trim.stripPrefix("$")
      val (num, mult) = t.lastOption match
        case Some('k' | 'K') => (t.init, BigDecimal(1000))
        case Some('m' | 'M') => (t.init, BigDecimal(1000000))
        case Some('g' | 'G') => (t.init, BigDecimal(1000000000))
        case _               => (t, BigDecimal(1))
      scala.util
        .Try(BigDecimal(num.trim))
        .toOption
        .map(_ * mult)
        .filter(_ > 0)
        .toRight(s"'$raw' is not a positive amount")

    def parse(s: String): Either[String, Limit] =
      val t = s.trim
      t.lastIndexOf('/') match
        case -1 => Left(s"'$t' is not <amount> tokens|usd/<window> (e.g. 500k tokens/5h, 5 usd/day)")
        case i  =>
          val (lhs, win) = (t.substring(0, i).trim, t.substring(i + 1))
          val words = lhs.split("\\s+").toList.filter(_.nonEmpty)
          val parsed: Either[String, (BigDecimal, Measure)] = words match
            case List(a) if a.startsWith("$") => amount(a).map(_ -> Measure.Usd)
            case List(a, m)                   =>
              Measure.parse(m).toRight(s"'$m' is neither tokens nor usd").flatMap(me => amount(a).map(_ -> me))
            case _ => Left(s"'$t' is not <amount> tokens|usd/<window> (e.g. 500k tokens/5h, 5 usd/day)")
          for
            am <- parsed
            w <- Window.parse(win)
            _ <- Either.cond(am._2 == Measure.Usd || am._1.isWhole, (), s"'$t': a token count is a whole number")
          yield Limit(am._1, am._2, w)

    def fromSpec(s: LimitSpec): Either[String, Limit] =
      for
        w <- Window.parse(s.window)
        a <- Either.cond(
          s.amount > 0 && !s.amount.isNaN && !s.amount.isInfinite,
          BigDecimal(s.amount),
          s"a limit's amount must be positive, not ${s.amount}"
        )
        _ <- Either.cond(s.measure == Measure.Usd || a.isWhole, (), s"a token count is a whole number, not ${s.amount}")
      yield Limit(a, s.measure, w)

  /** `claude-code:*` covers every model of the provider: a Claude plan is one pool, whichever model spends it. */
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
    def parse(s: String, known: Set[String] = Set.empty): Either[String, Target] =
      val t = s.trim
      if t.endsWith(":*") then
        val p = t.dropRight(2)
        ProviderKind
          .parse(p, known)
          .map(Provider(_))
          .toRight(s"unknown provider '$p' in '$t' (one of: ${ProviderKind.names(known)})")
      else ModelRef.parse(t, known).map(Model(_))

  final case class Allowance(target: Target, limits: List[Limit], reservePct: Int):
    def text: String =
      (limits.map(_.text) ++ Option.when(reservePct > 0)(s"reserve $reservePct%")).mkString(", ")

  object Allowance:
    def of(target: Target, limits: List[Limit], reservePct: Int): Either[String, Allowance] =
      if reservePct < 0 || reservePct > 99 then Left(s"a reserve is 0 to 99 %, not $reservePct")
      else
        val windows =
          limits.groupBy(l => (l.measure, l.window.seconds)).collect { case (k, ls) if ls.size > 1 => ls.head }
        if windows.nonEmpty then
          Left(s"${target.wire}: two limits on the same measure and window (${windows.map(_.text).mkString(", ")})")
        else Right(Allowance(target, limits, reservePct))

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

  def parseAllowance(target: String, items: List[String], known: Set[String] = Set.empty): Either[String, Allowance] =
    val ts = items.map(_.trim).filter(_.nonEmpty)
    val (reserves, limits) = ts.partition(_.toLowerCase(java.util.Locale.ROOT).startsWith("reserve"))
    for
      t <- Target.parse(target, known)
      rs <- reserves match
        case Nil      => Right(0)
        case r :: Nil => parseReserve(r.drop("reserve".length))
        case _        => Left(s"${t.wire}: more than one reserve")
      ls <- limits.map(Limit.parse).partitionMap(identity) match
        case (Nil, ok) => Right(ok)
        case (errs, _) => Left(s"${t.wire}: ${errs.mkString("; ")}")
      a <- Allowance.of(t, ls, rs)
    yield a

  def parseReserve(s: String): Either[String, Int] =
    s.trim.stripPrefix("=").trim.stripSuffix("%").trim.toIntOption.toRight(s"'${s.trim}' is not a reserve percentage")

  final case class Spend(at: Long, tokens: Long, usd: BigDecimal)

  final case class LimitState(
      limit: Limit,
      used: BigDecimal,
      remaining: BigDecimal,
      reserve: BigDecimal,
      usable: Boolean,
      freesAt: Option[Long]
  ):
    def headroom: BigDecimal = (limit.amount - reserve - used).max(0)

  private def measureOf(s: Spend, m: Measure): BigDecimal = m match
    case Measure.Tokens => BigDecimal(s.tokens)
    case Measure.Usd    => s.usd

  def limitState(limit: Limit, reservePct: Int, spends: Seq[Spend], now: Long): LimitState =
    val windowMs = limit.window.seconds * 1000L
    val inWindow = spends.filter(s => s.at > now - windowMs && s.at <= now).sortBy(_.at)
    val used = inWindow.map(measureOf(_, limit.measure)).sum
    val reserve = limit.amount * reservePct / 100
    val threshold = limit.amount - reserve
    val usable = used < threshold
    val freesAt =
      if usable || threshold <= 0 then None
      else
        inWindow
          .scanLeft((used, Option.empty[Long])) { case ((left, _), s) =>
            (left - measureOf(s, limit.measure), Some(s.at + windowMs))
          }
          .collectFirst { case (left, Some(at)) if left < threshold => at }
    LimitState(limit, used, (limit.amount - used).max(0), reserve, usable, freesAt)

  def states(a: Allowance, spends: Seq[Spend], now: Long): List[LimitState] =
    a.limits.map(limitState(_, a.reservePct, spends, now))

  def verdict(ss: List[LimitState]): (Boolean, Option[Long]) =
    val blocked = ss.filterNot(_.usable)
    if blocked.isEmpty then (true, None)
    else (false, if blocked.exists(_.freesAt.isEmpty) then None else Some(blocked.flatMap(_.freesAt).max))

  def headroom(ss: List[LimitState]): (Option[Long], Option[BigDecimal]) =
    val tok = ss.filter(_.limit.measure == Measure.Tokens).map(_.headroom).minOption.map(_.toLong)
    val usd = ss.filter(_.limit.measure == Measure.Usd).map(_.headroom).minOption
    (tok, usd)

  final case class LimitSpec(amount: Double, measure: Measure, window: String)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object LimitSpec:
    given Schema[LimitSpec] = summon[KindlingsSchema[LimitSpec]].schema

  final case class ChainSet(models: List[String]) derives KindlingsCodecAsObject, KindlingsSchema
  object ChainSet:
    given Schema[ChainSet] = summon[KindlingsSchema[ChainSet]].schema

  final case class AllowanceSet(target: String, limits: List[LimitSpec] = Nil, reservePct: Int = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object AllowanceSet:
    given Schema[AllowanceSet] = summon[KindlingsSchema[AllowanceSet]].schema

  enum SettingSource(val wire: String):
    case Config extends SettingSource("config")
    case Saved extends SettingSource("saved")

  final case class LimitStatus(
      limit: String,
      measure: Measure,
      window: String,
      amount: Double,
      used: Double,
      remaining: Double,
      reserve: Double,
      usable: Boolean,
      freesAt: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object LimitStatus:
    given Schema[LimitStatus] = summon[KindlingsSchema[LimitStatus]].schema

  final case class AllowanceStatus(
      target: String,
      source: String,
      reservePct: Int,
      limits: List[LimitStatus],
      usable: Boolean,
      freesAt: Option[String] = None,
      note: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object AllowanceStatus:
    given Schema[AllowanceStatus] = summon[KindlingsSchema[AllowanceStatus]].schema

  final case class ChainEntry(
      model: String,
      ready: Boolean,
      usable: Boolean,
      problem: Option[String] = None,
      freesAt: Option[String] = None,
      billing: Option[String] = None,
      cooling: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ChainEntry:
    given Schema[ChainEntry] = summon[KindlingsSchema[ChainEntry]].schema

  final case class RoleChain(
      role: String,
      source: String,
      models: List[ChainEntry],
      active: Option[String] = None,
      problem: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RoleChain:
    given Schema[RoleChain] = summon[KindlingsSchema[RoleChain]].schema
