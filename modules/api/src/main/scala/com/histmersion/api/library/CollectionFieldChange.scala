package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** One field of an item: what the collection holds and what it is (or would be) written to. */
final case class CollectionFieldChange(
    field: CollectionFieldChange.Field,
    from: CollectionFieldChange.From,
    to: CollectionFieldChange.To
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionFieldChange:
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type From = From.T
  object From extends Newtype[String, Pure]
  type To = To.T
  object To extends Newtype[String, Pure]
