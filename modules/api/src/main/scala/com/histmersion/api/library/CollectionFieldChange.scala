package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One field of an item: what the collection holds and what it is (or would be) written to. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionFieldChange(
    field: CollectionFieldChange.Field,
    from: CollectionFieldChange.From,
    to: CollectionFieldChange.To
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionFieldChange:
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type From = From.T
  object From extends Newtype[String, Pure]
  type To = To.T
  object To extends Newtype[String, Pure]
