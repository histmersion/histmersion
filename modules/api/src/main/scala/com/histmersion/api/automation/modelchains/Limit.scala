package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import io.github.iltotore.iron.constraint.any.Pure

final case class Limit(amount: BigDecimal, measure: Measure, window: Window):
  def text: String = measure match
    case Measure.Tokens => (s"${Limit.compact(amount)} tokens/${window.text}")
    case Measure.Usd    => (s"${amount.bigDecimal.stripTrailingZeros.toPlainString} usd/${window.text}")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def spec: LimitSpec = LimitSpec(LimitSpec.Amount(amount.toDouble), measure, LimitSpec.Window(window.text))

object Limit:
  type Spec = Spec.T
  object Spec extends Newtype[String, Pure]

  def compact(a: BigDecimal): String =
    val units = List(BigDecimal(1000000000) -> "G", BigDecimal(1000000) -> "M", BigDecimal(1000) -> "k")
    (units
      .collectFirst { case (u, s) if a >= u && (a / u).isWhole => s"${(a / u).toBigInt}$s" }
      .getOrElse(a.bigDecimal.stripTrailingZeros.toPlainString))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
      .toRight(
        Invalid.Unreadable.here(TypeName.of(LimitSpec.Amount), Raw(s"$raw"), Vector(Raw("500k"), Raw("5"), Raw("$5")))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Spec): Either[Problem, Limit] =
    val t = (s).value.trim
    t.lastIndexOf('/') match
      case -1 =>
        Left(Invalid.Unreadable.here(TypeName.of(Spec), Raw(s"$t"), Vector(Raw("500k tokens/5h"), Raw("5 usd/day"))))
      case i =>
        val (lhs, win) = (t.substring(0, i).trim, t.substring(i + 1))
        val words = lhs.split("\\s+").toList.filter(_.nonEmpty)
        val parsed: Either[Problem, (BigDecimal, Measure)] = words match
          case List(a) if a.startsWith("$") => amount((a)).map(_ -> Measure.Usd)
          case List(a, m)                   =>
            Measure
              .parse(Measure.Wire(m))
              .toRight(
                Invalid.Unknown
                  .here(TypeName.named[Measure], Raw(s"$m"), Measure.values.toVector.map(v => Raw(s"${v.wire}")))
              )
              .flatMap(me => amount((a)).map(_ -> me))
          case _ =>
            Left(
              Invalid.Unreadable.here(TypeName.of(Spec), Raw(s"$t"), Vector(Raw("500k tokens/5h"), Raw("5 usd/day")))
            )
        for
          am <- parsed
          w <- Window.parse(LimitSpec.Window(win))
          _ <- Either.cond(
            am._2 == Measure.Usd || am._1.isWhole,
            (),
            Invalid.Unreadable.here(TypeName.of(LimitSpec.Amount), Raw(s"${am._1}"), Vector(Raw("500000")))
          )
        yield Limit(am._1, am._2, w)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromSpec(s: LimitSpec): Either[Problem, Limit] =
    val amt = s.amount.value
    for
      w <- Window.parse(s.window)
      a <- Either.cond(
        amt > 0 && !amt.isNaN && !amt.isInfinite,
        BigDecimal(amt),
        Invalid.OutOfRange.here(TypeName.of(LimitSpec.Amount), Raw(s"$amt"), Invalid.Bounds(Some(Raw("0")), None))
      )
      _ <- Either.cond(
        s.measure == Measure.Usd || a.isWhole,
        (),
        Invalid.Unreadable.here(TypeName.of(LimitSpec.Amount), Raw(s"$amt"), Vector(Raw("500000")))
      )
    yield Limit(a, s.measure, w)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def measureOf(s: Spend, m: Measure): BigDecimal = m match
    case Measure.Tokens => BigDecimal(s.tokens.value)
    case Measure.Usd    => s.usd

  extension (limit: Limit)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def limitState(reservePct: Allowance.ReservePct, spends: Seq[Spend], now: LimitState.Now): LimitState =
      val windowMs = (limit.window.seconds).value * 1000L
      val inWindow =
        spends.filter(s => (s.at).value > (now).value - windowMs && (s.at).value <= (now).value).sortBy(_.at)
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
        (if usable then LimitState.Usable.Usable else LimitState.Usable.Unusable),
        (freesAt).map(refactorValue16 => LimitState.FreesAt(refactorValue16))
      )
