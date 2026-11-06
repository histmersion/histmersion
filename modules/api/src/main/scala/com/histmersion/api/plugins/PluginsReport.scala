package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class PluginsReport(
    spiVersion: PluginsReport.SpiVersion = _root_.com.histmersion.api.plugins.PluginsReport.SpiVersion(0),
    plugins: List[PluginStatus] = Nil,
    warnings: List[PluginsReport.Warnings] = Nil,
    set: Option[PluginSet] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ok: com.histmersion.api.plugins.PluginsReport.OkResult = _root_.com.histmersion.api.plugins.PluginsReport.OkResult(plugins.forall(p => p.state != PluginState.Failed.wire && p.state != PluginState.Refused.wire))

object PluginsReport:
  type OkResult = OkResult.T
  object OkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SpiVersion = SpiVersion.T
  object SpiVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
