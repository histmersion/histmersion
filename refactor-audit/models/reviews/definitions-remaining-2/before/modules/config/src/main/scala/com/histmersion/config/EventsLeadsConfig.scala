package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class EventsLeadsConfig(
    km: Double,
    years: Long,
    articleKm: Double,
    largeVei: Int,
    largeMagnitude: Double,
    articleLimit: Int
) derives ConfigReader
