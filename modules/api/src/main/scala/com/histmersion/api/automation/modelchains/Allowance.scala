package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.problem.Problems
import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.util.problem.Problem.`++`

final case class Allowance(target: Target, limits: List[Limit], reservePct: Allowance.ReservePct):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text: String =
    ((limits.map(_.text) ++ Option.when((reservePct).value > 0)(s"reserve $reservePct%")).mkString(", "))

object Allowance:
  type ReserveSpec = ReserveSpec.T
  object ReserveSpec extends Newtype[String, Pure]
  type Spec = Spec.T
  object Spec extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type OfReservePct = ReservePct
  val OfReservePct: ReservePct.type = ReservePct

  type ReservePct = ReservePct.T
  object ReservePct extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(target: Target, limits: List[Limit], reservePct: OfReservePct): Either[Problem, Allowance] =
    if (reservePct).value < 0 || (reservePct).value > 99 then
      Left(
        Invalid.OutOfRange
          .here(TypeName.of(ReservePct), Raw(s"$reservePct"), Invalid.Bounds(Some(Raw("0")), Some(Raw("99"))))
      )
    else
      val windows =
        limits.groupBy(l => (l.measure, l.window.seconds)).collect { case (k, ls) if ls.size > 1 => ls.head }
      if windows.nonEmpty then
        Left(Invalid.Repeated(TypeName.named[Limit], Raw(s"${windows.head.text}"), Vector(Field(s"${target.wire}"))))
      else Right(Allowance(target, limits, reservePct))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseAllowances(
      s: Allowance.Spec,
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problems, List[Allowance]] =
    val entries = (s).value.split(';').toList.map(_.trim).filter(_.nonEmpty)
    val parsed = entries.map { e =>
      e.indexOf('=') match
        case -1 =>
          Left(
            Invalid.Unreadable.here(TypeName.of(Allowance.Spec), Raw(s"$e"), Vector(Raw("claude-code:* = 5 usd/day")))
          )
        case i =>
          Target.parseAllowance(
            Target.Spec(e.substring(0, i)),
            (e.substring(i + 1).split(',').toList).map(refactorValue11 => Limit.Spec(refactorValue11)),
            known
          )
    }
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val as = parsed.collect { case Right(a) => a }
        val dup = as.groupBy(_.target.wire).collect { case (t, xs) if xs.size > 1 => t }
        Problems.from(dup.toVector.map(d => Invalid.Repeated.here(TypeName.of(Target.Wire), Raw(s"$d")))).toLeft(as)
      case errs => Left(errs.reduce(_ ++ _))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseReserve(s: Allowance.ReserveSpec): Either[Problem, Allowance.ReservePct] =
    (((s).value.trim
      .stripPrefix("=")
      .trim
      .stripSuffix("%")
      .trim
      .toIntOption
      .toRight(
        Invalid.Unreadable
          .here(TypeName.of(Allowance.ReservePct), Raw(s"${(s).value.trim}"), Vector(Raw("reserve 20%")))
      )))
      .map(refactorValue15 => Allowance.ReservePct(refactorValue15))

  extension (a: Allowance)
    def states(spends: Seq[Spend], now: LimitState.Now): List[LimitState] =
      a.limits.map(_.limitState(a.reservePct, spends, now))
