package com.histmersion
package util.log

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

import java.time.Instant
import java.time.temporal.ChronoUnit

import LogConfig.{Backlog, BatchSize, Counted, Dir, FlushMillis, KeepDays, MaxBytes, PruneEveryMinutes, QueueSize}

final case class LogConfig(
    dir: Dir,
    keepDays: KeepDays,
    keepProblemDays: KeepDays,
    maxBytes: MaxBytes,
    queueSize: QueueSize,
    batchSize: BatchSize,
    flushMillis: FlushMillis,
    backlog: Backlog,
    pruneEveryMinutes: PruneEveryMinutes,
    counted: Counted
) derives ConfigReader:
  def countsPath(path: String): Boolean = counted.covers(path)

object LogConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]:
    def at(path: String): Dir = Dir(path)

    extension (d: Dir) def set: Option[String] = Option(d.value.trim).filter(_.nonEmpty)

  type KeepDays = KeepDays.T
  object KeepDays extends Newtype[Int, Pure]:
    extension (d: KeepDays) def before(now: Instant): Instant = now.minus(math.max(0, d.value).toLong, ChronoUnit.DAYS)

  type MaxBytes = MaxBytes.T
  object MaxBytes extends Newtype[Long, Pure]:
    extension (m: MaxBytes) def over(bytes: Double): Double = bytes - m.value.toDouble

  type QueueSize = QueueSize.T
  object QueueSize extends Newtype[Int, Pure]:
    extension (q: QueueSize) def capacity: Int = math.max(1, q.value)

  type BatchSize = BatchSize.T
  object BatchSize extends Newtype[Int, Pure]:
    extension (b: BatchSize)
      def after(taken: Int): Int = math.max(1, b.value) - taken

      def rows: Int = math.max(1, b.value)

  type FlushMillis = FlushMillis.T
  object FlushMillis extends Newtype[Int, Pure]:
    extension (f: FlushMillis) def millis: Long = math.max(1, f.value).toLong

  type Backlog = Backlog.T
  object Backlog extends Newtype[Int, Pure]:
    extension (b: Backlog)
      def keep[A](pending: Vector[A]): (Vector[A], Vector[A]) =
        val keep = math.max(0, b.value)
        (pending.takeRight(keep), pending.dropRight(keep))

  type PruneEveryMinutes = PruneEveryMinutes.T
  object PruneEveryMinutes extends Newtype[Int, Pure]:
    extension (p: PruneEveryMinutes)
      def due(lastMillis: Long, nowMillis: Long): Boolean = nowMillis - lastMillis >= math.max(1, p.value) * 60000L

  type Counted = Counted.T
  object Counted extends Newtype[String, Pure]:
    extension (c: Counted)
      def covers(path: String): Boolean =
        c.value
          .split(",")
          .iterator
          .map(_.trim)
          .filter(_.nonEmpty)
          .exists(p => path == p.stripSuffix("/") || path.startsWith(p))
