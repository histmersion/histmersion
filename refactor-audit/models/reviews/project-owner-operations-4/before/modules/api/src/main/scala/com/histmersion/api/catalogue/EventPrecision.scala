package com.histmersion.api.catalogue

import com.histmersion.api.{TimeSpan, YearFmt, Years}
import com.histmersion.api.given
object EventPrecision:
  type UncertaintyA2Y = UncertaintyA2Y.T
  object UncertaintyA2Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncertaintyWidthResult = UncertaintyWidthResult.T
  object UncertaintyWidthResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncertaintyWidthX = UncertaintyWidthX.T
  object UncertaintyWidthX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncertaintyWidthY = UncertaintyWidthY.T
  object UncertaintyWidthY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreciseUpTo = PreciseUpTo.T
  object PreciseUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApproximateUpTo = ApproximateUpTo.T
  object ApproximateUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncertaintyResult = UncertaintyResult.T
  object UncertaintyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UncertaintyPlusMinus = UncertaintyPlusMinus.T
  object UncertaintyPlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResult = OfResult.T
  object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfUncertainty = OfUncertainty.T
  object OfUncertainty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlusMinus = OfPlusMinus.T
  object OfPlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MappedResult = MappedResult.T
  object MappedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MappedPrecision = MappedPrecision.T
  object MappedPrecision extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelPrecision = LabelPrecision.T
  object LabelPrecision extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteResult = NoteResult.T
  object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteCentral = NoteCentral.T
  object NoteCentral extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotePlusMinus = NotePlusMinus.T
  object NotePlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteMethod = NoteMethod.T
  object NoteMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowerFirstResult = LowerFirstResult.T
  object LowerFirstResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowerFirstS = LowerFirstS.T
  object LowerFirstS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Precise = "precise"
  val Approximate = "approximate"
  val Imprecise = "imprecise"
  val Undated = "undated"
  val all: List[com.histmersion.api.catalogue.EventPrecision.All] = List(_root_.com.histmersion.api.catalogue.EventPrecision.All(Precise), _root_.com.histmersion.api.catalogue.EventPrecision.All(Approximate), _root_.com.histmersion.api.catalogue.EventPrecision.All(Imprecise), _root_.com.histmersion.api.catalogue.EventPrecision.All(Undated))

  val PreciseUpTo: com.histmersion.api.catalogue.EventPrecision.PreciseUpTo = _root_.com.histmersion.api.catalogue.EventPrecision.PreciseUpTo((50).toLong)
  val ApproximateUpTo: com.histmersion.api.catalogue.EventPrecision.ApproximateUpTo = _root_.com.histmersion.api.catalogue.EventPrecision.ApproximateUpTo((500).toLong)

  /** Widths are taken in astronomical years: historical years have no year 0. */
  def uncertainty(ts: TimeSpan, plusMinus: Option[com.histmersion.api.catalogue.EventPrecision.UncertaintyPlusMinus]): Option[com.histmersion.api.catalogue.EventPrecision.UncertaintyResult] =
    ts.outer.map { (a, b) =>
      def a2(y: com.histmersion.api.catalogue.EventPrecision.UncertaintyA2Y) = Years.toAstronomical(y)
      def width(x: Option[com.histmersion.api.catalogue.EventPrecision.UncertaintyWidthX], y: Option[com.histmersion.api.catalogue.EventPrecision.UncertaintyWidthY]): com.histmersion.api.catalogue.EventPrecision.UncertaintyWidthResult = (for p <- x; q <- y yield a2(q) - a2(p)).getOrElse(0L).max(0)
      val derived =
        if ts.beginBy.isEmpty && ts.endFrom.isEmpty then (a2(b) - a2(a)).abs / 2
        else width(ts.notBefore, ts.beginBy).max(width(ts.endFrom, ts.notAfter)) / 2
      derived.max(plusMinus.getOrElse(0L).max(0))
    }

  def of(uncertainty: Option[com.histmersion.api.catalogue.EventPrecision.OfUncertainty]): com.histmersion.api.catalogue.EventPrecision.OfResult =
    uncertainty match
      case None                            => _root_.com.histmersion.api.catalogue.EventPrecision.OfResult(Undated)
      case Some(u) if u <= PreciseUpTo     => _root_.com.histmersion.api.catalogue.EventPrecision.OfResult(Precise)
      case Some(u) if u <= ApproximateUpTo => _root_.com.histmersion.api.catalogue.EventPrecision.OfResult(Approximate)
      case _                               => _root_.com.histmersion.api.catalogue.EventPrecision.OfResult(Imprecise)

  def of(ts: TimeSpan, plusMinus: Option[com.histmersion.api.catalogue.EventPrecision.OfPlusMinus]): com.histmersion.api.catalogue.EventPrecision.OfResult = of(uncertainty(ts, plusMinus))

  def mapped(precision: com.histmersion.api.catalogue.EventPrecision.MappedPrecision): com.histmersion.api.catalogue.EventPrecision.MappedResult = precision != Imprecise && precision != Undated

  def label(precision: com.histmersion.api.catalogue.EventPrecision.LabelPrecision): Option[com.histmersion.api.catalogue.EventPrecision.LabelResult] =
    precision match
      case Imprecise   => Some(_root_.com.histmersion.api.catalogue.EventPrecision.LabelResult("imprecise date"))
      case Approximate => Some(_root_.com.histmersion.api.catalogue.EventPrecision.LabelResult("approximate date"))
      case _           => None

  def note(central: Option[com.histmersion.api.catalogue.EventPrecision.NoteCentral], plusMinus: Option[com.histmersion.api.catalogue.EventPrecision.NotePlusMinus], method: Option[com.histmersion.api.catalogue.EventPrecision.NoteMethod]): Option[com.histmersion.api.catalogue.EventPrecision.NoteResult] =
    val m = method.map((_).value.trim).filter(_.nonEmpty).map(lowerFirst)
    central match
      case Some(c) => Some(YearFmt(c) + plusMinus.filter(_ > 0).fold("")(u => s" ± $u") + m.fold("")(x => s" ($x)"))
      case None    => m.map(x => _root_.com.histmersion.api.catalogue.EventPrecision.NoteResult(s"dated by $x"))

  private def lowerFirst(s: com.histmersion.api.catalogue.EventPrecision.LowerFirstS): com.histmersion.api.catalogue.EventPrecision.LowerFirstResult =
    (s).value.split(" ", -1)
      .map(w =>
        if w.length >= 2 && w.charAt(0).isUpper && w.charAt(1).isLower then s"${w.charAt(0).toLower}${w.substring(1)}"
        else w
      )
      .mkString(" ")
