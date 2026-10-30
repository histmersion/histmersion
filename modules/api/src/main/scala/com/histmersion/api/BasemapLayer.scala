package com.histmersion.api

enum BasemapLayer(val wire: String, val title: String):
  case Vector extends BasemapLayer("vector", "coast, water and land cover")

  case Terrain extends BasemapLayer("terrain", "relief")

  case Labels extends BasemapLayer("labels", "physical names")

  /** The relief's own tiles have the sea flat at 0; these carry its depth (ETOPO1 from NOAA, or the GEBCO grid). */
  case Bathymetry extends BasemapLayer("bathymetry", "sea floor")

  def file: String = s"$wire.pmtiles"

object BasemapLayer:
  def parse(s: String): Option[BasemapLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  def parseMany(s: String): Either[String, List[BasemapLayer]] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case "" | "all" => Right(values.toList)
      case other      =>
        parse(other).map(List(_)).toRight(s"unknown base-map layer '$s' (vector, terrain, labels, bathymetry or all)")
