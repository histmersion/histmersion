package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

object ModelChains:
  type ParseChainResultPart1 = ParseChainResultPart1.T
  object ParseChainResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseChainS = ParseChainS.T
  object ParseChainS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseChainKnown = ParseChainKnown.T
  object ParseChainKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseModelsResultPart1 = ParseModelsResultPart1.T
  object ParseModelsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseModelsRefs = ParseModelsRefs.T
  object ParseModelsRefs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseModelsKnown = ParseModelsKnown.T
  object ParseModelsKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainTextResult = ChainTextResult.T
  object ChainTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllowancesResultPart1 = ParseAllowancesResultPart1.T
  object ParseAllowancesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllowancesS = ParseAllowancesS.T
  object ParseAllowancesS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllowancesKnown = ParseAllowancesKnown.T
  object ParseAllowancesKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllowanceResultPart1 = ParseAllowanceResultPart1.T
  object ParseAllowanceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllowanceTarget = ParseAllowanceTarget.T
  object ParseAllowanceTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllowanceItems = ParseAllowanceItems.T
  object ParseAllowanceItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAllowanceKnown = ParseAllowanceKnown.T
  object ParseAllowanceKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseReserveResultPart1 = ParseReserveResultPart1.T
  object ParseReserveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseReserveResultPart2 = ParseReserveResultPart2.T
  object ParseReserveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseReserveS = ParseReserveS.T
  object ParseReserveS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LimitStateReservePct = LimitStateReservePct.T
  object LimitStateReservePct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LimitStateNow = LimitStateNow.T
  object LimitStateNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatesNow = StatesNow.T
  object StatesNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictResultPart1 = VerdictResultPart1.T
  object VerdictResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictResultPart2 = VerdictResultPart2.T
  object VerdictResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadroomResultPart1 = HeadroomResultPart1.T
  object HeadroomResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import Agents.{ModelRef, ProviderKind}

  /** One model is a chain of one, so a `roles.*` setting written before chains existed still reads. A model id never
    * contains `>`.
    */
  def parseChain(s: com.histmersion.api.automation.ModelChains.ParseChainS, known: Set[com.histmersion.api.automation.ModelChains.ParseChainKnown] = Set.empty): Either[com.histmersion.api.automation.ModelChains.ParseChainResultPart1, List[ModelRef]] =
    val parts = s.split('>').toList.map(_.trim)
    if parts.forall(_.isEmpty) then Right(Nil)
    else parseModels(parts, known)

  def parseModels(refs: List[com.histmersion.api.automation.ModelChains.ParseModelsRefs], known: Set[com.histmersion.api.automation.ModelChains.ParseModelsKnown] = Set.empty): Either[com.histmersion.api.automation.ModelChains.ParseModelsResultPart1, List[ModelRef]] =
    val parsed = refs.map(_.trim).filter(_.nonEmpty).map(ModelRef.parse(_, known))
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val ms = parsed.collect { case Right(m) => m }
        val dup = ms.diff(ms.distinct).map(_.wire).distinct
        if dup.nonEmpty then Left(s"a chain names a model twice: ${dup.mkString(", ")}") else Right(ms)
      case errs => Left(errs.mkString("; "))

  def chainText(ms: List[ModelRef]): com.histmersion.api.automation.ModelChains.ChainTextResult = ms.map(_.wire).mkString(" > ")

  enum Measure(val wire: String):
    case Tokens extends Measure("tokens")
    case Usd extends Measure("usd")

  object Measure:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.ModelChains.Measure.ParseS): Option[Measure] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
    given WireEnumValues[Measure]:
      def all: Array[Measure] = Measure.values
      def wire(e: Measure): com.histmersion.api.automation.ModelChains.Measure.WireResult = e.wire
      def fromWire(s: com.histmersion.api.automation.ModelChains.Measure.FromWireS): Option[Measure] = parse(s)
    given io.circe.Encoder[Measure] = WireEnum.encoder
    given io.circe.Decoder[Measure] = WireEnum.decoder
    given Schema[Measure] = WireEnum.schema

  final case class Window(seconds: Window.Seconds):
    def text: com.histmersion.api.automation.ModelChains.Window.TextResult =
      if seconds % 86400 == 0 then s"${seconds / 86400}d"
      else if seconds % 3600 == 0 then s"${seconds / 3600}h"
      else s"${seconds / 60}m"

  object Window:
    type TextResult = TextResult.T
    object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxSeconds = MaxSeconds.T
    object MaxSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Seconds = Seconds.T
    object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private val Unit = Map('m' -> 60L, 'h' -> 3600L, 'd' -> 86400L, 'w' -> 604800L)
    private val Names = Map("hour" -> 3600L, "day" -> 86400L, "week" -> 604800L, "month" -> 30L * 86400L)

    val MaxSeconds: com.histmersion.api.automation.ModelChains.Window.MaxSeconds = 90L * 86400L

    def parse(s: com.histmersion.api.automation.ModelChains.Window.ParseS): Either[com.histmersion.api.automation.ModelChains.Window.ParseResultPart1, Window] =
      val t = s.trim.toLowerCase(java.util.Locale.ROOT)
      val secs = Names.get(t).orElse {
        Option.when(t.length >= 2)(t.last).flatMap(Unit.get).flatMap(u => t.init.toLongOption.filter(_ > 0).map(_ * u))
      }
      secs match
        case None => Left(s"'$s' is not a window (hour, day, week, month, or <n>m|h|d|w such as 5h or 7d)")
        case Some(x) if x < 60 || x > MaxSeconds => Left(s"window '$s' must be between 1 minute and 90 days")
        case Some(x)                             => Right(Window(x))

  final case class Limit(amount: BigDecimal, measure: Measure, window: Window):
    def text: com.histmersion.api.automation.ModelChains.Limit.TextResult = measure match
      case Measure.Tokens => s"${Limit.compact(amount)} tokens/${window.text}"
      case Measure.Usd    => s"${amount.bigDecimal.stripTrailingZeros.toPlainString} usd/${window.text}"
    def spec: LimitSpec = LimitSpec(amount.toDouble, measure, window.text)

  object Limit:
    type TextResult = TextResult.T
    object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CompactResult = CompactResult.T
    object CompactResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AmountResultPart1 = AmountResultPart1.T
    object AmountResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AmountRaw = AmountRaw.T
    object AmountRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromSpecResultPart1 = FromSpecResultPart1.T
    object FromSpecResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def compact(a: BigDecimal): com.histmersion.api.automation.ModelChains.Limit.CompactResult =
      val units = List(BigDecimal(1000000000) -> "G", BigDecimal(1000000) -> "M", BigDecimal(1000) -> "k")
      units
        .collectFirst { case (u, s) if a >= u && (a / u).isWhole => s"${(a / u).toBigInt}$s" }
        .getOrElse(a.bigDecimal.stripTrailingZeros.toPlainString)

    private def amount(raw: com.histmersion.api.automation.ModelChains.Limit.AmountRaw): Either[com.histmersion.api.automation.ModelChains.Limit.AmountResultPart1, BigDecimal] =
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

    def parse(s: com.histmersion.api.automation.ModelChains.Limit.ParseS): Either[com.histmersion.api.automation.ModelChains.Limit.ParseResultPart1, Limit] =
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

    def fromSpec(s: LimitSpec): Either[com.histmersion.api.automation.ModelChains.Limit.FromSpecResultPart1, Limit] =
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

    def wire: com.histmersion.api.automation.ModelChains.Target.WireResult = this match
      case Model(r)    => r.wire
      case Provider(k) => s"${k.wire}:*"

    def covers(m: ModelRef): com.histmersion.api.automation.ModelChains.Target.CoversResult = this match
      case Model(r)    => r == m
      case Provider(k) => m.provider == k

  object Target:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoversResult = CoversResult.T
    object CoversResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseKnown = ParseKnown.T
    object ParseKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.ModelChains.Target.ParseS, known: Set[com.histmersion.api.automation.ModelChains.Target.ParseKnown] = Set.empty): Either[com.histmersion.api.automation.ModelChains.Target.ParseResultPart1, Target] =
      val t = s.trim
      if t.endsWith(":*") then
        val p = t.dropRight(2)
        ProviderKind
          .parse(p, known)
          .map(Provider(_))
          .toRight(s"unknown provider '$p' in '$t' (one of: ${ProviderKind.names(known)})")
      else ModelRef.parse(t, known).map(Model(_))

  final case class Allowance(target: Target, limits: List[Limit], reservePct: Allowance.ReservePct):
    def text: com.histmersion.api.automation.ModelChains.Allowance.TextResult =
      (limits.map(_.text) ++ Option.when(reservePct > 0)(s"reserve $reservePct%")).mkString(", ")

  object Allowance:
    type TextResult = TextResult.T
    object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfResultPart1 = OfResultPart1.T
    object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfReservePct = OfReservePct.T
    object OfReservePct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ReservePct = ReservePct.T
    object ReservePct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(target: Target, limits: List[Limit], reservePct: com.histmersion.api.automation.ModelChains.Allowance.OfReservePct): Either[com.histmersion.api.automation.ModelChains.Allowance.OfResultPart1, Allowance] =
      if reservePct < 0 || reservePct > 99 then Left(s"a reserve is 0 to 99 %, not $reservePct")
      else
        val windows =
          limits.groupBy(l => (l.measure, l.window.seconds)).collect { case (k, ls) if ls.size > 1 => ls.head }
        if windows.nonEmpty then
          Left(s"${target.wire}: two limits on the same measure and window (${windows.map(_.text).mkString(", ")})")
        else Right(Allowance(target, limits, reservePct))

  def parseAllowances(s: com.histmersion.api.automation.ModelChains.ParseAllowancesS, known: Set[com.histmersion.api.automation.ModelChains.ParseAllowancesKnown] = Set.empty): Either[com.histmersion.api.automation.ModelChains.ParseAllowancesResultPart1, List[Allowance]] =
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

  def parseAllowance(target: com.histmersion.api.automation.ModelChains.ParseAllowanceTarget, items: List[com.histmersion.api.automation.ModelChains.ParseAllowanceItems], known: Set[com.histmersion.api.automation.ModelChains.ParseAllowanceKnown] = Set.empty): Either[com.histmersion.api.automation.ModelChains.ParseAllowanceResultPart1, Allowance] =
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

  def parseReserve(s: com.histmersion.api.automation.ModelChains.ParseReserveS): Either[com.histmersion.api.automation.ModelChains.ParseReserveResultPart1, com.histmersion.api.automation.ModelChains.ParseReserveResultPart2] =
    s.trim.stripPrefix("=").trim.stripSuffix("%").trim.toIntOption.toRight(s"'${s.trim}' is not a reserve percentage")

  final case class Spend(at: Spend.At, tokens: Spend.Tokens, usd: BigDecimal)

  object Spend:
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tokens = Tokens.T
    object Tokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class LimitState(
      limit: Limit,
      used: BigDecimal,
      remaining: BigDecimal,
      reserve: BigDecimal,
      usable: LimitState.Usable,
      freesAt: Option[LimitState.FreesAt]
  ):
    def headroom: BigDecimal = (limit.amount - reserve - used).max(0)

  object LimitState:
    type Usable = Usable.T
    object Usable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FreesAt = FreesAt.T
    object FreesAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def measureOf(s: Spend, m: Measure): BigDecimal = m match
    case Measure.Tokens => BigDecimal(s.tokens)
    case Measure.Usd    => s.usd

  def limitState(limit: Limit, reservePct: com.histmersion.api.automation.ModelChains.LimitStateReservePct, spends: Seq[Spend], now: com.histmersion.api.automation.ModelChains.LimitStateNow): LimitState =
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

  def states(a: Allowance, spends: Seq[Spend], now: com.histmersion.api.automation.ModelChains.StatesNow): List[LimitState] =
    a.limits.map(limitState(_, a.reservePct, spends, now))

  def verdict(ss: List[LimitState]): (com.histmersion.api.automation.ModelChains.VerdictResultPart1, Option[com.histmersion.api.automation.ModelChains.VerdictResultPart2]) =
    val blocked = ss.filterNot(_.usable)
    if blocked.isEmpty then (true, None)
    else (false, if blocked.exists(_.freesAt.isEmpty) then None else Some(blocked.flatMap(_.freesAt).max))

  def headroom(ss: List[LimitState]): (Option[com.histmersion.api.automation.ModelChains.HeadroomResultPart1], Option[BigDecimal]) =
    val tok = ss.filter(_.limit.measure == Measure.Tokens).map(_.headroom).minOption.map(_.toLong)
    val usd = ss.filter(_.limit.measure == Measure.Usd).map(_.headroom).minOption
    (tok, usd)

  final case class LimitSpec(amount: LimitSpec.Amount, measure: Measure, window: LimitSpec.Window)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object LimitSpec:
    type Amount = Amount.T
    object Amount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Window = Window.T
    object Window extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ChainSet(models: List[ChainSet.Models]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object ChainSet:
    type Models = Models.T
    object Models extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class AllowanceSet(target: AllowanceSet.Target, limits: List[LimitSpec] = Nil, reservePct: AllowanceSet.ReservePct = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object AllowanceSet:
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReservePct = ReservePct.T
    object ReservePct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum SettingSource(val wire: String):
    case Config extends SettingSource("config")
    case Saved extends SettingSource("saved")

  final case class LimitStatus(
      limit: LimitStatus.Limit,
      measure: Measure,
      window: LimitStatus.Window,
      amount: LimitStatus.Amount,
      used: LimitStatus.Used,
      remaining: LimitStatus.Remaining,
      reserve: LimitStatus.Reserve,
      usable: LimitStatus.Usable,
      freesAt: Option[LimitStatus.FreesAt] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object LimitStatus:
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Window = Window.T
    object Window extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Amount = Amount.T
    object Amount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Used = Used.T
    object Used extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Remaining = Remaining.T
    object Remaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reserve = Reserve.T
    object Reserve extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Usable = Usable.T
    object Usable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FreesAt = FreesAt.T
    object FreesAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class AllowanceStatus(
      target: AllowanceStatus.Target,
      source: AllowanceStatus.Source,
      reservePct: AllowanceStatus.ReservePct,
      limits: List[LimitStatus],
      usable: AllowanceStatus.Usable,
      freesAt: Option[AllowanceStatus.FreesAt] = None,
      note: Option[AllowanceStatus.Note] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object AllowanceStatus:
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReservePct = ReservePct.T
    object ReservePct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Usable = Usable.T
    object Usable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FreesAt = FreesAt.T
    object FreesAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ChainEntry(
      model: ChainEntry.Model,
      ready: ChainEntry.Ready,
      usable: ChainEntry.Usable,
      problem: Option[ChainEntry.Problem] = None,
      freesAt: Option[ChainEntry.FreesAt] = None,
      billing: Option[ChainEntry.Billing] = None,
      cooling: ChainEntry.Cooling = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ChainEntry:
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ready = Ready.T
    object Ready extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Usable = Usable.T
    object Usable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FreesAt = FreesAt.T
    object FreesAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Billing = Billing.T
    object Billing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cooling = Cooling.T
    object Cooling extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RoleChain(
      role: RoleChain.Role,
      source: RoleChain.Source,
      models: List[ChainEntry],
      active: Option[RoleChain.Active] = None,
      problem: Option[RoleChain.Problem] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RoleChain:
    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Active = Active.T
    object Active extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
