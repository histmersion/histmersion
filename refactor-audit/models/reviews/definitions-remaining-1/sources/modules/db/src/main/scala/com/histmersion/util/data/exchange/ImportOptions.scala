package com.histmersion.util.data.exchange




final case class ImportOptions(dryRun: ImportOptions.DryRun, chunkChars: ImportOptions.ChunkChars, toleranceKm: ImportOptions.ToleranceKm)

object ImportOptions:
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkChars = ChunkChars.T
  object ChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
