package com.histmersion
package util.log

import com.histmersion.api.automation.ListFilters
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw, TypeName}
import com.histmersion.util.time.TimeEquality.instantEquality
import io.github.iltotore.iron.constraint.any.Pure

import java.time.Instant

/** A checked query of the structured log: what an event must match, the page's size and place, and which store to read.
  * The rules the table's SQL follows, and the file's reader follows here. Pure.
  */
final case class LogQuery(
    trace: Option[LogEvent.Trace],
    label: Option[LogEvent.Label],
    subjects: Vector[LogEvent.Subject],
    kind: Option[LogEvent.Kind],
    since: Option[Instant],
    until: Option[Instant],
    limit: LogQuery.Limit,
    before: Option[LogQuery.Position],
    store: LogQuery.Store
):
  /** Does `e` pass every filter but the page's place? */
  def matches(e: LogEvent): Boolean =
    val at = LogQuery.instantOf(e)
    trace.forall(_ == e.trace) && label.forall(l => e.label.contains(l)) &&
    subjects.forall(e.subjects.contains) && kind.forall(_ == e.kind) &&
    since.forall(s => !at.isBefore(s)) && until.forall(u => !at.isAfter(u))

  /** Is `e` older than the page's place (after it in the list)? */
  def older(e: LogEvent): Boolean = before.forall(_.precedes(e))

  /** The page out of `events` (any order): newest first, at most `limit`, with the cursor of the next older one. */
  def page(events: Iterable[LogEvent]): (Vector[LogEvent], Option[LogPage.Cursor]) =
    val kept = events.iterator.filter(e => matches(e) && older(e)).toVector.sorted(using LogQuery.newestFirst)
    val (rows, rest) = kept.splitAt(limit.value)
    (rows, Option.when(rest.nonEmpty)(rows.lastOption).flatten.map(LogQuery.cursorOf))

object LogQuery:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  /** Which store a query reads. */
  enum Store derives CanEqual:
    /** The table; the file when the database cannot answer. */
    case Database

    /** The file alone. */
    case File

  /** An event's place in the list (newest first; one instant's events by id, descending): where an older page starts.
    */
  final case class Position(at: Instant, id: LogEvent.Id):
    /** Does `e` come after this place? */
    def precedes(e: LogEvent): Boolean =
      val t = instantOf(e)
      t.isBefore(at) || (t == at && e.id.value < id.value)

  /** A trace as the server mints it: a UUID, in lower case. */
  val TraceShape: scala.util.matching.Regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  private val TraceExample = "0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f"

  def instantOf(e: LogEvent): Instant = Instant.parse(e.at.value)

  val newestFirst: Ordering[LogEvent] =
    Ordering.by[LogEvent, (Instant, String)](e => (instantOf(e), e.id.value)).reverse

  def cursorOf(e: LogEvent): LogPage.Cursor = LogPage.Cursor(s"${e.at.value}~${e.id.value}")

  def position(c: LogPage.Cursor): Either[Problem, Position] =
    c.value.trim.split("~", 2) match
      case Array(at, id) if id.nonEmpty =>
        scala.util
          .Try(Instant.parse(at))
          .toOption
          .map(Position(_, LogEvent.Id(id)))
          .toRight(Invalid.Broken.here(Invalid.Rule.LogPage(Raw.of(c))))
      case _ => Left(Invalid.Broken.here(Invalid.Rule.LogPage(Raw.of(c))))

  /** Every step of one trace, newest first. */
  def ofTrace(trace: LogEvent.Trace, limit: Int): LogQuery =
    LogQuery(Some(trace), None, Vector.empty, None, None, None, Limit(limit), None, Store.Database)

  /** Every step concerning `subject`, newest first. */
  def ofSubject(subject: LogEvent.Subject, limit: Int): LogQuery =
    LogQuery(None, None, Vector(subject), None, None, None, Limit(limit), None, Store.Database)

  /** The query a client asked for, checked against `now`: `kind` one of the event cases, `since`/`until` a date, an
    * instant or a time ago, `limit` within 1 … [[LogPage.MaxLimit]], `before` a cursor this log gave, `from` `database`
    * or `file`.
    */
  def parse(f: LogPage.Filter, now: Instant): Either[Problem, LogQuery] =
    def nonEmpty[T](v: Option[T], text: T => String): Option[String] = v.map(text(_).trim).filter(_.nonEmpty)
    for
      trace <- nonEmpty(f.trace, _.value).fold(Right(None))(t =>
        Option
          .when(TraceShape.matches(t.toLowerCase))(Some(LogEvent.Trace(t.toLowerCase)))
          .toRight(Invalid.Unreadable.here(TypeName.of(LogEvent.Trace), Raw(t), Vector(Raw(TraceExample))))
      )
      kind <- nonEmpty(f.kind, _.value).fold(Right(None))(k =>
        LogEvent.Kinds
          .find(_.equalsIgnoreCase(k))
          .map(found => Some(LogEvent.Kind(found)))
          .toRight(Invalid.Unknown.here(TypeName.of(LogEvent.Kind), Raw(k), LogEvent.Kinds.toVector.map(Raw(_))))
      )
      since <- nonEmpty(f.since, _.value).fold(Right(None))(s =>
        ListFilters.timeBound(ListFilters.TimeBound(s), end = false, now).map(Some(_))
      )
      until <- nonEmpty(f.until, _.value).fold(Right(None))(u =>
        ListFilters.timeBound(ListFilters.TimeBound(u), end = true, now).map(Some(_))
      )
      before <- f.before.filter(_.value.trim.nonEmpty).fold(Right(None))(c => position(c).map(Some(_)))
      store <- nonEmpty(f.from, _.value).fold(Right(Store.Database))(s =>
        Store.values
          .find(_.toString.equalsIgnoreCase(s))
          .toRight(
            Invalid.Unknown.here(TypeName.of(LogPage.From), Raw(s), Store.values.toVector.map(v => Raw(v.toString)))
          )
      )
    yield LogQuery(
      trace,
      nonEmpty(f.label, _.value).map(LogEvent.Label(_)),
      Vector(
        nonEmpty(f.job, _.value).map(k => LogEvent.Subject.Job(LogEvent.Key(k))),
        nonEmpty(f.run, _.value).map(k => LogEvent.Subject.AgentRun(LogEvent.Key(k))),
        nonEmpty(f.source, _.value).map(k => LogEvent.Subject.Source(LogEvent.Key(k))),
        nonEmpty(f.problem, _.value).map(k => LogEvent.Subject.Problem(LogEvent.Key(k)))
      ).flatten,
      kind,
      since,
      until,
      Limit(f.limit.fold(LogPage.DefaultLimit)(_.value).max(1).min(LogPage.MaxLimit)),
      before,
      store
    )
