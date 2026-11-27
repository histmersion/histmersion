package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What became of an imported book's cover: one line for a person, a `warning` when it could not be stored. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionCover(
    line: CollectionCover.Line,
    warning: CollectionCover.Warning = CollectionCover.Warning(false)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionCover:
  type Line = Line.T
  object Line extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Warning = Warning.T
  object Warning extends Newtype[Boolean, Pure]
