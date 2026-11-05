package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** One plan pool: a floor (% that must stay LEFT) per window kind (`five_hour=25, seven_day=25`; Codex windows by
  * length, `300m`, `10080m`), how many LLM calls may draw on it at once, and how often it is read while the autopilot
  * has queued work for it.
  */
final case class PlanPoolConfig(
    floors: String,
    maxCallsInFlight: Int,
    refreshEveryMinutes: ScheduledRefresh = ScheduledRefresh.Off
) derives ConfigReader
