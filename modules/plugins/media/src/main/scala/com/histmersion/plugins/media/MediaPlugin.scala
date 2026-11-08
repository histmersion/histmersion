package com.histmersion
package plugins.media

import com.histmersion.plugin.{Plugin, Spi}
import hearth.kindlings.sconfigderivation.ConfigReader

/** One of Histmersion's own media collections: on by default (its defaults resource; the two that need a key of the
  * user's own are off), switched off by `histmersion.plugins.<id>.enabled = false`. Its settings are Histmersion's own
  * `histmersion.media.*`, which the host supplies it; its block holds `enabled` and, for a keyed one, its key.
  */
trait MediaPlugin(id: String) extends Plugin:
  val info: Plugin.Info =
    Plugin.Info(Plugin.Info.Id(id), Plugin.Info.Version(MediaPlugin.Version), Plugin.Info.SpiVersion(Spi.Version))

object MediaPlugin:
  val Version = "0.1.0"

  /** A media plugin's settings: Histmersion's own `media.*`, which the host supplies. */
  final case class Config(media: MediaSettings) derives ConfigReader
