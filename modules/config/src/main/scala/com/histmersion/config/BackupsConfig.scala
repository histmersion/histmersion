package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Where `pg_dump` archives are written (plan §1.10). `pgBinDir` is empty by default — the binaries are looked up on
  * PATH — and points at a directory holding `pg_dump`/`pg_restore` when they are installed off PATH (homebrew's libpq
  * is keg-only).
  */
/** @param keep how many archives a finished dump leaves in `dir` (the newest); `0` keeps them all. */
final case class BackupsConfig(dir: BackupsConfig.Dir, pgBinDir: BackupsConfig.PgBinDir, timeoutSeconds: BackupsConfig.TimeoutSeconds, requestWaitSeconds: BackupsConfig.RequestWaitSeconds, keep: BackupsConfig.Keep)
    derives ConfigReader

object BackupsConfig:
  type Dir = Dir.T
  object Dir extends RefinedType[String, Pure]
  type PgBinDir = PgBinDir.T
  object PgBinDir extends RefinedType[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends RefinedType[Int, Pure]
  type RequestWaitSeconds = RequestWaitSeconds.T
  object RequestWaitSeconds extends RefinedType[Int, Pure]
  type Keep = Keep.T
  object Keep extends RefinedType[Int, Pure]
