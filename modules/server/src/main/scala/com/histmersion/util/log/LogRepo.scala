package com.histmersion
package util.log

import com.augustnagro.magnum.*
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import com.histmersion.api.log.LogEvent
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Timestamps

import scala.util.Try

/** The structured log's two tables: `log_events` (every event, as its JSON and the columns queries filter on) and
  * `log_writes` (the write statements the triggers recorded, waiting for the writer). The only code that writes either
  * (StructuredLogSchemaSuite holds that).
  */
private[log] object LogRepo:

  /** Every event of `events` not in the table yet (an event retried after a failed batch is not written twice). */
  def insert(events: Vector[LogEvent])(using DbCon): Unit =
    if events.nonEmpty then insertAll(events)

  private def insertAll(events: Vector[LogEvent])(using DbCon): Unit =
    val _ = batchUpdate(events) { e =>
      val subjects: List[String] = e.subjects.map(_.ref).toList
      val json = writeToString(e)
      sql"""INSERT INTO log_events (id, at, trace, label, kind, problem, subjects, event)
            VALUES (${e.id}::uuid, ${e.at}::timestamptz, ${e.trace}::uuid, ${e.label},
                    ${e.kind}, ${e.problem}, $subjects::text[], $json::jsonb)
            ON CONFLICT (id) DO NOTHING""".update
    }

  /** A staged write statement, as the trigger recorded it. */
  final case class Staged(
      id: String,
      at: String,
      trace: String,
      label: Option[String],
      table: String,
      op: String,
      rows: Option[Long],
      keys: List[String],
      citekeys: List[String]
  ) derives DbCodec

  /** At most `max` staged write statements, oldest first, taken out of `log_writes`: the caller writes their events in
    * the same transaction, so a failure leaves them staged.
    */
  def takeStaged(max: Int)(using DbCon): Vector[Staged] =
    sql"""DELETE FROM log_writes WHERE seq IN (SELECT seq FROM log_writes ORDER BY seq LIMIT $max FOR UPDATE SKIP LOCKED)
          RETURNING id::text, ${Timestamps.iso(sql"at")}, trace::text, label, tbl, op, rows, keys, citekeys"""
      .returning[Staged]
      .run()
      .toVector

  /** A page of events: `where` the filters, newest first, at most `limit`. */
  def page(where: Frag, limit: Int)(using DbCon): Vector[LogEvent] =
    sql"SELECT event::text FROM log_events WHERE TRUE $where ORDER BY at DESC, id DESC LIMIT $limit"
      .query[String]
      .run()
      .toVector
      .flatMap(decode)

  /** The trace a problem was raised in, if its event is kept. */
  def traceOfProblem(id: LogEvent.ProblemId)(using DbCon): Option[LogEvent.Trace] =
    val subject = LogEvent.Subject.Problem(LogEvent.Key(id.value)).ref
    sql"""SELECT trace::text FROM log_events WHERE subjects @> ARRAY[$subject]::text[] ORDER BY at, id LIMIT 1"""
      .query[String]
      .run()
      .headOption
      .map(LogEvent.Trace(_))

  /** Ordinary events before `ordinary`, problems before `problems`: deleted; the rows deleted. */
  def deleteOlder(ordinary: String, problems: String)(using DbCon): Long =
    sql"""DELETE FROM log_events WHERE (NOT problem AND at < $ordinary::timestamptz)
                                    OR (problem AND at < $problems::timestamptz)""".update.run().toLong

  /** The table's size on disk (its indexes included), its rows, and the dead rows whose space is not reused yet. */
  def size(using DbCon): (Long, Long, Long) =
    sql"""SELECT pg_total_relation_size('log_events'), (SELECT count(*) FROM log_events),
                 coalesce((SELECT n_dead_tup FROM pg_stat_user_tables WHERE relname = 'log_events'), 0)"""
      .query[(Long, Long, Long)]
      .run()
      .headOption
      .getOrElse((0L, 0L, 0L))

  /** The oldest `n` events deleted (the size cap); the rows deleted. */
  def deleteOldest(n: Int)(using DbCon): Long =
    sql"""DELETE FROM log_events WHERE id IN (SELECT id FROM log_events ORDER BY at, id LIMIT $n)""".update.run().toLong

  def decode(json: String): Option[LogEvent] = Try(readFromString[LogEvent](json)).toOption
