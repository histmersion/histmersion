package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
final case class MapArea(label: MapArea.Label, bbox: MapArea.Bbox, approxGb: MapArea.ApproxGb)

object MapArea:

  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Bbox = Bbox.T
  object Bbox extends Newtype[String, Pure]
  type ApproxGb = ApproxGb.T
  object ApproxGb extends Newtype[Double, Pure]

  def parse(entry: String): Option[MapArea] =
    (entry).split('|').toList.map(_.trim) match
      case List(label, box, gb) if label.nonEmpty && box.nonEmpty => gb.toDoubleOption.map(g => MapArea(Label(label), Bbox(box), ApproxGb(g)))
      case _                                                      => None

  def size(gb: ApproxGb): String = (s"${BigDecimal((gb).value).setScale(1, BigDecimal.RoundingMode.HALF_UP)} GB")
