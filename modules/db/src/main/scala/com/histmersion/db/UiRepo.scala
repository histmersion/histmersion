package com.histmersion.db

import com.histmersion.api.{Agents, UiNotices}
import com.histmersion.values.RunState
import com.histmersion.api.UiNotices.{Board, Counters}

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The web UI's toolbar notices (migration V083, core [[UiNotices]]): each watched list's count and newest change, read
  * in ONE statement; the per-window "seen" marks are the person's (`domain.personal.seen`). Stateless.
  *
  * A board's `latest` is the newest `updated_at` (or, for an approval, its proposal or decision) of the rows that still
  * exist; `note` blocks of a queue are export scaffolding and never count.
  */
object UiRepo:

  /** UTC, microseconds, one fixed format — so two instants compare as strings ([[UiNotices.fresh]]). */
  private def iso(col: String): SqlLiteral =
    SqlLiteral(s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')""")

  private val Latest = iso("b.latest")

  /** Every board's counter, in [[Board]]'s order, with the reader's `seen` marks (board → ISO instant). A board with no
    * rows yet counts 0 and has no `latest`; one never opened has no `seen`.
    */
  def counters(ds: DataSource, seen: Map[String, String]): Counters =
    val rows = connect(ds):
      sql"""WITH b(board, n, latest) AS (
              SELECT list, (count(*) FILTER (WHERE status = 'open'))::int, max(updated_at) FILTER (WHERE status <> 'note')
                FROM queue_items GROUP BY list
              UNION ALL
              SELECT 'approvals', (count(*) FILTER (WHERE state = 'open'))::int, max(greatest(created_at, decided_at))
                FROM approvals
              UNION ALL
              SELECT 'goals', count(*)::int, max(updated_at) FROM goals)
            SELECT b.board, b.n, $Latest FROM b"""
        .query[(String, Option[Int], Option[String])]
        .run()
    val byBoard = rows.map((k, n, l) => k -> (n, l)).toMap
    Counters(Board.values.toList.map { b =>
      val (n, latest) = byBoard.getOrElse(b.wire, (None, None))
      UiNotices.counter(b, Some(n.getOrElse(0)), latest, seen.get(b.wire))
    })

  /** The research agents at a glance: the agent runs running and queued, and the autopilot's switch and drain (off and
    * not draining while it was never switched).
    */
  def agents(ds: DataSource): UiNotices.AgentsGlance = connect(ds)(agentsGlance)

  private[db] def agentsGlance(using DbCon): UiNotices.AgentsGlance =
    val (running, queued) =
      sql"""SELECT (count(*) FILTER (WHERE status = ${RunState.Running.wire}))::int,
                   (count(*) FILTER (WHERE status = ${RunState.Queued.wire}))::int
            FROM jobs WHERE kind = ${Agents.JobKind}""".query[(Int, Int)].run().head
    val (on, draining) =
      sql"SELECT enabled, drain_since IS NOT NULL FROM agent_autopilot WHERE id"
        .query[(Boolean, Boolean)]
        .run()
        .headOption
        .getOrElse((false, false))
    UiNotices.AgentsGlance(running, queued, on, draining)
