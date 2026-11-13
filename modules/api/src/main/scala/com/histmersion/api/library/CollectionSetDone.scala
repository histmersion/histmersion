package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** The fields set on item `id`. */
final case class CollectionSetDone(
    id: CollectionSetDone.Id,
    fields: List[CollectionSetDone.Fields]
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CollectionSetDone:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Fields = Fields.T
  object Fields extends Newtype[String, Pure]
