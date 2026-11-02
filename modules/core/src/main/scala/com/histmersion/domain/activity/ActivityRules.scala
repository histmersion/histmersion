package com.histmersion.domain.activity

import com.histmersion.api.ApiError
import com.histmersion.api.automation.{Activity, ListFilters}
import com.histmersion.api.automation.Activity.{Entry, EntryKind, Show, Writer}

import java.time.Instant
import scala.annotation.tailrec

/** What the activity list's filters mean, entry by entry, and how a page of it is cut: the rules the database's query
  * follows in SQL, and the in-memory list follows here. Pure.
  */
object ActivityRules:

  /** The statuses of a run or job that is going now. */
  val RunningStates: Set[String] = Set("running")

  /** The statuses of a run or job that went wrong. */
  val ProblemStates: Set[String] = Set("failed")

  /** Does `e` belong to what `show` shows? */
  def shows(show: Show, e: Entry): Boolean =
    val kind = e.entryKind
    show match
      case Show.Everything => true
      case Show.Running    => isRunOrJob(e) && RunningStates.contains(e.state)
      case Show.Agents     =>
        kind.contains(EntryKind.Run) || (kind.contains(EntryKind.Log) && e.by.contains(Writer.Agent.wire))
      case Show.Jobs => kind.contains(EntryKind.Job) || (kind.contains(EntryKind.Log) && e.by.contains(Writer.Job.wire))
      case Show.Yours    => kind.contains(EntryKind.Decision)
      case Show.Problems => isRunOrJob(e) && ProblemStates.contains(e.state)

  private def isRunOrJob(e: Entry): Boolean = e.entryKind.exists(k => k == EntryKind.Run || k == EntryKind.Job)

  /** Does `e` pass every filter of `q` but the page's place? */
  def matches(q: ActivityQuery, e: Entry): Boolean =
    shows(q.show, e) && q.goal.forall(g => e.goal.contains(g)) && q.since.forall(s => !instantOf(e).isBefore(s))

  private def instantOf(e: Entry): Instant = Instant.parse(e.at)

  /** Newest first; entries of one instant by key, descending. */
  val newestFirst: Ordering[Entry] =
    Ordering.by[Entry, (Instant, String)](e => (instantOf(e), e.key)).reverse

  /** Is `e` older than the cursor (after it in the list)? */
  def after(c: ActivityCursor, e: Entry): Boolean =
    val at = instantOf(e)
    at.isBefore(c.at) || (at == c.at && e.key < c.key)

  /** The page `q` asks for out of `entries` (any order), with the cursor of the next one. */
  def page(entries: List[Entry], q: ActivityQuery, counts: Activity.Counts): Activity.Page =
    val kept = entries.filter(e => matches(q, e) && q.before.forall(after(_, e))).sorted(using newestFirst)
    val (rows, rest) = kept.splitAt(q.limit)
    Activity.Page(rows, Option.when(rest.nonEmpty)(rows.lastOption).flatten.map(cursorOf(_).wire), counts)

  def cursorOf(e: Entry): ActivityCursor = ActivityCursor(instantOf(e), e.key)

  /** The chips' counts over `entries`: running now, and failed since `weekAgo`. */
  def counts(entries: List[Entry], weekAgo: Instant): Activity.Counts =
    @tailrec def loop(rest: List[Entry], running: Int, problems: Int): Activity.Counts = rest match
      case e :: tail =>
        val run = isRunOrJob(e) && RunningStates.contains(e.state)
        val fail = isRunOrJob(e) && ProblemStates.contains(e.state) && !instantOf(e).isBefore(weekAgo)
        loop(tail, running + (if run then 1 else 0), problems + (if fail then 1 else 0))
      case Nil => Activity.Counts(running, problems)
    loop(entries, 0, 0)

  /** The query a client asked for, checked: `show` one of the chips' words, `since` a date, an instant or a time ago
    * (against `now`), `limit` within 1 … [[Activity.MaxLimit]], `before` a cursor this list gave.
    */
  def query(
      show: Option[String],
      since: Option[String],
      goal: Option[String],
      limit: Option[Int],
      before: Option[String],
      now: Instant
  ): Either[ApiError, ActivityQuery] =
    def bad(m: String) = ApiError(m)
    for
      s <- show
        .map(_.trim)
        .filter(_.nonEmpty)
        .fold(Right(Show.Everything))(w =>
          Show.parse(w).toRight(bad(s"unknown kind '$w' — one of: ${Show.values.map(_.wire).mkString(", ")}"))
        )
      t <- since
        .map(_.trim)
        .filter(_.nonEmpty)
        .fold(Right(None))(v => ListFilters.timeBound(v, end = false, now).map(Some(_)).left.map(bad))
      c <- before
        .map(_.trim)
        .filter(_.nonEmpty)
        .fold(Right(None))(v => ActivityCursor.parse(v).map(Some(_)).left.map(bad))
    yield ActivityQuery(
      s,
      t,
      goal.map(_.trim).filter(_.nonEmpty),
      limit.getOrElse(Activity.DefaultLimit).max(1).min(Activity.MaxLimit),
      c
    )
