package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class MediaWikidataConfig(
    apiUrl: String,
    batch: Int,
    maxlag: Int,
    placeProperties: String,
    eventProperties: String
) derives ConfigReader
