package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The GEBCO grid as a sea-floor source: `url`, one GeoTIFF of the grid's tiling (`tileDegrees` square) with `{north}`,
  * `{south}`, `{west}`, `{east}`; the extract's zooms 0…`maxZoom`; its credit line.
  */
final case class BasemapGebcoConfig(url: String, tileDegrees: Int, maxZoom: Int, attribution: String)
    derives ConfigReader
