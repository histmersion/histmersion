package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class MapArea(label: MapArea.Label, bbox: MapArea.Bbox, approxGb: MapArea.ApproxGb)

object MapArea:

  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bbox = Bbox.T
  object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApproxGb = ApproxGb.T
  object ApproxGb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(entry: String): Option[MapArea] =
    (entry).split('|').toList.map(_.trim) match
      case List(label, box, gb) if label.nonEmpty && box.nonEmpty => gb.toDoubleOption.map(MapArea(_root_.com.histmersion.api.catalogue.MapArea.Label(label), _root_.com.histmersion.api.catalogue.MapArea.Bbox(box), _root_.com.histmersion.api.catalogue.MapArea.ApproxGb(_)))
      case _                                                      => None

  def size(gb: com.histmersion.api.catalogue.MapArea.ApproxGb): String = (s"${BigDecimal((gb).value).setScale(1, BigDecimal.RoundingMode.HALF_UP)} GB")
