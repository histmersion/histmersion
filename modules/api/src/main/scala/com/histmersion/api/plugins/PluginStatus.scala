package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
  type Enabled = Enabled.T
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
