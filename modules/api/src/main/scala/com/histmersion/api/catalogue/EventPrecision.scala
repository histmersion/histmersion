package com.histmersion
package api.catalogue

import com.histmersion.api.{TimeSpan, YearFmt, Years}
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object EventPrecision:
  type Uncertainty = Uncertainty.T
  object Uncertainty extends Newtype[Long, Pure]

  val Precise = "precise"
  val Approximate = "approximate"
  val Imprecise = "imprecise"
  val Undated = "undated"
  val all: List[EventSummary.Precision] = List(
    EventSummary.Precision(Precise),
    EventSummary.Precision(Approximate),
    EventSummary.Precision(Imprecise),
    EventSummary.Precision(Undated)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val PreciseUpTo: Uncertainty = Uncertainty((50).toLong)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val ApproximateUpTo: Uncertainty = Uncertainty((500).toLong)

  /** Widths are taken in astronomical years: historical years have no year 0. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def uncertainty(ts: TimeSpan, plusMinus: Option[EventSummary.PlusMinus]): Option[Uncertainty] =
    ts.outer.map { (a, b) =>
      def a2(y: Year) = Years.toAstronomical(y)
      def width(x: Option[Year], y: Option[Year]): Long =
        (for p <- x; q <- y yield (a2(q)).value - (a2(p)).value).getOrElse(0L).max(0)
      val derived =
        if ts.beginBy.isEmpty && ts.endFrom.isEmpty then ((a2(b)).value - (a2(a)).value).abs / 2
        else width(ts.notBefore, ts.beginBy).max(width(ts.endFrom, ts.notAfter)) / 2
      Uncertainty(derived.max(plusMinus.fold(0L)(p => (p).value).max(0)))
    }

  def of(uncertainty: Option[Uncertainty]): EventSummary.Precision =
    uncertainty match
      case None                                => EventSummary.Precision(Undated)
      case Some(u) if (u) <= (PreciseUpTo)     => EventSummary.Precision(Precise)
      case Some(u) if (u) <= (ApproximateUpTo) => EventSummary.Precision(Approximate)
      case _                                   => EventSummary.Precision(Imprecise)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def of(ts: TimeSpan, plusMinus: Option[EventSummary.PlusMinus]): EventSummary.Precision = of(
    uncertainty(ts, plusMinus)
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def mapped(precision: EventSummary.Precision): Boolean =
    (precision != EventSummary.Precision(Imprecise) && precision != EventSummary.Precision(Undated))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def label(precision: EventSummary.Precision): Option[String] =
    (precision).value match
      case Imprecise   => Some(("imprecise date"))
      case Approximate => Some(("approximate date"))
      case _           => None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def note(
      central: Option[EventSummary.Central],
      plusMinus: Option[EventSummary.PlusMinus],
      method: Option[EventSummary.DatingMethod]
  ): Option[String] =
    val m = method.map((_).value.trim).filter(_.nonEmpty).map {
      val refactorFunction = lowerFirst; (refactorValue2) => refactorFunction((refactorValue2))
    }
    central match
      case Some(c) =>
        Some(
          YearFmt(Year(c.value)) + plusMinus.filter(p => (p).value > 0).fold("")(u => s" ± $u") + m.fold("")(x =>
            s" ($x)"
          )
        )
      case None => m.map(x => (s"dated by $x"))

  private def lowerFirst(s: String): String =
    ((s)
      .split(" ", -1)
      .map(w =>
        if w.length >= 2 && w.charAt(0).isUpper && w.charAt(1).isLower then s"${w.charAt(0).toLower}${w.substring(1)}"
        else w
      )
      .mkString(" "))
