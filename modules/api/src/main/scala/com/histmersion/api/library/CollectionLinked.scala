package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A source linked to item `id`, whose title is `title`. */
final case class CollectionLinked(
    citekey: Citekey,
    id: CollectionLinked.Id,
    title: Title
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionLinked:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
