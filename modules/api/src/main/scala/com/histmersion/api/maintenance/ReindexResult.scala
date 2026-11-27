package com.histmersion
package api.maintenance
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Re-indexing the whole corpus takes minutes, far past any HTTP timeout, so one call does a bounded slice. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class ReindexResult(
    sources: ReindexResult.Sources,
    chunks: ReindexResult.Chunks,
    lastCitekey: Option[ReindexResult.LastCitekey],
    remaining: ReindexResult.Remaining
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ReindexResult:
  type Sources = Sources.T
  object Sources extends Newtype[Int, Pure]
  type Chunks = Chunks.T
  object Chunks extends Newtype[Int, Pure]
  export com.histmersion.api.library.Citekey as LastCitekey
  type Remaining = Remaining.T
  object Remaining extends Newtype[Int, Pure]
