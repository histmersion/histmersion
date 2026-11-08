package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A field to set on an item, by the collection's own field name. */
final case class CollectionFieldValue(
    name: CollectionFieldValue.Name,
    value: CollectionFieldValue.Value
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionFieldValue:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Value = Value.T
  object Value extends RefinedType[String, Pure]
