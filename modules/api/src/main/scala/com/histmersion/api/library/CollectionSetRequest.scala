package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** Set named fields on item `id` of a collection. */
final case class CollectionSetRequest(
    id: CollectionSetRequest.Id,
    fields: List[CollectionFieldValue]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionSetRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
