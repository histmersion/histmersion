package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

final case class ImportOptions(
    dryRun: ImportOptions.DryRun,
    chunkChars: ImportOptions.ChunkChars,
    toleranceKm: ImportOptions.ToleranceKm
)

object ImportOptions:
  enum DryRun derives CanEqual:
    case DryRun, Applied
  object DryRun extends TwoCases[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  type ChunkChars = ChunkChars.T
  object ChunkChars extends Newtype[Int, Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends Newtype[Double, Pure]
