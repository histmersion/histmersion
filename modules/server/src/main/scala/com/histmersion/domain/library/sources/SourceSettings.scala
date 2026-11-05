package com.histmersion.domain.library.sources

/** What a source's write follows: the size of its full-text chunks, and the tolerance its findings are placed with. */
final case class SourceSettings(chunkChars: SourceSettings.ChunkChars, toleranceKm: SourceSettings.ToleranceKm)

object SourceSettings:
  type ChunkChars = ChunkChars.T
  object ChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
