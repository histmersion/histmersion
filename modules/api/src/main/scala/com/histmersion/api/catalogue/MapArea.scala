package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
final case class MapArea(label: MapArea.Label, bbox: MapArea.Bbox, approxGb: MapArea.ApproxGb)

object MapArea:

  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Bbox = Bbox.T
  object Bbox extends Newtype[String, Pure]
  type ApproxGb = ApproxGb.T
  object ApproxGb extends Newtype[Double, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(entry: String): Option[MapArea] =
    (entry).split('|').toList.map(_.trim) match
      case List(label, box, gb) if label.nonEmpty && box.nonEmpty =>
        gb.toDoubleOption.map(g => MapArea(Label(label), Bbox(box), ApproxGb(g)))
      case _ => None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def size(gb: ApproxGb): String = (s"${BigDecimal((gb).value).setScale(1, BigDecimal.RoundingMode.HALF_UP)} GB")
