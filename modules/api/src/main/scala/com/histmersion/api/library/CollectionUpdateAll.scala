package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Api
import com.histmersion.api.TwoStates

/** An update of every linked item: one row per item, in id order, and whether it was written (`applied`). */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionUpdateAll(
    rows: List[CollectionUpdateRow],
    applied: CollectionUpdateAll.Applied
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionUpdateAll:

  /** Plan the rewrite of every linked item from its record; with `apply`, write it. */
  enum Applied derives CanEqual:
    case Applied, Previewed
  object Applied extends TwoStates[Applied]:
    extension (applied: Applied) def applied: Boolean = applied == Applied

  final case class Request(
      apply: Request.Apply = Request.Apply.DryRun
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Request:
    export Api.Apply
