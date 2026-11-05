package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A layer read tile by tile when a page shows an area the download does not hold (the relief deeper than the extract;
  * the map and the sea floor outside it, #3760), cached to `cacheGb`.
  */
final case class BasemapOnDemandConfig(
    enabled: Boolean,
    maxZoom: Int,
    cacheGb: Double,
    concurrency: Int,
    requestsPerSecond: Double,
    retryAfterSeconds: Int
) derives ConfigReader
