package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Where `pg_dump` archives are written (plan §1.10). `pgBinDir` is empty by default — the binaries are looked up on
  * PATH — and points at a directory holding `pg_dump`/`pg_restore` when they are installed off PATH (homebrew's libpq
  * is keg-only).
  */
/** @param keep how many archives a finished dump leaves in `dir` (the newest); `0` keeps them all. */
final case class BackupsConfig(dir: BackupsConfig.Dir, pgBinDir: BackupsConfig.PgBinDir, timeoutSeconds: BackupsConfig.TimeoutSeconds, requestWaitSeconds: BackupsConfig.RequestWaitSeconds, keep: BackupsConfig.Keep)
    derives ConfigReader

object BackupsConfig:
  type Dir = Dir.T
  object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PgBinDir = PgBinDir.T
  object PgBinDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestWaitSeconds = RequestWaitSeconds.T
  object RequestWaitSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keep = Keep.T
  object Keep extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
