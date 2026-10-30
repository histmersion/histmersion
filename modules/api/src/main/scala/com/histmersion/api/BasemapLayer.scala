package com.histmersion.api

/** The terrain base map (docs/research-terrain-basemap.md): MODERN terrain only — relief, coast, water, rivers, natural
  * land cover — drawn by the web page from two regional PMTiles extracts the server downloads once (`histmersion
  * basemap fetch`) and serves (`GET /api/basemap/{layer}/{z}/{x}/{y}`). The wire types and the pure tile arithmetic;
  * the server owns the files.
  */
enum BasemapLayer(val wire: String, val title: String):
  /** Coast, water, rivers, land cover and land use, natural points: vector tiles (MVT) without the modern layers
    * (`basemap.vector.drop`: roads, buildings, places, boundaries …).
    */
  case Vector extends BasemapLayer("vector", "coast, water and land cover")

  /** Elevation, terrarium-encoded raster tiles the page shades into relief. */
  case Terrain extends BasemapLayer("terrain", "relief")

  /** The physical names — ranges, deserts, peninsulas, lakes, major rivers, peaks — from Natural Earth, a small vector
    * archive built by the fetch (seas and gulfs come with the vector layer).
    */
  case Labels extends BasemapLayer("labels", "physical names")

  /** The sea floor: terrarium-encoded elevation tiles whose sea is its depth (ETOPO1's bathymetry, NOAA, read tile by
    * tile; or the GEBCO grid's, `basemap.bathymetry.source`) — the relief's own tiles have the sea flat at 0.
    */
  case Bathymetry extends BasemapLayer("bathymetry", "sea floor")

  def file: String = s"$wire.pmtiles"

object BasemapLayer:
  def parse(s: String): Option[BasemapLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** `all` (or blank) is every layer. */
  def parseMany(s: String): Either[String, List[BasemapLayer]] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case "" | "all" => Right(values.toList)
      case other      =>
        parse(other).map(List(_)).toRight(s"unknown base-map layer '$s' (vector, terrain, labels, bathymetry or all)")
