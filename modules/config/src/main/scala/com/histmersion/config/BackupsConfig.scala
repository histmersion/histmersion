package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

/** Where `pg_dump` archives are written (plan §1.10). `pgBinDir` is empty by default — the binaries are looked up on
  * PATH — and points at a directory holding `pg_dump`/`pg_restore` when they are installed off PATH (homebrew's libpq
  * is keg-only).
  */
/** @param keep
  *   how many archives a finished dump leaves in `dir` (the newest); `0` keeps them all.
  * @param restoreDrainSeconds
  *   how long a restore first waits for the writes, connections and workers in flight; with a write or a connection
  *   still out then, it changes nothing.
  */
final case class BackupsConfig(
    dir: BackupsConfig.Dir,
    pgBinDir: BackupsConfig.PgBinDir,
    timeoutSeconds: BackupsConfig.TimeoutSeconds,
    requestWaitSeconds: BackupsConfig.RequestWaitSeconds,
    keep: BackupsConfig.Keep,
    restoreDrainSeconds: BackupsConfig.RestoreDrainSeconds
) derives ConfigReader

object BackupsConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type PgBinDir = PgBinDir.T
  object PgBinDir extends Newtype[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type RequestWaitSeconds = RequestWaitSeconds.T
  object RequestWaitSeconds extends Newtype[Int, Pure]
  type Keep = Keep.T
  object Keep extends Newtype[Int, Pure]
  type RestoreDrainSeconds = RestoreDrainSeconds.T
  object RestoreDrainSeconds extends Newtype[Int, Pure]:
    extension (s: RestoreDrainSeconds)
      /** The wait as a duration (a negative setting waits for nothing). */
      def duration: FiniteDuration = FiniteDuration(math.max(0, s.value).toLong, TimeUnit.SECONDS)
