package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class PluginsReport(
    spiVersion: PluginsReport.SpiVersion = _root_.com.histmersion.api.plugins.PluginDescription.SpiVersion(0),
    plugins: List[PluginStatus] = Nil,
    warnings: List[PluginsReport.Warnings] = Nil,
    set: Option[PluginSet] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ok: Boolean = (plugins.forall(p => p.state != PluginState.Failed.wire && p.state != PluginState.Refused.wire))

object PluginsReport:

  export com.histmersion.api.plugins.PluginDescription.SpiVersion
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
