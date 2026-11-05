package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `drop`: the vector layers (or their kinds) never stored — [[Basemap.parseDrop]]; everything else is kept and the
  * style decides what is drawn. `onDemand`: the map outside the extract (or deeper), read as a page needs it (#3760).
  */
final case class BasemapVectorConfig(
    buildsUrl: String,
    url: String,
    build: String,
    maxZoom: Int,
    drop: String,
    attribution: String,
    onDemand: BasemapOnDemandConfig
) derives ConfigReader
