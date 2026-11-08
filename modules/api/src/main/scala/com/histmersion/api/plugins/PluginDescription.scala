package com.histmersion
package api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
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
  object Id extends Newtype[String, Pure]
  type Version = Version.T
  object Version extends Newtype[String, Pure]
  type SpiVersion = SpiVersion.T
  object SpiVersion extends Newtype[Int, Pure]
  type Provides = Provides.T
  object Provides extends Newtype[String, Pure]
  type Access = Access.T
  object Access extends Newtype[String, Pure]
  type Error = Error.T
  object Error extends Newtype[String, Pure]
