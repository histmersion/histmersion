package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The acquisition list's legal re-check: on or off, the days an item's check holds, the items tried a day and a pass,
  * the minutes between passes, the share (percent) of the autopilot's shared daily legal ingest allowance it may take,
  * and the hours after which a check that got no answer is due again.
  */
final case class RecheckConfig(
    enabled: Boolean,
    everyDays: Int,
    perDay: Int,
    perPass: Int,
    passMinutes: Int,
    sharePercent: Int,
    retryHours: Int
) derives ConfigReader
