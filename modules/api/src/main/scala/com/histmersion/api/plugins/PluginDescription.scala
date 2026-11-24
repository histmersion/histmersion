package com.histmersion
package api.plugins

import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A plugin a jar holds, as the relink kit's tool described it: `error` is every problem it met registering with its
  * defaults alone, written as a list (a file an earlier version wrote holds one sentence there, bare: the server
  * rewrites such a file once, at its start, into the list form).
  */
final case class PluginDescription(
    id: PluginDescription.Id,
    version: PluginDescription.Version,
    spiVersion: PluginDescription.SpiVersion,
    provides: List[PluginDescription.Provides] = Nil,
    access: List[PluginDescription.Access] = Nil,
    error: Option[NonEmptyVector[Problem]] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PluginDescription:
  type Id = Id.T
  object Id extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Version = Version.T
  object Version extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type SpiVersion = SpiVersion.T
  object SpiVersion extends Newtype[Int, Pure]
  type Provides = Provides.T
  object Provides extends Newtype[String, Pure]
  type Access = Access.T
  object Access extends Newtype[String, Pure]
