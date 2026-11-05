package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `ui.events`: the events on the map in playback — how long a local and a regional event fade (seconds of playback),
  * the most one playback returns, and how far an area outline is simplified (degrees).
  */
final case class UiEventsSettings(
    localFadeSeconds: Double,
    regionalFadeSeconds: Double,
    mapLimit: Int,
    outlineToleranceDeg: Double
) derives ConfigReader
