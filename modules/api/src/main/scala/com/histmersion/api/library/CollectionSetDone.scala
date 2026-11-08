package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** The fields set on item `id`. */
final case class CollectionSetDone(
    id: CollectionSetDone.Id,
    fields: List[CollectionSetDone.Fields]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionSetDone:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Fields = Fields.T
  object Fields extends Newtype[String, Pure]
