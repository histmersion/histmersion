package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A push: the item the original went into, the source's title, and with a replace the format replaced. */
final case class CollectionPushed(
    citekey: Citekey,
    id: CollectionPushed.Id,
    title: Title,
    replaced: Option[CollectionPushed.Replaced] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionPushed:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Replaced = Replaced.T
  object Replaced extends RefinedType[String, Pure]
