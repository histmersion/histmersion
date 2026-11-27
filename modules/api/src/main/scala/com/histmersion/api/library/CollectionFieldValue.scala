package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A field to set on an item, by the collection's own field name. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionFieldValue(
    name: CollectionFieldValue.Name,
    value: CollectionFieldValue.Value
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionFieldValue:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[String, Pure]
