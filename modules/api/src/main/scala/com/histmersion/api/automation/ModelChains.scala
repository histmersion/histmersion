package com.histmersion
package api.automation

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

object ModelChains:
  object Chain:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]

  import Agents.{ModelRef, ProviderKind}

  /** One model is a chain of one, so a `roles.*` setting written before chains existed still reads. A model id never
    * contains `>`.
    */
  def parseChain(
      s: Chain.Spec,
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problem, List[ModelRef]] =
    val parts = (s).value.split('>').toList.map(_.trim)
    if parts.forall(_.isEmpty) then Right(Nil)
    else
      (parseModels((parts).map(refactorValue => ModelRef.Model.Spec(refactorValue)), known)).left.map(refactorError =>
        refactorError
      )

  def parseModels(
      refs: List[ModelRef.Model.Spec],
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problem, List[ModelRef]] =
    val parsed = refs.map((_).value.trim).filter(_.nonEmpty).map(r => ModelRef.parse(ModelRef.Wire(r), known))
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val ms = parsed.collect { case Right(m) => m }
        val dup = ms.diff(ms.distinct).map(_.wire).distinct
        if dup.nonEmpty then
          Left(Problem.Unclassified(Legacy.Text(s"a chain names a model twice: ${dup.mkString(", ")}")))
        else Right(ms)
      case errs => Left(Problem.Unclassified(Legacy.Text(errs.map(_.sentence).mkString("; "))))

  def chainText(ms: List[ModelRef]): String = (ms.map(_.wire).mkString(" > "))

  enum Measure(val wire: Measure.Wire) derives CanEqual:
    case Tokens extends Measure(Measure.Wire("tokens"))
    case Usd extends Measure(Measure.Wire("usd"))

  object Measure:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Measure] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
    given WireEnumValues[Measure]:
      def all: Array[Measure] = values
      def wire(e: Measure): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[Measure] = parse(Wire((s).value))
    given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[Measure] = WireEnum.codec
    given Schema[Measure] = WireEnum.schema

  final case class Window(seconds: Window.Seconds):
    def text: String =
      if (seconds).value % 86400 == 0 then (s"${(seconds).value / 86400}d")
      else if (seconds).value % 3600 == 0 then (s"${(seconds).value / 3600}h")
      else (s"${(seconds).value / 60}m")

  object Window:
    type MaxSecondsNumber = MaxSecondsNumber.T
    object MaxSecondsNumber extends Newtype[Long, Pure]
    type MaxSeconds = MaxSecondsValue
    type MaxSecondsValue = MaxSecondsNumber
    val MaxSecondsValue: MaxSecondsNumber.type = MaxSecondsNumber

    type Seconds = Seconds.T
    object Seconds extends Newtype[Long, Pure]

    private val Unit = Map('m' -> 60L, 'h' -> 3600L, 'd' -> 86400L, 'w' -> 604800L)
    private val Names = Map("hour" -> 3600L, "day" -> 86400L, "week" -> 604800L, "month" -> 30L * 86400L)

    val MaxSeconds: MaxSeconds = MaxSecondsNumber(90L * 86400L)

    def parse(s: LimitSpec.Window): Either[Problem, Window] =
      val t = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
      val secs = Names.get(t).orElse {
        Option.when(t.length >= 2)(t.last).flatMap(Unit.get).flatMap(u => t.init.toLongOption.filter(_ > 0).map(_ * u))
      }
      secs match
        case None =>
          Left(
            Problem.Unclassified(
              Legacy.Text(s"'$s' is not a window (hour, day, week, month, or <n>m|h|d|w such as 5h or 7d)")
            )
          )
        case Some(x) if x < 60 || x > MaxSeconds.value =>
          Left(Problem.Unclassified(Legacy.Text(s"window '$s' must be between 1 minute and 90 days")))
        case Some(x) => Right(Window(Seconds(x)))

  final case class Limit(amount: BigDecimal, measure: Measure, window: Window):
    def text: String = measure match
      case Measure.Tokens => (s"${Limit.compact(amount)} tokens/${window.text}")
      case Measure.Usd    => (s"${amount.bigDecimal.stripTrailingZeros.toPlainString} usd/${window.text}")
    def spec: LimitSpec = LimitSpec(LimitSpec.Amount(amount.toDouble), measure, LimitSpec.Window(window.text))

  object Limit:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]

    def compact(a: BigDecimal): String =
      val units = List(BigDecimal(1000000000) -> "G", BigDecimal(1000000) -> "M", BigDecimal(1000) -> "k")
      (units
        .collectFirst { case (u, s) if a >= u && (a / u).isWhole => s"${(a / u).toBigInt}$s" }
        .getOrElse(a.bigDecimal.stripTrailingZeros.toPlainString))

    private def amount(raw: String): Either[Problem, BigDecimal] =
      val t = (raw).trim.stripPrefix("$")
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
        .toRight(Problem.Unclassified(Legacy.Text(s"'$raw' is not a positive amount")))

    def parse(s: Spec): Either[Problem, Limit] =
      val t = (s).value.trim
      t.lastIndexOf('/') match
        case -1 =>
          Left(
            Problem.Unclassified(
              Legacy.Text(s"'$t' is not <amount> tokens|usd/<window> (e.g. 500k tokens/5h, 5 usd/day)")
            )
          )
        case i =>
          val (lhs, win) = (t.substring(0, i).trim, t.substring(i + 1))
          val words = lhs.split("\\s+").toList.filter(_.nonEmpty)
          val parsed: Either[Problem, (BigDecimal, Measure)] = words match
            case List(a) if a.startsWith("$") => amount((a)).map(_ -> Measure.Usd)
            case List(a, m)                   =>
              Measure
                .parse(Measure.Wire(m))
                .toRight(Problem.Unclassified(Legacy.Text(s"'$m' is neither tokens nor usd")))
                .flatMap(me => amount((a)).map(_ -> me))
            case _ =>
              Left(
                Problem.Unclassified(
                  Legacy.Text(s"'$t' is not <amount> tokens|usd/<window> (e.g. 500k tokens/5h, 5 usd/day)")
                )
              )
          for
            am <- parsed
            w <- Window.parse(LimitSpec.Window(win))
            _ <- Either.cond(
              am._2 == Measure.Usd || am._1.isWhole,
              (),
              Problem.Unclassified(Legacy.Text(s"'$t': a token count is a whole number"))
            )
          yield Limit(am._1, am._2, w)

    def fromSpec(s: LimitSpec): Either[Problem, Limit] =
      val amt = s.amount.value
      for
        w <- Window.parse(s.window)
        a <- Either.cond(
          amt > 0 && !amt.isNaN && !amt.isInfinite,
          BigDecimal(amt),
          Problem.Unclassified(Legacy.Text(s"a limit's amount must be positive, not $amt"))
        )
        _ <- Either.cond(
          s.measure == Measure.Usd || a.isWhole,
          (),
          Problem.Unclassified(Legacy.Text(s"a token count is a whole number, not $amt"))
        )
      yield Limit(a, s.measure, w)

  /** `claude-code:*` covers every model of the provider: a Claude plan is one pool, whichever model spends it. */
  enum Target derives CanEqual:
    case Model(ref: ModelRef)
    case Provider(kind: ProviderKind)

    def wire: Target.Wire = this match
      case Model(r)    => Target.Wire(r.wire.value)
      case Provider(k) => Target.Wire(s"${k.wire}:*")

    def covers(m: ModelRef): Boolean = this match
      case Model(r)    => (r == m)
      case Provider(k) => (m.provider == k)

  object Target:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Spec, known: Set[ProviderKind.Plugin.Name] = Set.empty): Either[Problem, Target] =
      val t = (s).value.trim
      if t.endsWith(":*") then
        val p = t.dropRight(2)
        ProviderKind
          .parse(com.histmersion.api.automation.ProviderKind.Wire(p), known)
          .map(Provider(_))
          .toRight(
            Problem.Unclassified(Legacy.Text(s"unknown provider '$p' in '$t' (one of: ${ProviderKind.names(known)})"))
          )
      else ModelRef.parse(ModelRef.Wire(t), known).map(Model(_))

  final case class Allowance(target: Target, limits: List[Limit], reservePct: Allowance.ReservePct):
    def text: String =
      ((limits.map(_.text) ++ Option.when((reservePct).value > 0)(s"reserve $reservePct%")).mkString(", "))

  object Allowance:
    type ReserveSpec = ReserveSpec.T
    object ReserveSpec extends Newtype[String, Pure]
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]
    type OfReservePct = ReservePct
    val OfReservePct: ReservePct.type = ReservePct

    type ReservePct = ReservePct.T
    object ReservePct extends Newtype[Int, Pure]

    def of(target: Target, limits: List[Limit], reservePct: OfReservePct): Either[Problem, Allowance] =
      if (reservePct).value < 0 || (reservePct).value > 99 then
        Left(Problem.Unclassified(Legacy.Text(s"a reserve is 0 to 99 %, not $reservePct")))
      else
        val windows =
          limits.groupBy(l => (l.measure, l.window.seconds)).collect { case (k, ls) if ls.size > 1 => ls.head }
        if windows.nonEmpty then
          Left(
            Problem.Unclassified(
              Legacy.Text(
                s"${target.wire}: two limits on the same measure and window (${windows.map(_.text).mkString(", ")})"
              )
            )
          )
        else Right(Allowance(target, limits, reservePct))

  def parseAllowances(
      s: Allowance.Spec,
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problem, List[Allowance]] =
    val entries = (s).value.split(';').toList.map(_.trim).filter(_.nonEmpty)
    val parsed = entries.map { e =>
      e.indexOf('=') match
        case -1 => Left(Problem.Unclassified(Legacy.Text(s"'$e' is not <provider:model or provider:*> = <limits>")))
        case i  =>
          parseAllowance(
            Target.Spec(e.substring(0, i)),
            (e.substring(i + 1).split(',').toList).map(refactorValue11 => Limit.Spec(refactorValue11)),
            known
          )
    }
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val as = parsed.collect { case Right(a) => a }
        val dup = as.groupBy(_.target.wire).collect { case (t, xs) if xs.size > 1 => t }
        if dup.nonEmpty then
          Left(Problem.Unclassified(Legacy.Text(s"an allowance is given twice for ${dup.mkString(", ")}")))
        else Right(as)
      case errs => Left(Problem.Unclassified(Legacy.Text(errs.map(_.sentence).mkString("; "))))

  def parseAllowance(
      target: Target.Spec,
      items: List[Limit.Spec],
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problem, Allowance] =
    val ts = items.map((_).value.trim).filter(_.nonEmpty)
    val (reserves, limits) = ts.partition(_.toLowerCase(java.util.Locale.ROOT).startsWith("reserve"))
    for
      t <- Target.parse(target, known)
      rs <- reserves match
        case Nil      => Right(Allowance.ReservePct(0))
        case r :: Nil => parseReserve(Allowance.ReserveSpec(r.drop("reserve".length)))
        case _        => Left(Problem.Unclassified(Legacy.Text(s"${t.wire}: more than one reserve")))
      ls <- limits.map(l => Limit.parse(Limit.Spec(l))).partitionMap(identity) match
        case (Nil, ok) => Right(ok)
        case (errs, _) => Left(Problem.Unclassified(Legacy.Text(s"${t.wire}: ${errs.map(_.sentence).mkString("; ")}")))
      a <- Allowance.of(t, ls, rs)
    yield a

  def parseReserve(s: Allowance.ReserveSpec): Either[Problem, Allowance.ReservePct] =
    (((s).value.trim
      .stripPrefix("=")
      .trim
      .stripSuffix("%")
      .trim
      .toIntOption
      .toRight(s"'${(s).value.trim}' is not a reserve percentage"))
      .left
      .map(refactorError4 => Problem.Unclassified(Legacy.Text(refactorError4))))
      .map(refactorValue15 => Allowance.ReservePct(refactorValue15))

  final case class Spend(at: Spend.At, tokens: Spend.Tokens, usd: BigDecimal)

  object Spend:
    type At = At.T
    object At extends Newtype[Long, Pure]
    type Tokens = Tokens.T
    object Tokens extends Newtype[Long, Pure]

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
    type Now = Now.T
    object Now extends Newtype[Long, Pure]
    type Usable = Usable.T
    object Usable extends Newtype[Boolean, Pure]
    type FreesAt = FreesAt.T
    object FreesAt extends Newtype[Long, Pure]

  private def measureOf(s: Spend, m: Measure): BigDecimal = m match
    case Measure.Tokens => BigDecimal(s.tokens.value)
    case Measure.Usd    => s.usd

  def limitState(limit: Limit, reservePct: Allowance.ReservePct, spends: Seq[Spend], now: LimitState.Now): LimitState =
    val windowMs = (limit.window.seconds).value * 1000L
    val inWindow = spends.filter(s => (s.at).value > (now).value - windowMs && (s.at).value <= (now).value).sortBy(_.at)
    val used = inWindow.map(measureOf(_, limit.measure)).sum
    val reserve = limit.amount * reservePct.value / 100
    val threshold = limit.amount - reserve
    val usable = used < threshold
    val freesAt =
      if usable || threshold <= 0 then None
      else
        inWindow
          .scanLeft((used, Option.empty[Long])) { case ((left, _), s) =>
            (left - measureOf(s, limit.measure), Some((s.at).value + windowMs))
          }
          .collectFirst { case (left, Some(at)) if left < threshold => at }
    LimitState(
      limit,
      used,
      (limit.amount - used).max(0),
      reserve,
      LimitState.Usable(usable),
      (freesAt).map(refactorValue16 => LimitState.FreesAt(refactorValue16))
    )

  def states(a: Allowance, spends: Seq[Spend], now: LimitState.Now): List[LimitState] =
    a.limits.map(limitState(_, a.reservePct, spends, now))

  def verdict(ss: List[LimitState]): (Boolean, Option[LimitState.FreesAt]) =
    val blocked = ss.filterNot((refactorCallback => ((refactorCallback.usable) == LimitState.Usable(true))))
    if blocked.isEmpty then ((true), None)
    else ((false), if blocked.exists(_.freesAt.isEmpty) then None else Some(blocked.flatMap(_.freesAt).max))

  def headroom(ss: List[LimitState]): (Option[LimitState.FreesAt], Option[BigDecimal]) =
    val tok = ss.filter(_.limit.measure == Measure.Tokens).map(_.headroom).minOption.map(_.toLong)
    val usd = ss.filter(_.limit.measure == Measure.Usd).map(_.headroom).minOption
    ((tok).map(refactorValue5 => LimitState.FreesAt(refactorValue5)), usd)

  final case class LimitSpec(amount: LimitSpec.Amount, measure: Measure, window: LimitSpec.Window)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object LimitSpec:
    type Amount = Amount.T
    object Amount extends Newtype[Double, Pure]
    type Window = Window.T
    object Window extends Newtype[String, Pure]

  final case class ChainSet(models: List[ChainSet.Models])
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ChainSet:
    export ChainEntry.Model as Models

  final case class AllowanceSet(
      target: AllowanceSet.Target,
      limits: List[LimitSpec] = Nil,
      reservePct: AllowanceSet.ReservePct = Allowance.ReservePct(0)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object AllowanceSet:
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    export Allowance.ReservePct

  enum SettingSource(val wire: SettingSource.Wire) derives CanEqual:
    case Config extends SettingSource(SettingSource.Wire("config"))
    case Saved extends SettingSource(SettingSource.Wire("saved"))

  object SettingSource:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

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
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object LimitStatus:
    type Limit = Limit.T
    object Limit extends Newtype[String, Pure]
    type Window = Window.T
    object Window extends Newtype[String, Pure]
    type Amount = Amount.T
    object Amount extends Newtype[Double, Pure]
    type Used = Used.T
    object Used extends Newtype[Double, Pure]
    type Remaining = Remaining.T
    object Remaining extends Newtype[Double, Pure]
    type Reserve = Reserve.T
    object Reserve extends Newtype[Double, Pure]
    type Usable = Usable.T
    object Usable extends Newtype[Boolean, Pure]
    type FreesAt = FreesAt.T
    object FreesAt extends Newtype[String, Pure]

  final case class AllowanceStatus(
      target: AllowanceStatus.Target,
      source: SettingSource.Wire,
      reservePct: AllowanceStatus.ReservePct,
      limits: List[LimitStatus],
      usable: AllowanceStatus.Usable,
      freesAt: Option[AllowanceStatus.FreesAt] = None,
      note: Option[AllowanceStatus.Note] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object AllowanceStatus:
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    export Allowance.ReservePct
    type Usable = Usable.T
    object Usable extends Newtype[Boolean, Pure]
    export LimitStatus.FreesAt
    type Note = Note.T
    object Note extends Newtype[String, Pure]

  final case class ChainEntry(
      model: ChainEntry.Model,
      ready: ChainEntry.Ready,
      usable: ChainEntry.Usable,
      problem: Option[Problem] = None,
      freesAt: Option[ChainEntry.FreesAt] = None,
      billing: Option[ChainEntry.Billing] = None,
      cooling: ChainEntry.Cooling = ChainEntry.Cooling(false)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ChainEntry:
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type Ready = Ready.T
    object Ready extends Newtype[Boolean, Pure]
    type Usable = Usable.T
    object Usable extends Newtype[Boolean, Pure]
    export LimitStatus.FreesAt
    type Billing = Billing.T
    object Billing extends Newtype[String, Pure]
    type Cooling = Cooling.T
    object Cooling extends Newtype[Boolean, Pure]

  final case class RoleChain(
      role: RoleChain.Role,
      source: SettingSource.Wire,
      models: List[ChainEntry],
      active: Option[RoleChain.Active] = None,
      problem: Option[Problem] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object RoleChain:
    type Role = Role.T
    object Role extends Newtype[String, Pure]
    type Active = Active.T
    object Active extends Newtype[String, Pure]
