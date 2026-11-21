package com.histmersion
package util.log

import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw, TypeName}
import com.histmersion.util.time.TimeEquality.instantEquality

import java.time.Instant

import LogQuery.{newestFirst, Position, Store}

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
    limit: LogPage.Limit,
    before: Option[Position],
    store: Store
):
  /** Does `e` pass every filter but the page's place? */
  def matches(e: LogEvent): Boolean =
    val at = e.at.instant
    trace.forall(_ == e.trace) && label.forall(l => e.label.contains(l)) &&
    subjects.forall(e.subjects.contains) && kind.forall(_ == e.kind) &&
    since.forall(s => !at.isBefore(s)) && until.forall(u => !at.isAfter(u))

  /** Can events of the UTC `day` (`2026-11-20`) match the time bounds and the page's place? */
  def mayHoldDay(day: String): Boolean =
    since.forall(s => day >= LogEvent.At.of(s).day) &&
      until.forall(u => day <= LogEvent.At.of(u).day) &&
      before.forall(p => day <= LogEvent.At.of(p.at).day)

  /** Is `e` older than the page's place (after it in the list)? */
  def older(e: LogEvent): Boolean = before.forall(_.precedes(e))

  /** The page out of `events` (any order): newest first, at most `limit`, with the cursor of the next older one. */
  def page(events: Iterable[LogEvent]): (Vector[LogEvent], Option[LogPage.Cursor]) =
    val kept = events.iterator.filter(e => matches(e) && older(e)).toVector.sorted(using newestFirst)
    val (rows, rest) = limit.cut(kept)
    (rows, Option.when(rest.nonEmpty)(rows.lastOption).flatten.map(e => LogPage.Cursor.of(e.at, e.id)))

object LogQuery:

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
      val t = e.at.instant
      t.isBefore(at) || (t == at && e.id.sortsBefore(id))

  val newestFirst: Ordering[LogEvent] =
    Ordering.by[LogEvent, (Instant, LogEvent.Id)](e => (e.at.instant, e.id)).reverse

  def position(c: LogPage.Cursor): Either[Problem, Position] =
    c.place.map(Position(_, _)).toRight(Invalid.Broken.here(Invalid.Rule.LogPage(Raw.of(c))))

  /** Every step of one trace, newest first. */
  def ofTrace(trace: LogEvent.Trace, limit: LogPage.Limit): LogQuery =
    LogQuery(Some(trace), None, Vector.empty, None, None, None, limit, None, Store.Database)

  /** Every step concerning `subject`, newest first. */
  def ofSubject(subject: LogEvent.Subject, limit: LogPage.Limit): LogQuery =
    LogQuery(None, None, Vector(subject), None, None, None, limit, None, Store.Database)

  /** The query a client asked for, checked against `now`: `kind` one of the event cases, `since`/`until` a date, an
    * instant or a time ago, `limit` within 1 … [[LogPage.MaxLimit]], `before` a cursor this log gave, `from` `database`
    * or `file`.
    */
  def parse(f: LogPage.Filter, now: Instant): Either[Problem, LogQuery] =
    for
      trace <- f.trace
        .flatMap(_.present)
        .fold(Right(None))(t =>
          Option
            .when(t.wellFormed)(Some(t))
            .toRight(
              Invalid.Unreadable
                .here(TypeName.of(LogEvent.Trace), Raw.of(t), Vector(Raw("0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f")))
            )
        )
      kind <- f.kind
        .filter(_.present)
        .fold(Right(None))(k =>
          k.known
            .map(Some(_))
            .toRight(
              Invalid.Unknown.here(TypeName.of(LogEvent.Kind), Raw.of(k), LogEvent.Kinds.toVector.map(Raw.of(_)))
            )
        )
      since <- f.since.flatMap(_.present).fold(Right(None))(_.instant(end = false, now).map(Some(_)))
      until <- f.until.flatMap(_.present).fold(Right(None))(_.instant(end = true, now).map(Some(_)))
      before <- f.before.flatMap(_.present).fold(Right(None))(c => position(c).map(Some(_)))
      store <- f.from
        .flatMap(_.present)
        .fold(Right(Store.Database))(s =>
          Store.values
            .find(v => s.names(v.toString))
            .toRight(Invalid.Unknown.here(TypeName.of(LogPage.From), Raw.of(s), Store.values.toVector.map(Raw.of(_))))
        )
    yield LogQuery(
      trace,
      f.label.flatMap(_.present),
      Vector(
        f.job.flatMap(_.present).map(LogEvent.Subject.Job(_)),
        f.run.flatMap(_.present).map(LogEvent.Subject.AgentRun(_)),
        f.source.flatMap(_.present).map(LogEvent.Subject.Source(_)),
        f.problem.flatMap(_.present).map(_.subject)
      ).flatten,
      kind,
      since,
      until,
      LogPage.Limit.clamped(f.limit),
      before,
      store
    )
