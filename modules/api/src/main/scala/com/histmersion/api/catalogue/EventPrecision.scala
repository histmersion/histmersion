package com.histmersion.api.catalogue

import com.histmersion.api.{TimeSpan, YearFmt, Years}
import com.histmersion.api.given
object EventPrecision:
  type ApproximateUpToNumber = ApproximateUpToNumber.T
  object ApproximateUpToNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bound = Bound.T
  object Bound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreciseUpToNumber = PreciseUpToNumber.T
  object PreciseUpToNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Uncertainty = Uncertainty.T
  object Uncertainty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PreciseUpTo = PreciseUpToValue
  type PreciseUpToValue = com.histmersion.api.catalogue.EventPrecision.PreciseUpToNumber
  val PreciseUpToValue: _root_.com.histmersion.api.catalogue.EventPrecision.PreciseUpToNumber.type = _root_.com.histmersion.api.catalogue.EventPrecision.PreciseUpToNumber
  type ApproximateUpTo = ApproximateUpToValue
  type ApproximateUpToValue = com.histmersion.api.catalogue.EventPrecision.ApproximateUpToNumber
  val ApproximateUpToValue: _root_.com.histmersion.api.catalogue.EventPrecision.ApproximateUpToNumber.type = _root_.com.histmersion.api.catalogue.EventPrecision.ApproximateUpToNumber
  type OfPlusMinus = _root_.com.histmersion.api.catalogue.EventSummary.PlusMinus
  val OfPlusMinus: _root_.com.histmersion.api.catalogue.EventSummary.PlusMinus.type = _root_.com.histmersion.api.catalogue.EventSummary.PlusMinus
  type LabelPrecision = _root_.com.histmersion.api.catalogue.EventSummary.Precision
  val LabelPrecision: _root_.com.histmersion.api.catalogue.EventSummary.Precision.type = _root_.com.histmersion.api.catalogue.EventSummary.Precision

  val Precise = "precise"
  val Approximate = "approximate"
  val Imprecise = "imprecise"
  val Undated = "undated"
  val all: List[com.histmersion.api.catalogue.EventSummary.Precision] = List(_root_.com.histmersion.api.catalogue.EventSummary.Precision(Precise), _root_.com.histmersion.api.catalogue.EventSummary.Precision(Approximate), _root_.com.histmersion.api.catalogue.EventSummary.Precision(Imprecise), _root_.com.histmersion.api.catalogue.EventSummary.Precision(Undated))

  val PreciseUpTo: com.histmersion.api.catalogue.EventPrecision.PreciseUpTo = _root_.com.histmersion.api.catalogue.EventPrecision.PreciseUpToNumber((50).toLong)
  val ApproximateUpTo: com.histmersion.api.catalogue.EventPrecision.ApproximateUpTo = _root_.com.histmersion.api.catalogue.EventPrecision.ApproximateUpToNumber((500).toLong)

  /** Widths are taken in astronomical years: historical years have no year 0. */
  def uncertainty(ts: TimeSpan, plusMinus: Option[com.histmersion.api.catalogue.EventSummary.PlusMinus]): Option[com.histmersion.api.catalogue.EventPrecision.Uncertainty] =
    ts.outer.map { (a, b) =>
      def a2(y: com.histmersion.api.catalogue.Year) = Years.toAstronomical(y)
      def width(x: Option[com.histmersion.api.catalogue.EventPrecision.Bound], y: Option[com.histmersion.api.catalogue.EventPrecision.Bound]): com.histmersion.api.catalogue.EventPrecision.Uncertainty = (for p <- x; q <- y yield a2(q) - a2(p)).getOrElse(0L).max(0)
      val derived =
        if ts.beginBy.isEmpty && ts.endFrom.isEmpty then (a2(b) - a2(a)).abs / 2
        else width(ts.notBefore, ts.beginBy).max(width(ts.endFrom, ts.notAfter)) / 2
      derived.max(plusMinus.getOrElse(0L).max(0))
    }

  def of(uncertainty: Option[com.histmersion.api.catalogue.EventPrecision.Uncertainty]): com.histmersion.api.catalogue.EventSummary.Precision =
    uncertainty match
      case None                            => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Undated)
      case Some(u) if u <= PreciseUpTo     => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Precise)
      case Some(u) if u <= ApproximateUpTo => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Approximate)
      case _                               => _root_.com.histmersion.api.catalogue.EventSummary.Precision(Imprecise)

  def of(ts: TimeSpan, plusMinus: Option[com.histmersion.api.catalogue.EventPrecision.OfPlusMinus]): com.histmersion.api.catalogue.EventSummary.Precision = of((uncertainty(ts, plusMinus)).map(refactorValue4 => _root_.com.histmersion.api.catalogue.EventPrecision.Uncertainty((refactorValue4).value)))

  def mapped(precision: com.histmersion.api.catalogue.EventSummary.Precision): Boolean = (precision != _root_.com.histmersion.api.catalogue.EventSummary.Precision(Imprecise) && precision != _root_.com.histmersion.api.catalogue.EventSummary.Precision(Undated))

  def label(precision: com.histmersion.api.catalogue.EventPrecision.LabelPrecision): Option[String] =
    precision match
      case Imprecise   => Some(("imprecise date"))
      case Approximate => Some(("approximate date"))
      case _           => None

  def note(central: Option[com.histmersion.api.catalogue.EventSummary.Central], plusMinus: Option[com.histmersion.api.catalogue.EventSummary.PlusMinus], method: Option[com.histmersion.api.catalogue.EventSummary.DatingMethod]): Option[String] =
    val m = method.map((_).value.trim).filter(_.nonEmpty).map({ val refactorFunction = lowerFirst; (refactorValue2) => refactorFunction((refactorValue2)) })
    central match
      case Some(c) => Some(YearFmt(c) + plusMinus.filter(_ > 0).fold("")(u => s" ± $u") + m.fold("")(x => s" ($x)"))
      case None    => m.map(x => (s"dated by $x"))

  private def lowerFirst(s: String): String =
    ((s).split(" ", -1)
      .map(w =>
        if w.length >= 2 && w.charAt(0).isUpper && w.charAt(1).isLower then s"${w.charAt(0).toLower}${w.substring(1)}"
        else w
      )
      .mkString(" "))
