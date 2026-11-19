package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A push: the item the original went into, the source's title, and with a replace the format replaced. */
final case class CollectionPushed(
    citekey: Citekey,
    id: CollectionPushed.Id,
    title: Title,
    replaced: Option[CollectionPushed.Replaced] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionPushed:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Replaced = Replaced.T
  object Replaced extends Newtype[String, Pure]
