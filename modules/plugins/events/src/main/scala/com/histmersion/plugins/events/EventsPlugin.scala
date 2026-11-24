package com.histmersion
package plugins.events

import com.histmersion.plugin.{Plugin, Spi}
import com.histmersion.lint.{LintRule, tolerated}

/** One of Histmersion's own event catalogues: on by default (its defaults resource), switched off by
  * `histmersion.plugins.events-<catalogue>.enabled = false`. Its settings are Histmersion's own `events.<catalogue>.*`,
  * which the host supplies it.
  */
trait EventsPlugin(id: String) extends Plugin:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val info: Plugin.Info =
    Plugin.Info(Plugin.Info.Id(id), Plugin.Info.Version(EventsPlugin.Version), Plugin.Info.SpiVersion(Spi.Version))

object EventsPlugin:
  val Version = "0.1.0"
