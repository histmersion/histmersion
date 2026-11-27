package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The fields set on item `id`. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionSetDone(
    id: CollectionSetDone.Id,
    fields: List[CollectionSetDone.Fields]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionSetDone:
  export CollectionImportRequest.Id
  type Fields = Fields.T
  object Fields extends Newtype[String, Pure]
