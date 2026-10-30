package com.histmersion.api

/** An area of the background map the setup and Settings offer to download (`basemap.setup-areas`): what it is,
  * its box (`world`, or `minLon,minLat,maxLon,maxLat`) and about how big its download is. */
final case class MapArea(label: String, bbox: String, approxGb: Double)

object MapArea:
  /** `"<label>|<box>|<GB>"`; `None` for an entry that is not one. Pure. */
  def parse(entry: String): Option[MapArea] =
    entry.split('|').toList.map(_.trim) match
      case List(label, box, gb) if label.nonEmpty && box.nonEmpty => gb.toDoubleOption.map(MapArea(label, box, _))
      case _                                                      => None

  /** A size in GB as a person reads it: `8.8 GB`, `0.4 GB`. */
  def size(gb: Double): String = s"${BigDecimal(gb).setScale(1, BigDecimal.RoundingMode.HALF_UP)} GB"
