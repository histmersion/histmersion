package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** Link source `citekey` to item `id` of a collection (the collection itself is not changed). */
final case class CollectionLinkRequest(
    citekey: Citekey,
    id: CollectionLinkRequest.Id
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CollectionLinkRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
