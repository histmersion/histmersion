package com.histmersion
package util.log

import com.augustnagro.magnum.*
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

/** Reading the structured log: a page of a query, and the trace a problem was raised in. The table is read; when the
  * database cannot answer, or the query asks for it, the files are.
  */
trait LogService:
  def page(q: LogQuery): LogPage

  /** Every step of the trace `id` was raised in (oldest first), or `None` when no event names that problem. */
  def problem(id: LogEvent.ProblemId, limit: LogPage.Limit): Option[LogPage]

object LogService:
  /** How long a read of the table may take before the files are read instead. */
  val ReadTimeoutMs: Int = 10000

  def apply(transactor: Transactor, files: LogFiles): LogService = Implementation(transactor, files)

  final private class Implementation(transactor: Transactor, files: LogFiles) extends LogService:
    def page(q: LogQuery): LogPage =
      q.store match
        case LogQuery.Store.File     => fromFiles(q)
        case LogQuery.Store.Database =>
          try
            val rows = transactor.readOnlyRollback(Transactor.TimeoutMs(ReadTimeoutMs)) {
              LogRepo.page(where(q), q.limit.andOne)
            }
            val (events, next) = q.page(rows)
            LogPage(events, next, LogPage.Origin.Database)
          catch case NonFatal(_) => fromFiles(q)

    def problem(id: LogEvent.ProblemId, limit: LogPage.Limit): Option[LogPage] =
      val trace =
        try transactor.readOnlyRollback(Transactor.TimeoutMs(ReadTimeoutMs))(LogRepo.traceOfProblem(id))
        catch case NonFatal(_) => None
      trace
        .orElse(files.page(LogQuery.ofSubject(id.subject, LogPage.Limit(1)))._1.headOption.map(_.trace))
        .map(t => oldestFirst(page(LogQuery.ofTrace(t, limit))))

    private def fromFiles(q: LogQuery): LogPage =
      val (events, next) = files.page(q)
      LogPage(events, next, LogPage.Origin.File)

  /** The filters of `q` as SQL, each one only when set. */
  private[log] def where(q: LogQuery): Frag =
    import com.histmersion.util.database.given
    import com.histmersion.util.database.NewtypeColumns.given
    val subjects: List[String] = q.subjects.map(_.ref).toList
    sql"""${q.trace.fold(sql"")(t => sql" AND trace = $t::uuid")}${q.label.fold(sql"")(l => sql" AND label = $l")}${
        if subjects.isEmpty then sql"" else sql" AND subjects @> $subjects::text[]"
      }${q.kind.fold(sql"")(k => sql" AND kind = $k")}${q.since.fold(sql"")(s =>
        sql" AND at >= ${LogStamp.iso(s)}::timestamptz"
      )}${q.until.fold(sql"")(u => sql" AND at <= ${LogStamp.iso(u)}::timestamptz")}${q.before.fold(sql"")(p =>
        sql" AND (at, id) < (${LogStamp.iso(p.at)}::timestamptz, ${p.id}::uuid)"
      )}"""

  private def oldestFirst(p: LogPage): LogPage = LogPage(p.events.reverse, p.next, p.origin)

  /** The same reads over events held in memory (tests): written with [[add]]. */
  final class InMemory(events: AtomicReference[Vector[LogEvent]] = AtomicReference(Vector.empty)) extends LogService:
    def add(e: LogEvent): Unit = events.updateAndGet(_ :+ e): Unit

    def page(q: LogQuery): LogPage =
      val (rows, next) = q.page(events.get)
      LogPage(rows, next, LogPage.Origin.Database)

    def problem(id: LogEvent.ProblemId, limit: LogPage.Limit): Option[LogPage] =
      events.get
        .find(_.subjects.contains(id.subject))
        .map(e => oldestFirst(page(LogQuery.ofTrace(e.trace, limit))))
