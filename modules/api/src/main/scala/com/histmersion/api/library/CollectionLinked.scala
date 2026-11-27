package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A source linked to item `id`, whose title is `title`. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionLinked(
    citekey: Citekey,
    id: CollectionLinked.Id,
    title: Title
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionLinked:
  export CollectionImportRequest.Id
