package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.constraint.any.Pure




final case class ImportOptions(dryRun: ImportOptions.DryRun, chunkChars: ImportOptions.ChunkChars, toleranceKm: ImportOptions.ToleranceKm)

object ImportOptions:
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
  type ChunkChars = ChunkChars.T
  object ChunkChars extends Newtype[Int, Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends Newtype[Double, Pure]
