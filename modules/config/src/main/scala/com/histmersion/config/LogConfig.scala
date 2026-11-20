package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The structured log: where its rolling files are (`dir`, empty: `<data dir>/logs/structured`), how long its events are kept
  * (ordinary ones `keepDays`, problems `keepProblemDays`) and how large the table and the files may grow (`maxBytes`
  * each; the oldest go first), how the writer batches, and which request paths are counted per minute rather than
  * logged one by one.
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
  /** The counted path prefixes, each trimmed, empty ones left out. */
  def countedPrefixes: List[LogConfig.CountedPrefix] =
    counted.value.split(",").toList.map(_.trim).filter(_.nonEmpty).map(LogConfig.CountedPrefix(_))

object LogConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type KeepDays = KeepDays.T
  object KeepDays extends Newtype[Int, Pure]
  type MaxBytes = MaxBytes.T
  object MaxBytes extends Newtype[Long, Pure]
  type QueueSize = QueueSize.T
  object QueueSize extends Newtype[Int, Pure]
  type BatchSize = BatchSize.T
  object BatchSize extends Newtype[Int, Pure]
  type FlushMillis = FlushMillis.T
  object FlushMillis extends Newtype[Int, Pure]
  type Backlog = Backlog.T
  object Backlog extends Newtype[Int, Pure]
  type PruneEveryMinutes = PruneEveryMinutes.T
  object PruneEveryMinutes extends Newtype[Int, Pure]
  type Counted = Counted.T
  object Counted extends Newtype[String, Pure]
  type CountedPrefix = CountedPrefix.T
  object CountedPrefix extends Newtype[String, Pure]
