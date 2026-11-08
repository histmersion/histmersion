package com.histmersion
package api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class PluginDescription(
    id: PluginDescription.Id,
    version: PluginDescription.Version,
    spiVersion: PluginDescription.SpiVersion,
    provides: List[PluginDescription.Provides] = Nil,
    access: List[PluginDescription.Access] = Nil,
    error: Option[PluginDescription.Error] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object PluginDescription:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Version = Version.T
  object Version extends RefinedType[String, Pure]
  type SpiVersion = SpiVersion.T
  object SpiVersion extends RefinedType[Int, Pure]
  type Provides = Provides.T
  object Provides extends RefinedType[String, Pure]
  type Access = Access.T
  object Access extends RefinedType[String, Pure]
  type Error = Error.T
  object Error extends RefinedType[String, Pure]
