package com.histmersion
package util.log

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.histmersion.api.log.{LogEvent, LogPage}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** The structured log's rolling files in `dir`: one per UTC day and retention class, `events-<day>.jsonl` and
  * `problems-<day>.jsonl`, one event per line, in the same JSON as the table's `event` column. Every event is written
  * here as well as to the table, so the log can be searched while the database is down. Only the log writer appends.
  */
final class LogFiles(dir: () => Option[Path]):

  /** Append `events`, each to its day's file of its class. `false` when there is no directory or it cannot be written.
    */
  def append(events: Vector[LogEvent]): Boolean =
    events.isEmpty || dir().exists { d =>
      Try {
        val _ = Files.createDirectories(d)
        events.groupBy(e => LogFiles.name(e)).foreach { (name, group) =>
          val w = Files.newBufferedWriter(
            d.resolve(name),
            UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE
          )
          try
            group.foreach { e =>
              w.write(writeToString(e))
              w.newLine()
            }
          finally w.close()
        }
      }.isSuccess
    }

  /** The page `q` asks for, read from the files: days newest first, stopping once a whole day is past the page. Lines
    * that do not read as an event (one being written now) are skipped.
    */
  def page(q: LogQuery): (Vector[LogEvent], Option[LogPage.Cursor]) =
    val days = LogFiles.days(files).filter(d => LogFiles.within(d, q))
    @annotation.tailrec
    def loop(rest: List[String], found: Vector[LogEvent]): Vector[LogEvent] = rest match
      case day :: tail if found.size <= q.limit.value =>
        val events = files.filter(f => LogFiles.dayOf(f).contains(day)).flatMap(read)
        loop(tail, found ++ events.filter(e => q.matches(e) && q.older(e)))
      case _ => found
    q.page(loop(days, Vector.empty))

  /** Every file of the log, with its size. */
  def files: List[Path] =
    dir().filter(Files.isDirectory(_)).toList.flatMap { d =>
      val stream = Files.list(d)
      try stream.iterator().asScala.filter(p => LogFiles.dayOf(p).isDefined).toList
      finally stream.close()
    }

  /** The files' total size. */
  def bytes: Long = files.map(f => Try(Files.size(f)).getOrElse(0L)).sum

  /** Retention: ordinary files older than `keepDays`, problem files older than `keepProblemDays`, then the oldest until
    * the files fit in `maxBytes` (today's never). The files deleted.
    */
  def prune(now: Instant, keepDays: Int, keepProblemDays: Int, maxBytes: Long): List[Path] =
    val today = LogStamp.day(LogStamp.iso(now))
    val ordinary = LogStamp.day(LogStamp.iso(now.minus(keepDays.toLong, ChronoUnit.DAYS)))
    val problems = LogStamp.day(LogStamp.iso(now.minus(keepProblemDays.toLong, ChronoUnit.DAYS)))
    val expired = files.filter { f =>
      val day = LogFiles.dayOf(f).getOrElse(today)
      if f.getFileName.toString.startsWith(LogFiles.Problems) then day < problems else day < ordinary
    }
    val gone = expired.filter(delete)
    val left = files.sortBy(f => LogFiles.dayOf(f).getOrElse(today))
    @annotation.tailrec
    def shrink(rest: List[Path], total: Long, deleted: List[Path]): List[Path] = rest match
      case f :: tail if total > maxBytes && !LogFiles.dayOf(f).contains(today) =>
        val size = Try(Files.size(f)).getOrElse(0L)
        if delete(f) then shrink(tail, total - size, f :: deleted) else shrink(tail, total, deleted)
      case _ => deleted
    gone ++ shrink(left, left.map(f => Try(Files.size(f)).getOrElse(0L)).sum, Nil).reverse

  private def delete(f: Path): Boolean = Try(Files.deleteIfExists(f)).getOrElse(false)

  private def read(f: Path): Vector[LogEvent] =
    Try(Files.readAllLines(f, UTF_8).asScala.toVector).getOrElse(Vector.empty).flatMap(LogRepo.decode)

object LogFiles:
  val Events = "events-"
  val Problems = "problems-"
  val Suffix = ".jsonl"

  /** The file `e` goes in. */
  def name(e: LogEvent): String =
    s"${if e.problem then Problems else Events}${LogStamp.day(e.at.value)}$Suffix"

  /** The day of a log file's name, `None` for any other file. */
  def dayOf(f: Path): Option[String] =
    val n = f.getFileName.toString
    List(Events, Problems)
      .find(n.startsWith)
      .map(prefix => n.stripPrefix(prefix).stripSuffix(Suffix))
      .filter(d => n.endsWith(Suffix) && d.matches("\\d{4}-\\d{2}-\\d{2}"))

  /** The days of `files`, newest first, each once. */
  def days(files: List[Path]): List[String] = files.flatMap(dayOf).distinct.sorted.reverse

  /** Can events of `day` match `q`'s time bounds and page? */
  def within(day: String, q: LogQuery): Boolean =
    q.since.forall(s => day >= LogStamp.day(LogStamp.iso(s))) &&
      q.until.forall(u => day <= LogStamp.day(LogStamp.iso(u))) &&
      q.before.forall(p => day <= LogStamp.day(LogStamp.iso(p.at)))
