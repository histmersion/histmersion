package com.histmersion
package util.log

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.storage.LineFiles

import java.time.Instant

import LogFiles.{dayOf, daysOf, fileOf, Problems}

/** The structured log's rolling files: one per UTC day and retention class, `events-<day>.jsonl` and
  * `problems-<day>.jsonl`, one event per line, in the same JSON as the table's `event` column. Every event is written
  * here as well as to the table, so the log can be searched while the database is down. Only the log writer appends.
  */
final class LogFiles(files: LineFiles):

  /** Append `events`, each to its day's file of its class. `false` when there is no directory or it cannot be written.
    */
  def append(events: Vector[LogEvent]): Boolean =
    events.isEmpty ||
      files.append(
        events.groupBy(e => fileOf(e.problem, e.at.day)).map((file, group) => file -> group.map(writeToString(_)))
      )

  /** The page `q` asks for, read from the files: days newest first, stopping once a whole day is past the page. Lines
    * that do not read as an event (one being written now) are skipped.
    */
  def page(q: LogQuery): (Vector[LogEvent], Option[LogPage.Cursor]) =
    val all = names
    @annotation.tailrec
    def loop(rest: List[String], found: Vector[LogEvent]): Vector[LogEvent] = rest match
      case day :: tail if !q.limit.below(found.size) =>
        val events = all.filter(f => dayOf(f).contains(day)).flatMap(read)
        loop(tail, found ++ events.filter(e => q.matches(e) && q.older(e)))
      case _ => found
    q.page(loop(daysOf(all).filter(q.mayHoldDay), Vector.empty))

  /** The names of the log's files. */
  def names: List[String] = files.names.filter(dayOf(_).isDefined)

  /** The files' total size. */
  def bytes: Long = names.map(files.size).sum

  /** Retention at `now`: ordinary files of days before `ordinaryBefore`, problem files of days before `problemsBefore`,
    * then the oldest until the files fit in `maxBytes` (today's never). The names of the files deleted.
    */
  def prune(
      now: Instant,
      ordinaryBefore: Instant,
      problemsBefore: Instant,
      maxBytes: LogConfig.MaxBytes
  ): List[String] =
    val today = LogEvent.At.of(now).day
    val ordinary = LogEvent.At.of(ordinaryBefore).day
    val problems = LogEvent.At.of(problemsBefore).day
    val expired = names.filter { f =>
      val day = dayOf(f).getOrElse(today)
      if f.startsWith(Problems) then day < problems else day < ordinary
    }
    val gone = expired.filter(files.delete)
    val left = names.sortBy(f => dayOf(f).getOrElse(today))
    @annotation.tailrec
    def shrink(rest: List[String], total: Long, deleted: List[String]): List[String] = rest match
      case f :: tail if maxBytes.over(total.toDouble) > 0 && !dayOf(f).contains(today) =>
        val size = files.size(f)
        if files.delete(f) then shrink(tail, total - size, f :: deleted) else shrink(tail, total, deleted)
      case _ => deleted
    gone ++ shrink(left, left.map(files.size).sum, Nil).reverse

  private def read(f: String): Vector[LogEvent] = files.lines(f).flatMap(LogRepo.decode)

object LogFiles:
  val Events = "events-"
  val Problems = "problems-"
  val Suffix = ".jsonl"

  /** The file of the events of `day`, problems (`problem`) or ordinary ones. */
  def fileOf(problem: Boolean, day: String): String = s"${if problem then Problems else Events}$day$Suffix"

  /** The day of a log file's name, `None` for any other file. */
  def dayOf(name: String): Option[String] =
    List(Events, Problems)
      .find(name.startsWith)
      .map(prefix => name.stripPrefix(prefix).stripSuffix(Suffix))
      .filter(d => name.endsWith(Suffix) && d.matches("\\d{4}-\\d{2}-\\d{2}"))

  /** The days of the files `names`, newest first, each once. */
  def daysOf(names: List[String]): List[String] = names.flatMap(dayOf).distinct.sorted.reverse
