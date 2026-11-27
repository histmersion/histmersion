package com.histmersion
package plugins.fetchers

import com.histmersion.plugin.{Plugin, Spi}
import com.histmersion.lint.{tolerated, LintRule}

/** One of Histmersion's own acquisition plugins: on by default (its defaults resource), switched off by
  * `histmersion.plugins.<id>.enabled = false`. Its endpoints and rules are Histmersion's own `histmersion.acquire`
  * settings, which the host supplies it; its block holds `enabled` and the settings that are the user's own — a
  * contact, a credential — never an endpoint.
  */
trait FetcherPlugin(id: String) extends Plugin:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val info: Plugin.Info =
    Plugin.Info(Plugin.Info.Id(id), Plugin.Info.Version(FetcherPlugin.Version), Plugin.Info.SpiVersion(Spi.Version))

object FetcherPlugin:
  val Version = "0.1.0"
