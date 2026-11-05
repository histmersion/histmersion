package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The sea floor: terrarium tiles at `url` (`{z}/{x}/{y}`), zooms 0…`maxZoom` of the region, one GET a tile,
  * `requestGapMs` apart; `onDemand`: the tiles outside the region, read as a page needs them (#3760). `source` picks
  * what the extract is made from: those tiles (ETOPO1), or the GEBCO grid (`gebco`).
  */
final case class BasemapBathymetryConfig(
    url: String,
    maxZoom: Int,
    requestGapMs: Int,
    attribution: String,
    onDemand: BasemapOnDemandConfig,
    source: SeaFloorSource,
    gebco: BasemapGebcoConfig
) derives ConfigReader:
  def extractMaxZoom: Int = source match
    case SeaFloorSource.Etopo1 => maxZoom
    case SeaFloorSource.Gebco  => gebco.maxZoom
