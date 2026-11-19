package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** Set named fields on item `id` of a collection. */
final case class CollectionSetRequest(
    id: CollectionSetRequest.Id,
    fields: List[CollectionFieldValue]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionSetRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
