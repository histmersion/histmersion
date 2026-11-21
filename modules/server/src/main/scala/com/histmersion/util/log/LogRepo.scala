package com.histmersion
package util.log

import com.augustnagro.magnum.*
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Timestamps

import java.time.Instant
import scala.util.Try

/** The structured log's two tables: `log_events` (every event, as its JSON and the columns queries filter on) and
  * `log_writes` (the write statements the triggers recorded, waiting for the writer). The only code that writes either
  * (StructuredLogSchemaSuite holds that).
  */
private[log] object LogRepo:

  /** Every event of `events` not in the table yet (an event retried after a failed batch is not written twice): how
    * many were new, when the driver says.
    */
  def insert(events: Vector[LogEvent])(using DbCon): Option[Long] =
    if events.isEmpty then Some(0L)
    else
      batchUpdate(events) { e =>
        val subjects: List[String] = e.subjects.map(_.ref).toList
        val json = writeToString(e)
        sql"""INSERT INTO log_events (id, at, trace, label, kind, problem, subjects, event)
              VALUES (${e.id}::uuid, ${e.at}::timestamptz, ${e.trace}::uuid, ${e.label},
                      ${e.kind}, ${e.problem}, $subjects::text[], $json::jsonb)
              ON CONFLICT (id) DO NOTHING""".update
      } match
        case BatchUpdateResult.Success(rows) => Some(rows)
        case BatchUpdateResult.SuccessNoInfo => None

  /** A staged write statement, as the trigger recorded it. */
  final case class Staged(
      id: LogEvent.Id,
      at: LogEvent.At,
      trace: LogEvent.Trace,
      label: Option[LogEvent.Label],
      table: LogEvent.Table,
      op: String,
      rows: Option[LogEvent.Count],
      keys: List[LogEvent.Key],
      citekeys: List[LogEvent.Key]
  ) derives DbCodec

  /** The write statements of one table and change counted in one finished minute. */
  final case class Counted(at: LogEvent.At, table: LogEvent.Table, op: String, count: LogEvent.Count) derives DbCodec

  /** The counts of the minutes that are over, taken out of `log_counts` (in the caller's transaction, as the staged
    * writes are).
    */
  def takeCounted(using DbCon): Vector[Counted] =
    sql"""DELETE FROM log_counts WHERE minute < date_trunc('minute', now())
          RETURNING ${Timestamps.iso(sql"minute")}, tbl, op, n"""
      .returning[Counted]
      .run()
      .toVector

  /** At most `max` staged write statements, oldest first, taken out of `log_writes`: the caller writes their events in
    * the same transaction, so a failure leaves them staged.
    */
  def takeStaged(max: Int)(using DbCon): Vector[Staged] =
    sql"""DELETE FROM log_writes WHERE seq IN (SELECT seq FROM log_writes ORDER BY seq LIMIT $max FOR UPDATE SKIP LOCKED)
          RETURNING id::text, ${Timestamps.iso(sql"at")}, trace::text, label, tbl, op, rows, keys, citekeys"""
      .returning[Staged]
      .run()
      .toVector

  /** A page of events: `where` the filters, newest first (`oldestFirst`: oldest first), at most `limit`. */
  def page(where: Frag, limit: LogPage.Limit, oldestFirst: Boolean = false)(using DbCon): Vector[LogEvent] =
    val query =
      if oldestFirst then sql"SELECT event::text FROM log_events WHERE TRUE $where ORDER BY at, id LIMIT $limit"
      else sql"SELECT event::text FROM log_events WHERE TRUE $where ORDER BY at DESC, id DESC LIMIT $limit"
    query
      .query[String]
      .run()
      .toVector
      .flatMap(decode)

  /** Ordinary events before `ordinary`, problems before `problems`: deleted; the rows deleted. */
  def deleteOlder(ordinary: Instant, problems: Instant)(using DbCon): Long =
    val (o, p) = (LogEvent.At.of(ordinary), LogEvent.At.of(problems))
    sql"""DELETE FROM log_events WHERE (NOT problem AND at < $o::timestamptz)
                                    OR (problem AND at < $p::timestamptz)""".update.run().toLong

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
