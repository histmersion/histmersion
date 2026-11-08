package com.histmersion
package api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class Backlink(kind: Backlink.Kind, id: Backlink.Id, title: Option[Backlink.Title])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Backlink:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
