package com.histmersion
package util.log

import com.histmersion.config.LogConfig
import com.histmersion.util.database.{Transactor, WritePermit}

import java.time.Instant
import scala.util.control.NonFatal

/** The structured log's retention, the same for the table and the files: ordinary events are kept `keep-days`, problems
  * `keep-problem-days`, and each store at most `max-bytes`, the oldest going first.
  */
final class LogRetention(files: LogFiles, transactor: Transactor, gate: WritePermit.Gate):

  /** One run over the files and the table at `now`. A database that cannot be reached now is pruned at the next run.
    */
  def run(cfg: LogConfig, now: Instant): Unit =
    val ordinary = cfg.keepDays.before(now)
    val problems = cfg.keepProblemDays.before(now)
    val _ = files.prune(now, ordinary, problems, cfg.maxBytes)
    try
      val _ = gate.unit {
        gate.permit {
          transactor.write {
            val _ = LogRepo.deleteOlder(LogStamp.iso(ordinary), LogStamp.iso(problems))
            val (bytes, rows, dead) = LogRepo.size
            val over = LogRetention.excessRows(bytes, rows, dead, cfg.maxBytes)
            if over > 0 then
              val _ = LogRepo.deleteOldest(over)
          }
        }
      }
    catch
      case NonFatal(e) =>
        scribe.warn(s"log retention: the table is pruned at the next run (${e.getClass.getSimpleName})")

object LogRetention:
  /** How many of the oldest rows one run of the size cap deletes at most (the next run goes on). */
  val MaxDeleted: Int = 1000000

  /** The rows to delete so that the live rows of a table of `bytes` (holding `rows` live and `dead` dead ones) fit in
    * `max`, at the table's average row size. The space a delete frees is reused, not returned to the disk, so the size
    * alone would keep asking for more; the live share of it is what counts.
    */
  def excessRows(bytes: Long, rows: Long, dead: Long, max: LogConfig.MaxBytes): Int =
    if rows <= 0 then 0
    else
      val perRow = math.max(1.0, bytes.toDouble / (rows + math.max(0L, dead)).toDouble)
      val over = max.over(perRow * rows.toDouble)
      if over <= 0 then 0
      else math.min(MaxDeleted.toLong, math.ceil(over / perRow).toLong).toInt
