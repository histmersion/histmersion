package com.histmersion
package api.plugins
import com.histmersion.api.Problem
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class PluginStatus(
    id: PluginStatus.Id,
    version: PluginStatus.Version,
    spiVersion: PluginStatus.SpiVersion,
    enabled: PluginStatus.Enabled,
    state: PluginState.Wire,
    provides: List[PluginStatus.Provides] = Nil,
    settings: List[PluginSetting] = Nil,
    warnings: List[PluginStatus.Warnings] = Nil,
    error: Option[Problem] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object PluginStatus:
  export PluginDescription.{Id, SpiVersion, Version}
  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  export PluginDescription.Provides
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]
