package com.histmersion.plugins.media

import com.histmersion.plugin.{Plugin, PluginInfo, Spi}

/** One of Histmersion's own media collections: on by default (its defaults resource; the two that need a key of the
  * user's own are off), switched off by `histmersion.plugins.<id>.enabled = false`. Its settings are Histmersion's own
  * `histmersion.media.*`, which the host supplies it; its block holds `enabled` and, for a keyed one, its key.
  */
trait MediaPlugin(id: String) extends Plugin:
  val info: PluginInfo = PluginInfo(id, MediaPlugin.Version, Spi.Version)

object MediaPlugin:
  val Version = "1.0.0"
