package com.histmersion.plugins.events

import com.histmersion.plugin.{Plugin, PluginInfo, Spi}

/** One of Histmersion's own event catalogues: on by default (its defaults resource), switched off by
  * `histmersion.plugins.events-<catalogue>.enabled = false`. Its settings are Histmersion's own `events.<catalogue>.*`,
  * which the host supplies it.
  */
trait EventsPlugin(id: String) extends Plugin:
  val info: PluginInfo = PluginInfo(id, EventsPlugin.Version, Spi.Version)

object EventsPlugin:
  val Version = "1.0.0"
