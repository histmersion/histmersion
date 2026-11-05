package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class MapArea(label: String, bbox: String, approxGb: Double)

object MapArea:
  def parse(entry: String): Option[MapArea] =
    entry.split('|').toList.map(_.trim) match
      case List(label, box, gb) if label.nonEmpty && box.nonEmpty => gb.toDoubleOption.map(MapArea(label, box, _))
      case _                                                      => None

  def size(gb: Double): String = s"${BigDecimal(gb).setScale(1, BigDecimal.RoundingMode.HALF_UP)} GB"
