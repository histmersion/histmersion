package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The structured log: where its rolling files are (`dir`, empty: `<data dir>/logs/structured`), how long its events
  * are kept (ordinary ones `keepDays`, problems `keepProblemDays`) and how large the table and the files may grow
  * (`maxBytes` each; the oldest go first), how the writer batches, and which request paths are counted per minute
  * rather than logged one by one.
  */
final case class LogConfig(
    dir: LogConfig.Dir,
    keepDays: LogConfig.KeepDays,
    keepProblemDays: LogConfig.KeepDays,
    maxBytes: LogConfig.MaxBytes,
    queueSize: LogConfig.QueueSize,
    batchSize: LogConfig.BatchSize,
    flushMillis: LogConfig.FlushMillis,
    backlog: LogConfig.Backlog,
    pruneEveryMinutes: LogConfig.PruneEveryMinutes,
    counted: LogConfig.Counted
) derives ConfigReader:
  /** Whether a GET of `path` is counted rather than logged one by one. */
  def countsPath(path: String): Boolean = counted.prefixes.exists(_.covers(path))

object LogConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]:
    extension (d: Dir)
      /** The directory set, when one is (empty: the server's logs directory). */
      def set: Option[String] = Option(d.value.trim).filter(_.nonEmpty)

  type KeepDays = KeepDays.T
  object KeepDays extends Newtype[Int, Pure]:
    extension (d: KeepDays)
      /** The cut-off at `now`: what is older goes. */
      def before(now: java.time.Instant): java.time.Instant =
        now.minus(math.max(0, d.value).toLong, java.time.temporal.ChronoUnit.DAYS)

  type MaxBytes = MaxBytes.T
  object MaxBytes extends Newtype[Long, Pure]:
    extension (m: MaxBytes)
      /** How far `bytes` goes over the cap (zero or less: within it). */
      def over(bytes: Double): Double = bytes - m.value.toDouble

  type QueueSize = QueueSize.T
  object QueueSize extends Newtype[Int, Pure]:
    extension (q: QueueSize) def capacity: Int = math.max(1, q.value)

  type BatchSize = BatchSize.T
  object BatchSize extends Newtype[Int, Pure]:
    extension (b: BatchSize)
      /** How many more a batch already holding `taken` may take. */
      def after(taken: Int): Int = math.max(1, b.value) - taken

      def rows: Int = math.max(1, b.value)

  type FlushMillis = FlushMillis.T
  object FlushMillis extends Newtype[Int, Pure]:
    extension (f: FlushMillis) def millis: Long = math.max(1, f.value).toLong

  type Backlog = Backlog.T
  object Backlog extends Newtype[Int, Pure]:
    extension (b: Backlog)
      /** The newest of `pending` the backlog keeps, and how many it lets go. */
      def keep[A](pending: Vector[A]): (Vector[A], Int) =
        val keep = math.max(0, b.value)
        (pending.takeRight(keep), math.max(0, pending.size - keep))

  type PruneEveryMinutes = PruneEveryMinutes.T
  object PruneEveryMinutes extends Newtype[Int, Pure]:
    extension (p: PruneEveryMinutes)
      /** Whether a run is due at `nowMillis`, the last one at `lastMillis`. */
      def due(lastMillis: Long, nowMillis: Long): Boolean = nowMillis - lastMillis >= math.max(1, p.value) * 60000L

  type Counted = Counted.T
  object Counted extends Newtype[String, Pure]:
    extension (c: Counted)
      /** The counted path prefixes, each trimmed, empty ones left out. */
      def prefixes: List[CountedPrefix] =
        c.value.split(",").toList.map(_.trim).filter(_.nonEmpty).map(CountedPrefix(_))

  type CountedPrefix = CountedPrefix.T
  object CountedPrefix extends Newtype[String, Pure]:
    extension (p: CountedPrefix)
      /** Whether `path` is under it (`/web/` covers `/web` and `/web/app.js`). */
      def covers(path: String): Boolean = path == p.value.stripSuffix("/") || path.startsWith(p.value)
