package com.histmersion
package config

import com.histmersion.config.edit.SettingKey
import com.histmersion.lint.{movesTo, FeaturePlace}

/** A memory parameter of the bundled Postgres that `runtime.postgres` sets: the setting that sizes it, and what `auto`
  * comes to. Its name to Postgres is the case's name in Postgres' spelling ([[name]]: `SharedBuffers` is
  * `shared_buffers`).
  */
@movesTo(FeaturePlace.UtilDatabase)
enum PgParameter(val key: SettingKey):
  /** `shared_buffers`: the pages Postgres itself keeps in memory. */
  case SharedBuffers extends PgParameter(SettingKey("histmersion.runtime.postgres.shared-buffers"))

  /** `effective_cache_size`: what the planner may assume the operating system caches (it allocates nothing). */
  case EffectiveCacheSize extends PgParameter(SettingKey("histmersion.runtime.postgres.effective-cache-size"))

  /** `work_mem`: one sort or hash of one query node. */
  case WorkMem extends PgParameter(SettingKey("histmersion.runtime.postgres.work-mem"))

  /** `maintenance_work_mem`: one VACUUM, CREATE INDEX or ALTER TABLE ADD FOREIGN KEY. */
  case MaintenanceWorkMem extends PgParameter(SettingKey("histmersion.runtime.postgres.maintenance-work-mem"))

  /** The parameter's name to Postgres, as `-c` passes it: the case's name in snake case (`shared_buffers`). */
  def name: String = productPrefix.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)

  /** What `auto` comes to on a machine with `ram`:
    *
    *   - shared buffers RAM/16 within 128 MB…2 GB: initdb's 128 MB holds a fraction of the gazetteer's indexes, so a
    *     place search or a text query re-read its pages through the kernel on every keystroke (2 GB on a 48 GB machine:
    *     warm interactive p50s 10–60 % lower, 2026-10-09);
    *   - the effective cache size RAM/2, at least 512 MB;
    *   - work memory 16 MB, whatever the RAM (a 24 000-row candidate sort stays in memory);
    *   - maintenance work memory RAM/32 within 64 MB (Postgres' default) and 1 GB.
    */
  def auto(ram: ByteCount): PgBytes = this match
    case SharedBuffers =>
      PgBytes.share(ram, 16, PgBytes.megabytes(128), Some(PgBytes.megabytes(2048)))
    case EffectiveCacheSize => PgBytes.share(ram, 2, PgBytes.megabytes(512), None)
    case WorkMem            => PgBytes.megabytes(16)
    case MaintenanceWorkMem => PgBytes.share(ram, 32, PgBytes.megabytes(64), Some(PgBytes.megabytes(1024)))
