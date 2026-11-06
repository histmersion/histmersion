package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** Re-indexing the whole corpus takes minutes, far past any HTTP timeout, so one call does a bounded slice. */
final case class ReindexResult(sources: ReindexResult.Sources, chunks: ReindexResult.Chunks, lastCitekey: Option[ReindexResult.LastCitekey], remaining: ReindexResult.Remaining)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReindexResult:
  type Sources = Sources.T
  object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Chunks = Chunks.T
  object Chunks extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastCitekey = com.histmersion.api.library.Citekey
  val LastCitekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey
  type Remaining = Remaining.T
  object Remaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
