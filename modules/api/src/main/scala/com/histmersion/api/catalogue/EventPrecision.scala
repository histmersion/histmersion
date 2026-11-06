package com.histmersion.api.catalogue

import com.histmersion.api.{TimeSpan, YearFmt, Years}
import com.histmersion.api.given
object EventPrecision:
  type Uncertainty = Uncertainty.T
  object Uncertainty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Precise = "precise"
  val Approximate = "approximate"
  val Imprecise = "imprecise"
  val Undated = "undated"
  val all: List[com.histmersion.api.catalogue.EventSummary.Precision] = List(_root_.com.histmersion.api.catalogue.EventSummary.Precision(Precise), _root_.com.histmersion.api.catalogue.EventSummary.Precision(Approximate), _root_.com.histmersion.api.catalogue.EventSummary.Precision(Imprecise), _root_.com.histmersion.api.catalogue.EventSummary.Precision(Undated))

  val PreciseUpTo: com.histmersion.api.catalogue.EventPrecision.Uncertainty = _root_.com.histmersion.api.catalogue.EventPrecision.Uncertainty((50).toLong)
  val ApproximateUpTo: com.histmersion.api.catalogue.EventPrecision.Uncertainty = _root_.com.histmersion.api.catalogue.EventPrecision.Uncertainty((500).toLong)

  /** Widths are taken in astronomical years: historical years have no year 0. */
  def uncertainty(ts: TimeSpan, plusMinus: Option[com.histmersion.api.catalogue.EventSummary.PlusMinus]): Option[com.histmersion.api.catalogue.EventPrecision.Uncertainty] =
    ts.outer.map { (a, b) =>
      def a2(y: com.histmersion.api.catalogue.Year) = Years.toAstronomical(y)
      def width(x: Option[com.histmersion.api.catalogue.Year], y: Option[com.histmersion.api.catalogue.Year]): Long = (for p <- x; q <- y yield (a2(q)).value - (a2(p)).value).getOrElse(0L).max(0)
      val derived =
        if ts.beginBy.isEmpty && ts.endFrom.isEmpty then ((a2(b)).value - (a2(a)).value).abs / 2
        else width(ts.notBefore, ts.beginBy).max(width(ts.endFrom, ts.notAfter)) / 2
      _root_.com.histmersion.api.catalogue.EventPrecision.Uncertainty(derived.max(plusMinus.fold(0L)(p => (p).value).max(0)))
    }

  def of(uncertainty: Option[com.histmersion.api.catalogue.EventPrecision.Uncertainty]): com.histmersion.api.catalogue.EventSummary.Precision =
    uncertainty match
      case None                            => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Undated)
      case Some(u) if (u).value <= (PreciseUpTo).value     => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Precise)
      case Some(u) if (u).value <= (ApproximateUpTo).value => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Approximate)
      case _                               => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Imprecise)

  def of(ts: TimeSpan, plusMinus: Option[com.histmersion.api.catalogue.EventSummary.PlusMinus]): com.histmersion.api.catalogue.EventSummary.Precision = of(uncertainty(ts, plusMinus))

  def mapped(precision: com.histmersion.api.catalogue.EventSummary.Precision): Boolean = (precision != _root_.com.histmersion.api.catalogue.EventSummary.Precision(Imprecise) && precision != _root_.com.histmersion.api.catalogue.EventSummary.Precision(Undated))

  def label(precision: com.histmersion.api.catalogue.EventSummary.Precision): Option[String] =
    (precision).value match
      case Imprecise   => Some(("imprecise date"))
      case Approximate => Some(("approximate date"))
      case _           => None

  def note(central: Option[com.histmersion.api.catalogue.EventSummary.Central], plusMinus: Option[com.histmersion.api.catalogue.EventSummary.PlusMinus], method: Option[com.histmersion.api.catalogue.EventSummary.DatingMethod]): Option[String] =
    val m = method.map((_).value.trim).filter(_.nonEmpty).map({ val refactorFunction = lowerFirst; (refactorValue2) => refactorFunction((refactorValue2)) })
    central match
      case Some(c) => Some(YearFmt(_root_.com.histmersion.api.catalogue.Year(c.value)) + plusMinus.filter(p => (p).value > 0).fold("")(u => s" ± $u") + m.fold("")(x => s" ($x)"))
      case None    => m.map(x => (s"dated by $x"))

  private def lowerFirst(s: String): String =
    ((s).split(" ", -1)
      .map(w =>
        if w.length >= 2 && w.charAt(0).isUpper && w.charAt(1).isLower then s"${w.charAt(0).toLower}${w.substring(1)}"
        else w
      )
      .mkString(" "))
