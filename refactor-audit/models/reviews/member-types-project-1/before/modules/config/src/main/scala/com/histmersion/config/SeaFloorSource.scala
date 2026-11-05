package com.histmersion.config

/** Where the sea floor's extract comes from (`basemap.bathymetry.source`). */
enum SeaFloorSource(val wire: String):
  /** NOAA's ETOPO1 (1 arc-minute, ~1.8 km) through Mapzen's terrain tiles: one GET a tile. */
  case Etopo1 extends SeaFloorSource("etopo1")

  /** The GEBCO grid (15 arc-seconds, ~460 m): its GeoTIFFs read by range and made into tiles by the fetch. */
  case Gebco extends SeaFloorSource("gebco")

object SeaFloorSource:
  def parse(s: String): Either[String, SeaFloorSource] =
    values
      .find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
      .toRight(s"'$s' is not a sea-floor source (${values.map(_.wire).mkString(", ")})")
