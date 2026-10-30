package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PluginsReport(
    spiVersion: Int = 0,
    plugins: List[PluginStatus] = Nil,
    warnings: List[String] = Nil,
    set: Option[PluginSet] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def ok: Boolean = plugins.forall(p => p.state != PluginState.Failed.wire && p.state != PluginState.Refused.wire)
object PluginsReport:
  given Schema[PluginsReport] = summon[KindlingsSchema[PluginsReport]].schema
