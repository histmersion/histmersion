package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `onDemand`: the deeper relief read from `url` as a page needs it ([[BasemapOnDemandConfig]]); `focusRegions`: "name
  * minLon,minLat,maxLon,maxLat" — the regions `fetch --focus <name>` keeps to that depth.
  */
final case class BasemapTerrainConfig(
    url: String,
    maxZoom: Int,
    attribution: String,
    onDemand: BasemapOnDemandConfig,
    focusRegions: List[String]
) derives ConfigReader
