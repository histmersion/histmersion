package com.histmersion
package util.log

import com.augustnagro.magnum.*
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

trait LogService:
  def page(q: LogQuery): LogPage

  def problem(id: LogEvent.ProblemId, limit: LogPage.Limit): Option[LogPage]

object LogService:
  val ReadTimeout: Transactor.TimeoutMs = Transactor.TimeoutMs(10000)

  def apply(transactor: Transactor, files: LogFiles): LogService = Implementation(transactor, files)

  final private class Implementation(transactor: Transactor, files: LogFiles) extends LogService:
    def page(q: LogQuery): LogPage =
      q.store match
        case LogQuery.Store.File     => fromFiles(q)
        case LogQuery.Store.Database =>
          try
            val rows = transactor.readOnlyRollback(ReadTimeout) {
              LogRepo.page(where(q), q.limit.andOne)
            }
            val (events, next) = q.page(rows)
            LogPage(events, next, LogPage.Origin.Database)
          catch case NonFatal(_) => fromFiles(q)

    def problem(id: LogEvent.ProblemId, limit: LogPage.Limit): Option[LogPage] =
      val trace =
        try
          transactor
            .readOnlyRollback(ReadTimeout)(
              LogRepo
                .page(where(LogQuery.ofSubject(id.subject, LogPage.Limit.one)), LogPage.Limit.one, oldestFirst = true)
            )
            .headOption
            .map(_.trace)
        catch case NonFatal(_) => None
      trace
        .orElse(files.page(LogQuery.ofSubject(id.subject, LogPage.Limit.one))._1.headOption.map(_.trace))
        .map(t => page(LogQuery.ofTrace(t, limit)).oldestFirst)

    private def fromFiles(q: LogQuery): LogPage =
      val (events, next) = files.page(q)
      LogPage(events, next, LogPage.Origin.File)

  private[log] def where(q: LogQuery): Frag =
    import com.histmersion.util.database.given
    import com.histmersion.util.database.NewtypeColumns.given
    val subjects: List[String] = q.subjects.map(_.ref).toList
    sql"""${q.trace.fold(sql"")(t => sql" AND trace = $t::uuid")}${q.label.fold(sql"")(l => sql" AND label = $l")}${
        if subjects.isEmpty then sql"" else sql" AND subjects @> $subjects::text[]"
      }${q.kind.fold(sql"")(k => sql" AND kind = $k")}${q.since.fold(sql"")(s =>
        sql" AND at >= ${LogEvent.At.of(s)}::timestamptz"
      )}${q.until.fold(sql"")(u => sql" AND at <= ${LogEvent.At.of(u)}::timestamptz")}${q.before.fold(sql"")(p =>
        sql" AND (at, id) < (${LogEvent.At.of(p.at)}::timestamptz, ${p.id}::uuid)"
      )}"""

  final class InMemory(events: AtomicReference[Vector[LogEvent]] = AtomicReference(Vector.empty)) extends LogService:
    def add(e: LogEvent): Unit = events.updateAndGet(_ :+ e): Unit

    def page(q: LogQuery): LogPage =
      val (rows, next) = q.page(events.get)
      LogPage(rows, next, LogPage.Origin.Database)

    def problem(id: LogEvent.ProblemId, limit: LogPage.Limit): Option[LogPage] =
      events.get
        .find(_.subjects.contains(id.subject))
        .map(e => page(LogQuery.ofTrace(e.trace, limit)).oldestFirst)
