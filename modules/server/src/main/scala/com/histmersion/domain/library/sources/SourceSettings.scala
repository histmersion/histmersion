package com.histmersion
package domain.library.sources

import io.github.iltotore.iron.constraint.any.Pure

/** What a source's write follows: the size of its full-text chunks, and the tolerance its findings are placed with. */
final case class SourceSettings(chunkChars: SourceSettings.ChunkChars, toleranceKm: SourceSettings.ToleranceKm)

object SourceSettings:
  type ChunkChars = ChunkChars.T
  object ChunkChars extends Newtype[Int, Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends Newtype[Double, Pure]
