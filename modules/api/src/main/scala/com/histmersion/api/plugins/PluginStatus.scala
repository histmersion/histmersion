package com.histmersion
package api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class PluginStatus(
    id: PluginStatus.Id,
    version: PluginStatus.Version,
    spiVersion: PluginStatus.SpiVersion,
    enabled: PluginStatus.Enabled,
    state: PluginStatus.State,
    provides: List[PluginStatus.Provides] = Nil,
    settings: List[PluginSetting] = Nil,
    warnings: List[PluginStatus.Warnings] = Nil,
    error: Option[PluginStatus.Error] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object PluginStatus:
  export PluginDescription.{Id, Version, SpiVersion}
  type Enabled = Enabled.T
  object Enabled extends RefinedType[Boolean, Pure]
  type State = State.T
  object State extends RefinedType[String, Pure]
  export PluginDescription.Provides
  type Warnings = Warnings.T
  object Warnings extends RefinedType[String, Pure]
  export PluginDescription.Error
