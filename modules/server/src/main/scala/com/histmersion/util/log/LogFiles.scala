package com.histmersion
package util.log

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.storage.LineFiles

import java.time.Instant

import LogFiles.{dayOf, daysOf, fileOf, Problems}
import com.histmersion.lint.{tolerated, LintRule}

final class LogFiles(files: LineFiles):

  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def append(events: Vector[LogEvent]): Boolean =
    events.isEmpty ||
      files.append(
        events.groupBy(e => fileOf(e.problem, e.at.day)).map((file, group) => file -> group.map(writeToString(_)))
      )

  def page(q: LogQuery): (Vector[LogEvent], Option[LogPage.Cursor]) =
    val all = names
    @annotation.tailrec
    def loop(rest: List[String], found: Vector[LogEvent]): Vector[LogEvent] = rest match
      case day :: tail if !q.limit.below(found.size) =>
        val events = all.filter(f => dayOf(f).contains(day)).flatMap(read)
        loop(tail, found ++ events.filter(e => q.matches(e) && q.older(e)))
      case _ => found
    q.page(loop(daysOf(all).filter(q.mayHoldDay), Vector.empty))

  def names: List[String] = files.names.filter(dayOf(_).isDefined)

  def bytes: Long = names.map(files.size).sum

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

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def fileOf(problem: Boolean, day: String): String = s"${if problem then Problems else Events}$day$Suffix"

  def dayOf(name: String): Option[String] =
    List(Events, Problems)
      .find(name.startsWith)
      .map(prefix => name.stripPrefix(prefix).stripSuffix(Suffix))
      .filter(d => name.endsWith(Suffix) && d.matches("\\d{4}-\\d{2}-\\d{2}"))

  def daysOf(names: List[String]): List[String] = names.flatMap(dayOf).distinct.sorted.reverse
