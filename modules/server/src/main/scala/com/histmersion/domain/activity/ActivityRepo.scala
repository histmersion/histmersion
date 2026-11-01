package com.histmersion.domain.activity

import com.histmersion.api.Activity
import com.histmersion.api.Activity.{Entry, Show}
import com.histmersion.db.{Param, Sql}

import com.augustnagro.magnum.*

import java.time.Instant

/** One row of the merged list, in the order of [[ActivityRepo.Merged]]'s columns. */
final private[activity] case class ActivityRow(
    key: String,
    kind: String,
    at: String,
    state: String,
    what: String,
    text: String,
    error: Option[String],
    endedAt: Option[String],
    goal: Option[String],
    job: Option[String],
    by: Option[String],
    citekey: Option[String],
    article: Option[String],
    queueItem: Option[Int],
    queueList: Option[String],
    approval: Option[Long],
    target: Option[String],
    targetKind: Option[String]
) derives DbCodec:
  def entry: Entry = Entry(
    key,
    kind,
    at,
    state,
    what,
    text,
    error,
    endedAt,
    goal,
    job,
    by,
    citekey,
    article,
    queueItem,
    queueList,
    approval,
    target,
    targetKind
  )

/** The activity list in SQL: the jobs (agent runs among them), the worklog and a person's decisions as one relation,
  * filtered and cut into pages by the rules of [[ActivityRules]]. Takes the connection it runs on.
  */
private[activity] object ActivityRepo:

  private val Iso = """'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'"""
  private def iso(col: String) = s"to_char($col AT TIME ZONE 'UTC', $Iso)"

  /** Every entry, with its time as a timestamp (`t`) to filter and order by, and its key in byte order. */
  private val Merged: String =
    s"""(SELECT CASE WHEN j.kind = 'agent' THEN 'run' ELSE 'job' END AS kind, j.id AS rid, j.created_at AS t,
                j.status AS state, coalesce(j.pipeline, j.kind) AS what, coalesce(j.progress, '') AS text, j.error,
                CASE WHEN j.status IN ('running', 'queued', 'waiting', 'paused') THEN NULL ELSE j.updated_at END AS ended,
                coalesce(j.goal_slug, CASE WHEN j.target_kind = 'goal' THEN j.target_id END) AS goal, j.id AS job,
                NULL::text AS writer,
                coalesce(j.citekey, CASE WHEN j.target_kind = 'source' THEN j.target_id END) AS citekey,
                CASE WHEN j.target_kind = 'article' THEN j.target_id END AS article,
                j.acquire_item AS queue_item, CASE WHEN j.acquire_item IS NOT NULL THEN 'acquire' END AS queue_list,
                NULL::bigint AS approval,
                coalesce(j.target_id, j.detail->>'about', j.detail->>'media', j.detail->>'events') AS target,
                j.target_kind AS target_kind
           FROM jobs j
         UNION ALL
         SELECT 'log', q.id::text, q.created_at, '', coalesce(q.topic, ''), q.text, NULL, NULL::timestamptz, q.goal_slug,
                q.created_job,
                CASE WHEN q.created_job IS NULL THEN NULL WHEN cj.kind = 'agent' THEN 'agent' ELSE 'job' END,
                NULL, NULL, q.id, 'log', NULL::bigint, NULL, NULL
           FROM queue_items q LEFT JOIN jobs cj ON cj.id = q.created_job
          WHERE q.list = 'log' AND q.status <> 'note'
         UNION ALL
         SELECT 'decision', a.id::text, a.decided_at, a.state, a.kind, coalesce(a.subject, a.citekey, ''), NULL,
                NULL::timestamptz, coalesce(rj.goal_slug, CASE WHEN a.kind = 'goal-plan' THEN a.subject END), a.job_id,
                NULL, a.citekey, CASE WHEN a.kind IN ('article-publish', 'article-filter') THEN a.subject END,
                NULL, NULL, a.id, a.target, NULL
           FROM (SELECT * FROM approvals WHERE decided_at IS NOT NULL AND ${com.histmersion.domain.automation.approvals.ApprovalQuery.decidedByExpr} = 'person') a
           LEFT JOIN jobs rj ON rj.id = a.job_id) m"""

  private val Projection: String =
    s"""SELECT kind || ':' || rid AS key, kind, ${iso("t")}, state, what, text, error, ${iso(
        "ended"
      )}, goal, job, writer,
              citekey, article, queue_item, queue_list, approval, target, target_kind"""

  private def showSql(show: Show): String = show match
    case Show.Everything => "true"
    case Show.Running    => s"kind IN ('run', 'job') AND state IN (${quoted(ActivityRules.RunningStates)})"
    case Show.Agents     => "(kind = 'run' OR (kind = 'log' AND writer = 'agent'))"
    case Show.Jobs       => "(kind = 'job' OR (kind = 'log' AND writer = 'job'))"
    case Show.Yours      => "kind = 'decision'"
    case Show.Problems   => s"kind IN ('run', 'job') AND state IN (${quoted(ActivityRules.ProblemStates)})"

  // the rules' own constants, never a client's text
  private def quoted(states: Set[String]): String = states.toList.sorted.map(s => s"'$s'").mkString(", ")

  private def where(q: ActivityQuery): Sql =
    Sql.raw(s" WHERE ${showSql(q.show)}") ++
      q.goal.fold(Sql.empty)(g => Sql.of(" AND goal = ?", Param.S(g))) ++
      q.since.fold(Sql.empty)(s => Sql.of(" AND t >= ?::timestamptz", Param.S(s.toString))) ++
      q.before.fold(Sql.empty)(c =>
        Sql.of(
          """ AND (t < ?::timestamptz OR (t = ?::timestamptz AND (kind || ':' || rid) COLLATE "C" < ?))""",
          Param.S(c.at.toString),
          Param.S(c.at.toString),
          Param.S(c.key)
        )
      )

  /** One page of `q`, with the next page's cursor. */
  def page(q: ActivityQuery)(using DbCon): (List[Entry], Option[String]) =
    val sql = Sql.raw(s"$Projection FROM $Merged") ++ where(q) ++
      Sql.of(""" ORDER BY t DESC, (kind || ':' || rid) COLLATE "C" DESC LIMIT ?""", Param.I(q.limit + 1))
    val rows = sql.frag.query[ActivityRow].run().toList.map(_.entry)
    val (kept, rest) = rows.splitAt(q.limit)
    (kept, Option.when(rest.nonEmpty)(kept.lastOption).flatten.map(ActivityRules.cursorOf(_).wire))

  /** The runs and jobs running now, and those started since `weekAgo` that failed. */
  def counts(weekAgo: Instant)(using DbCon): Activity.Counts =
    val sql = Sql.of(
      s"""SELECT count(*) FILTER (WHERE status IN (${quoted(ActivityRules.RunningStates)})),
                                count(*) FILTER (WHERE status IN (${quoted(
          ActivityRules.ProblemStates
        )}) AND created_at >= ?::timestamptz)
                         FROM jobs""",
      Param.S(weekAgo.toString)
    )
    sql.frag.query[(Long, Long)].run().headOption.fold(Activity.Counts())((r, p) => Activity.Counts(r.toInt, p.toInt))
