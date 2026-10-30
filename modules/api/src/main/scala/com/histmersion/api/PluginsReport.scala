package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Every plugin in this binary, in registration order, and what is wrong with the config around them (a block no plugin
  * claims, an `order` entry naming no plugin). `spiVersion` is the SPI this server speaks. `set` is the user-added
  * plugin set the binary was linked with (§7.3; `None`: first-party plugins only, as a stock image).
  */
final case class PluginsReport(
    spiVersion: Int = 0,
    plugins: List[PluginStatus] = Nil,
    warnings: List[String] = Nil,
    set: Option[PluginSet] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** Every plugin that was enabled is registered, and none was refused. */
  def ok: Boolean = plugins.forall(p => p.state != PluginState.Failed.wire && p.state != PluginState.Refused.wire)
object PluginsReport:
  given Schema[PluginsReport] = summon[KindlingsSchema[PluginsReport]].schema
