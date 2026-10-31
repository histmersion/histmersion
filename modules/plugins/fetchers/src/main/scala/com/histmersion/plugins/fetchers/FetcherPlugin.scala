package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Plugin, PluginInfo, Spi}

/** One of Histmersion's own acquisition plugins: on by default (its defaults resource), switched off by
  * `histmersion.plugins.<id>.enabled = false`. Its endpoints and rules are Histmersion's own `histmersion.acquire`
  * settings, which the host supplies it; its block holds `enabled` and the settings that are the user's own — a
  * contact, a credential — never an endpoint.
  */
trait FetcherPlugin(id: String) extends Plugin:
  val info: PluginInfo = PluginInfo(id, FetcherPlugin.Version, Spi.Version)

object FetcherPlugin:
  val Version = "1.0.0"
