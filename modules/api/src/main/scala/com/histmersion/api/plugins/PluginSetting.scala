package com.histmersion
package api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class PluginSetting(key: PluginSetting.Key, value: PluginSetting.Value)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object PluginSetting:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[String, Pure]
