package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure




final case class ImportOptions(dryRun: ImportOptions.DryRun, chunkChars: ImportOptions.ChunkChars, toleranceKm: ImportOptions.ToleranceKm)

object ImportOptions:
  type DryRun = DryRun.T
  object DryRun extends RefinedType[Boolean, Pure]
  type ChunkChars = ChunkChars.T
  object ChunkChars extends RefinedType[Int, Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends RefinedType[Double, Pure]
