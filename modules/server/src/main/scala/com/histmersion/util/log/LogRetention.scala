package com.histmersion
package util.log

import com.histmersion.util.database.{Transactor, WritePermit}

import java.time.Instant
import scala.util.control.NonFatal

import LogRetention.excessRows

final class LogRetention(files: LogFiles, transactor: Transactor, gate: WritePermit.Gate):

  def run(cfg: LogConfig, now: Instant): Unit =
    val ordinary = cfg.keepDays.before(now)
    val problems = cfg.keepProblemDays.before(now)
    val gone = files.prune(now, ordinary, problems, cfg.maxBytes)
    if gone.nonEmpty then scribe.info(s"log retention: ${gone.size} log file(s) deleted")
    try
      gate.unit {
        gate.permit {
          transactor.write {
            val expired = LogRepo.deleteOlder(ordinary, problems)
            val (bytes, rows, dead) = LogRepo.size
            val over = excessRows(bytes, rows, dead, cfg.maxBytes)
            expired + (if over > 0 then LogRepo.deleteOldest(over) else 0L)
          }
        }
      } match
        case Right(deleted) => if deleted > 0 then scribe.info(s"log retention: $deleted event(s) deleted")
        case Left(_) => scribe.info("log retention: a restore holds the database; the table is pruned at the next run")
    catch
      case NonFatal(e) =>
        scribe.warn(s"log retention: the table is pruned at the next run (${e.getClass.getSimpleName})")

object LogRetention:
  val MaxDeleted: Int = 1000000

  def excessRows(bytes: Long, rows: Long, dead: Long, max: LogConfig.MaxBytes): Int =
    if rows <= 0 then 0
    else
      val perRow = math.max(1.0, bytes.toDouble / (rows + math.max(0L, dead)).toDouble)
      val over = max.over(perRow * rows.toDouble)
      if over <= 0 then 0
      else math.min(MaxDeleted.toLong, math.ceil(over / perRow).toLong).toInt
