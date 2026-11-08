package com.histmersion
package api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** Re-indexing the whole corpus takes minutes, far past any HTTP timeout, so one call does a bounded slice. */
final case class ReindexResult(
    sources: ReindexResult.Sources,
    chunks: ReindexResult.Chunks,
    lastCitekey: Option[ReindexResult.LastCitekey],
    remaining: ReindexResult.Remaining
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object ReindexResult:
  type Sources = Sources.T
  object Sources extends Newtype[Int, Pure]
  type Chunks = Chunks.T
  object Chunks extends Newtype[Int, Pure]
  export com.histmersion.api.library.Citekey as LastCitekey
  type Remaining = Remaining.T
  object Remaining extends Newtype[Int, Pure]
