package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class EventsLinksConfig(
    km: Double,
    years: Long,
    nameWeight: Double,
    findingWeight: Double,
    minScore: Double,
    minNameChars: Int,
    maxCandidates: Int,
    sourcesPerName: Int,
    nearFindings: Int,
    batch: Int
) derives ConfigReader
