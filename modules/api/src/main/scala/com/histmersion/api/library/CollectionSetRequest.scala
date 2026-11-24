package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Set named fields on item `id` of a collection. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionSetRequest(
    id: CollectionSetRequest.Id,
    fields: List[CollectionFieldValue]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionSetRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
