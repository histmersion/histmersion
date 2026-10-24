package com.histmersion.db

import com.histmersion.api.UiNotices
import com.histmersion.api.UiNotices.{Board, Counters}

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The web UI's toolbar notices (migration V083, core [[UiNotices]]): each watched list's count and
  * newest change, read in ONE statement, and the per-window "seen" marks. Stateless.
  *
  * A board's `latest` is the newest `updated_at` (or, for an approval, its proposal or decision) of
  * the rows that still exist; `note` blocks of a queue are export scaffolding and never count. */
object UiRepo:

  /** UTC, microseconds, one fixed format — so two instants compare as strings ([[UiNotices.fresh]]). */
  private def iso(col: String): SqlLiteral =
    SqlLiteral(s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')""")

  private val Latest = iso("b.latest")
  private val Seen   = iso("s.seen_at")
  private val Marked = iso("seen_at")

  /** Every board's counter, in [[Board]]'s order. A board with no rows yet counts 0 and has no
    * `latest`; one never opened has no `seen`. */
  def counters(ds: DataSource): Counters =
    val rows = connect(ds):
      sql"""WITH b(board, n, latest) AS (
              SELECT list, (count(*) FILTER (WHERE status = 'open'))::int, max(updated_at) FILTER (WHERE status <> 'note')
                FROM queue_items GROUP BY list
              UNION ALL
              SELECT 'approvals', (count(*) FILTER (WHERE state = 'open'))::int, max(greatest(created_at, decided_at))
                FROM approvals
              UNION ALL
              SELECT 'goals', count(*)::int, max(updated_at) FROM goals)
            SELECT coalesce(b.board, s.board), b.n, $Latest, $Seen
            FROM b FULL JOIN ui_seen s ON s.board = b.board"""
        .query[(String, Option[Int], Option[String], Option[String])].run()
    val byBoard = rows.map((k, n, l, s) => k -> (n, l, s)).toMap
    Counters(Board.values.toList.map { b =>
      val (n, latest, seen) = byBoard.getOrElse(b.wire, (None, None, None))
      UiNotices.counter(b, Some(n.getOrElse(0)), latest, seen)
    })

  /** The reader saw `board` up to `at` (an ISO instant; `None` = now). The mark only moves forward.
    * Returns the stored mark. */
  def markSeen(ds: DataSource, board: Board, at: Option[String]): String =
    transact(ds):
      sql"""INSERT INTO ui_seen (board, seen_at) VALUES (${board.wire}, coalesce($at::timestamptz, now()))
            ON CONFLICT (board) DO UPDATE SET seen_at = greatest(ui_seen.seen_at, EXCLUDED.seen_at)
            RETURNING $Marked""".query[String].run().head
