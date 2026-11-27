package com.histmersion
package config

import com.histmersion.util.problem.{Problem, Problems}
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.numeric.Interval

import PgTuning.Connections
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Memory and connection settings the bundled Postgres is started with (`-c`, so an existing cluster gets them on its
  * next start; nothing is written to its `postgresql.conf`). Each memory setting is a [[PgSize]]: a size Postgres reads
  * (`2GB`, `512MB`), `auto` (sized from the machine's RAM, [[PgParameter.auto]]) or nothing (Postgres' own default).
  * initdb's defaults — 128 MB of shared buffers, 4 MB per sort — are sized for a machine with no RAM to spare; this one
  * holds a 15 GB gazetteer it searches as you type. A value that is no size stops the settings from loading, so a
  * mistyped size is said where the settings are read, not by a Postgres that will not start.
  */
@movesTo(FeaturePlace.UtilDatabase)
final case class PgTuning(
    sharedBuffers: PgSize,
    effectiveCacheSize: PgSize,
    workMem: PgSize,
    maintenanceWorkMem: PgSize,
    maxConnections: Connections
):

  /** The setting that sizes `parameter`. */
  def size(parameter: PgParameter): PgSize = parameter match
    case PgParameter.SharedBuffers      => sharedBuffers
    case PgParameter.EffectiveCacheSize => effectiveCacheSize
    case PgParameter.WorkMem            => workMem
    case PgParameter.MaintenanceWorkMem => maintenanceWorkMem

  /** Every parameter Postgres is started with a size for on a machine with `ram`, and that size, in order. */
  def sizes(ram: Option[ByteCount]): List[(PgParameter, PgBytes)] =
    PgParameter.values.toList.flatMap(p => size(p).on(p, ram).map(p -> _))

  /** The parameters set to `auto`, and what `auto` comes to on a machine with `ram` (none while it is unknown). */
  def automatic(ram: Option[ByteCount]): List[(PgParameter, PgBytes)] =
    PgParameter.values.toList.filter(p => size(p) == PgSize.Auto).flatMap(p => ram.map(r => p -> p.auto(r)))

  /** The database pool fits in these connections ([[DbPoolConfig.Max.peak]], with [[Connections.Reserved]] kept free),
    * or what is wrong with it: said of `db.pool.max`, the setting to lower.
    */
  def admits(pool: DbPoolConfig): Either[Problem, Unit] =
    val room = maxConnections.room
    Either.cond(
      pool.max.peak <= room,
      (),
      Problem.Config.Setting(
        Problem.SettingKey("histmersion.db.pool.max"),
        Problem.Config.Fault.PoolOverConnections(
          Problem.Count.of(pool.max),
          Problem.SettingKey("histmersion.runtime.postgres.max-connections"),
          Problem.Count.of(maxConnections),
          DbPoolConfig.Max.most(room).fold(Problem.Count(0L))(Problem.Count.of(_)),
          Problem.Count.of(Connections.Reserved)
        )
      )
    )

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object PgTuning:

  /** A number of connections to the bundled Postgres, within what its `max_connections` accepts. */
  type Connections = Connections.T
  object Connections extends Newtype[Int, Interval.Closed[1, 262143]]:
    /** Kept free of the server's pools: Postgres' 3 for a superuser's session, one for a backup's `pg_dump` or
      * `pg_restore`, one for the bundle creating its database.
      */
    val Reserved: Connections = Connections(5)

    extension (c: Connections)
      /** The connections left for the server's pools once [[Reserved]] are kept free. */
      def room: Int = c.value - Reserved.value

  /** The block as the settings write it: words, read as sizes by [[Written.tuning]]. */
  final case class Written(
      sharedBuffers: PgSize.Written,
      effectiveCacheSize: PgSize.Written,
      workMem: PgSize.Written,
      maintenanceWorkMem: PgSize.Written,
      maxConnections: Connections
  ) derives ConfigReader:

    /** Every memory setting read as a size, or each one that is none (said of its key). */
    def tuning: Either[Problems, PgTuning] =
      def read(p: PgParameter, w: PgSize.Written): Either[Problems, PgSize] =
        w.size.toRight(
          Problem.Config.Setting(Problem.SettingKey.of(p.key), Problem.Config.Fault.NotSize(Problem.Raw.of(w)))
        )
      (
        read(PgParameter.SharedBuffers, sharedBuffers),
        read(PgParameter.EffectiveCacheSize, effectiveCacheSize),
        read(PgParameter.WorkMem, workMem),
        read(PgParameter.MaintenanceWorkMem, maintenanceWorkMem)
      ).allOrProblems.map((s, e, w, m) => PgTuning(s, e, w, m, maxConnections))

  given ConfigReader[PgTuning] = ConfigLayers.checking(summon[ConfigReader[Written]])(_.tuning)
