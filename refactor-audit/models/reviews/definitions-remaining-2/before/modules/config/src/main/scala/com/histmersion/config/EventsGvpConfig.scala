package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `floorYear`: the earliest year a GVP dating may reach (the list is of HOLOCENE eruptions: a ± that runs before it is
  * held there).
  */
final case class EventsGvpConfig(
    url: String,
    pageSize: Int,
    windowYears: Long,
    requestIntervalMs: Int,
    regionMinVei: Int,
    outsideMinVei: Int,
    floorYear: Long
) derives ConfigReader
