package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class EventsNoaaConfig(
    url: String,
    pageSize: Int,
    requestIntervalMs: Int,
    datasets: String,
    regionMinMagnitude: Double,
    outsideMinMagnitude: Double
) derives ConfigReader
