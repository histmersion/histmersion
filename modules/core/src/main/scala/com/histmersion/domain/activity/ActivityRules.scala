package com.histmersion
package domain.activity

import com.histmersion.api.automation.activity.Entry
import com.histmersion.api.automation.activity.Show
import com.histmersion.api.automation.activity.EntryKind
import com.histmersion.api.automation.activity.Writer
import com.histmersion.api.automation.activity.Counts
import com.histmersion.api.automation.activity.Page

import com.histmersion.util.problem.Problem.{Invalid, Raw, TypeName}
import com.histmersion.api.automation.{Activity, ListFilters}

import java.time.Instant
import scala.annotation.tailrec
import com.histmersion.api.Api
import com.histmersion.util.time.TimeEquality.instantEquality
import com.histmersion.util.problem.Problems
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** What the activity list's filters mean, entry by entry, and how a page of it is cut: the rules the database's query
  * follows in SQL, and the in-memory list follows here. Pure.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.AutomationActivity)
object ActivityRules:
  /** The statuses of a run or job that is going now. */
  val RunningStates: Set[Entry.State] = Set(Entry.State("running"))

  /** The statuses of a run or job that went wrong. */
  val ProblemStates: Set[Entry.State] = Set(Entry.State("failed"))

  /** Does `e` belong to what `show` shows? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def shows(show: Show, e: Entry): Boolean =
    val kind = e.entryKind
    show match
      case Show.Everything => true
      case Show.Running    => isRunOrJob(e) && RunningStates.contains(e.state)
      case Show.Agents     =>
        kind
          .contains(EntryKind.Run) || (kind.contains(EntryKind.Log) && e.by.exists(_ == Writer.Agent.wire))
      case Show.Jobs =>
        kind.contains(EntryKind.Job) || (kind.contains(EntryKind.Log) && e.by.exists(_ == Writer.Job.wire))
      case Show.Yours    => kind.contains(EntryKind.Decision)
      case Show.Problems => isRunOrJob(e) && ProblemStates.contains(e.state)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def isRunOrJob(e: Entry): Boolean = e.entryKind.exists(k => k == EntryKind.Run || k == EntryKind.Job)

  /** Does `e` pass every filter of `q` but the page's place? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matches(q: ActivityQuery, e: Entry): Boolean =
    shows(q.show, e) && q.goal.forall(g => e.goal.exists(_.value == g.value)) && q.since.forall(s =>
      !instantOf(e).isBefore(s)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def instantOf(e: Entry): Instant = Instant.parse(e.at.value)

  /** Newest first; entries of one instant by key, descending. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val newestFirst: Ordering[Entry] =
    Ordering.by[Entry, (Instant, String)](e => (instantOf(e), e.key.value)).reverse

  /** Is `e` older than the cursor (after it in the list)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def after(c: ActivityCursor, e: Entry): Boolean =
    val at = instantOf(e)
    at.isBefore(c.at) || (at == c.at && e.key.value < c.key.value)

  /** The page `q` asks for out of `entries` (any order), with the cursor of the next one. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page(entries: List[Entry], q: ActivityQuery, counts: Counts): Page =
    val kept = entries.filter(e => matches(q, e) && q.before.forall(after(_, e))).sorted(using newestFirst)
    val (rows, rest) = kept.splitAt(q.limit.value)
    Page(
      rows,
      Option.when(rest.nonEmpty)(rows.lastOption).flatten.map(e => Page.Next(cursorOf(e).wire.value)),
      counts
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cursorOf(e: Entry): ActivityCursor = ActivityCursor(instantOf(e), ActivityCursor.Key(e.key.value))

  /** The chips' counts over `entries`: running now, and failed since `weekAgo`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def counts(entries: List[Entry], weekAgo: Instant): Counts =
    @tailrec def loop(rest: List[Entry], running: Int, problems: Int): Counts = rest match
      case e :: tail =>
        val run = isRunOrJob(e) && RunningStates.contains(e.state)
        val fail = isRunOrJob(e) && ProblemStates.contains(e.state) && !instantOf(e).isBefore(weekAgo)
        loop(tail, running + (if run then 1 else 0), problems + (if fail then 1 else 0))
      case Nil => Counts(Counts.Running(running), Counts.ProblemsWeek(problems))
    loop(entries, 0, 0)

  /** The query a client asked for, checked: `show` one of the chips' words, `since` a date, an instant or a time ago
    * (against `now`), `limit` within 1 … [[Activity.MaxLimit]], `before` a cursor this list gave.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def query(
      show: Option[Show.Wire],
      since: Option[ListFilters.TimeBound],
      goal: Option[ActivityQuery.Goal],
      limit: Option[ActivityQuery.Limit],
      before: Option[ActivityCursor.Wire],
      now: Instant
  ): Either[Problems, ActivityQuery] =
    for
      s <- show
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .fold(Right(Show.Everything))(w =>
          Show
            .parse(Show.Wire(w))
            .toRight(
              Invalid.Unknown.here(TypeName.of(Show.Wire), Raw(w), Show.values.toVector.map(s => Raw.of(s.wire)))
            )
        )
      t <- since
        .map(v => ListFilters.TimeBound(v.value.trim))
        .filter(_.value.nonEmpty)
        .fold(Right(None))(v => ListFilters.timeBound(v, end = false, now).map(Some(_)))
      c <- before
        .map(w => ActivityCursor.Wire(w.value.trim))
        .filter(_.value.nonEmpty)
        .fold(Right(None))(v => ActivityCursor.parse(v).map(Some(_)))
    yield ActivityQuery(
      s,
      t,
      goal.map(g => ActivityQuery.Goal(g.value.trim)).filter(_.value.nonEmpty),
      ActivityQuery.Limit(limit.fold(Activity.DefaultLimit)(_.value).max(1).min(Activity.MaxLimit)),
      c
    )
