package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** An item rewritten from a record: the fields written (none when it already agreed) and the record's title. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionUpdatedOne(
    citekey: Citekey,
    id: CollectionUpdatedOne.Id,
    changed: List[CollectionUpdatedOne.Changed],
    title: Title
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionUpdatedOne:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Changed = Changed.T
  object Changed extends Newtype[String, Pure]
