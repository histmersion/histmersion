package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The sources with a fetched file that are not in a collection: `count` gaps, `uncounted` records without a file,
  * `total` both, and every row.
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionGaps(
    count: CollectionGaps.Count,
    uncounted: CollectionGaps.Uncounted,
    total: CollectionGaps.Total,
    gaps: List[CollectionGapRow]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionGaps:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Uncounted = Uncounted.T
  object Uncounted extends Newtype[Int, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
