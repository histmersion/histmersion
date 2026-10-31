package com.histmersion.plugins.events

import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.events.gvp`: the WFS, the most eruptions one window may hold, a window's years, the pace, and the
  * Holocene floor no GVP dating reaches before.
  */
final case class GvpSettings(url: String, pageSize: Int, windowYears: Long, requestIntervalMs: Int, floorYear: Long)
    derives ConfigReader
