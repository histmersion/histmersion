package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** How a paper's illustration is chosen among its figures (`media.illustrations`). */
final case class MediaIllustrationsConfig(
    minSide: Int,
    minArea: Int,
    maxAspect: Double,
    maxRefs: Int,
    earlyPages: Int,
    pageScanMin: Int
) derives ConfigReader
