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
import com.histmersion.lint.{tolerated, LintRule}

private[log] object LogRepo:

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

  final case class Counted(at: LogEvent.At, table: LogEvent.Table, op: String, count: LogEvent.Count) derives DbCodec

  def takeCounted(using DbCon): Vector[Counted] =
    sql"""DELETE FROM log_counts WHERE minute < date_trunc('minute', now())
          RETURNING ${Timestamps.iso(sql"minute")}, tbl, op, n"""
      .returning[Counted]
      .run()
      .toVector

  def takeStaged(max: Int)(using DbCon): Vector[Staged] =
    sql"""DELETE FROM log_writes WHERE seq IN (SELECT seq FROM log_writes ORDER BY seq LIMIT $max FOR UPDATE SKIP LOCKED)
          RETURNING id::text, ${Timestamps.iso(sql"at")}, trace::text, label, tbl, op, rows, keys, citekeys"""
      .returning[Staged]
      .run()
      .toVector

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def page(where: Frag, limit: LogPage.Limit, oldestFirst: Boolean = false)(using DbCon): Vector[LogEvent] =
    val query =
      if oldestFirst then sql"SELECT event::text FROM log_events WHERE TRUE $where ORDER BY at, id LIMIT $limit"
      else sql"SELECT event::text FROM log_events WHERE TRUE $where ORDER BY at DESC, id DESC LIMIT $limit"
    query
      .query[String]
      .run()
      .toVector
      .flatMap(decode)

  def deleteOlder(ordinary: Instant, problems: Instant)(using DbCon): Long =
    val (o, p) = (LogEvent.At.of(ordinary), LogEvent.At.of(problems))
    sql"""DELETE FROM log_events WHERE (NOT problem AND at < $o::timestamptz)
                                    OR (problem AND at < $p::timestamptz)""".update.run().toLong

  def size(using DbCon): (Long, Long, Long) =
    sql"""SELECT pg_total_relation_size('log_events'), (SELECT count(*) FROM log_events),
                 coalesce((SELECT n_dead_tup FROM pg_stat_user_tables WHERE relname = 'log_events'), 0)"""
      .query[(Long, Long, Long)]
      .run()
      .headOption
      .getOrElse((0L, 0L, 0L))

  def deleteOldest(n: Int)(using DbCon): Long =
    sql"""DELETE FROM log_events WHERE id IN (SELECT id FROM log_events ORDER BY at, id LIMIT $n)""".update.run().toLong

  def decode(json: String): Option[LogEvent] = Try(readFromString[LogEvent](json)).toOption
