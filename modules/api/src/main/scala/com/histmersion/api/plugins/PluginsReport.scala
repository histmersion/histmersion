package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PluginsReport(
    spiVersion: Int = 0,
    plugins: List[PluginStatus] = Nil,
    warnings: List[String] = Nil,
    set: Option[PluginSet] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ok: Boolean = plugins.forall(p => p.state != PluginState.Failed.wire && p.state != PluginState.Refused.wire)
