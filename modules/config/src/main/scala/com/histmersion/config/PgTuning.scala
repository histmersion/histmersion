package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Memory settings the bundled Postgres is started with (`-c`, so an existing cluster gets them on its next start;
  * nothing is written to its `postgresql.conf`). Each is a Postgres size (`"2GB"`, `"512MB"`), `"auto"` (sized from the
  * machine's RAM, see `Supervisor.pgSettings`) or `""` (Postgres' own default). initdb's defaults — 128 MB of shared
  * buffers, 4 MB per sort — are sized for a machine with no RAM to spare; this one holds a 15 GB gazetteer it searches
  * as you type.
  */
final case class PgTuning(
    sharedBuffers: PgTuning.SharedBuffers,
    effectiveCacheSize: PgTuning.EffectiveCacheSize,
    workMem: PgTuning.WorkMem
) derives ConfigReader

object PgTuning:
  type SharedBuffers = SharedBuffers.T
  object SharedBuffers extends Newtype[String, Pure]
  type EffectiveCacheSize = EffectiveCacheSize.T
  object EffectiveCacheSize extends Newtype[String, Pure]
  type WorkMem = WorkMem.T
  object WorkMem extends Newtype[String, Pure]
