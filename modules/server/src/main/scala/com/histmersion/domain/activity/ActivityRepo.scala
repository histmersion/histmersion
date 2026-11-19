package com.histmersion
package domain.activity

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.{Entry, Show}

import com.augustnagro.magnum.*

import java.time.Instant
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.{joined, Timestamps}

/** The activity list in SQL: the jobs (agent runs among them), the worklog and a person's decisions as one relation,
  * filtered and cut into pages by the rules of [[ActivityRules]]. Takes the connection it runs on.
  */
private[activity] object ActivityRepo:

  /** Every entry, with its time as a timestamp (`t`) to filter and order by, and its key in byte order. */
  private val Merged: Frag =
    sql"""(SELECT CASE WHEN j.kind = 'agent' THEN 'run' ELSE 'job' END AS kind, j.id AS rid, j.created_at AS t,
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
           FROM (SELECT * FROM approvals WHERE decided_at IS NOT NULL AND ${ApprovalQuery.decidedByExpr} = 'person') a
           LEFT JOIN jobs rj ON rj.id = a.job_id) m"""

  private val Projection: Frag =
    sql"""SELECT kind || ':' || rid AS key, kind, ${Timestamps.iso(sql"t")}, state, what, text, error, ${Timestamps.iso(
        sql"ended"
      )}, goal, job, writer,
              citekey, article, queue_item, queue_list, approval, target, target_kind"""

  private def showSql(show: Show): Frag = show match
    case Show.Everything => sql"true"
    case Show.Running    => sql"kind IN ('run', 'job') AND state IN (${listed(ActivityRules.RunningStates)})"
    case Show.Agents     => sql"(kind = 'run' OR (kind = 'log' AND writer = 'agent'))"
    case Show.Jobs       => sql"(kind = 'job' OR (kind = 'log' AND writer = 'job'))"
    case Show.Yours      => sql"kind = 'decision'"
    case Show.Problems   => sql"kind IN ('run', 'job') AND state IN (${listed(ActivityRules.ProblemStates)})"

  /** The rules' own states, each bound through its codec, in their sorted order: `?, ?, …`. */
  private def listed(states: Set[Entry.State]): Frag =
    states.toList.sortBy(_.value).map(s => sql"$s").joined(sql", ")

  private def where(q: ActivityQuery): Frag =
    sql" WHERE ${showSql(q.show)}${q.goal.fold(sql"")(g =>
        sql" AND goal = $g"
      )}${q.since.fold(sql"")(s => sql" AND t >= ${s.toString}::timestamptz")}${q.before.fold(sql"")(c =>
        sql""" AND (t < ${c.at.toString}::timestamptz OR (t = ${c.at.toString}::timestamptz AND (kind || ':' || rid) COLLATE "C" < ${c.key}))"""
      )}"

  /** One page of `q`, with the next page's cursor. */
  def page(q: ActivityQuery)(using DbCon): (List[Entry], Option[ActivityCursor.Wire]) =
    val sql = sql"""$Projection FROM $Merged${where(
        q
      )} ORDER BY t DESC, (kind || ':' || rid) COLLATE "C" DESC LIMIT ${q.limit.value + 1}"""
    val rows = sql.query[ActivityRow].run().toList.map(_.entry)
    val (kept, rest) = rows.splitAt(q.limit.value)
    (kept, Option.when(rest.nonEmpty)(kept.lastOption).flatten.map(ActivityRules.cursorOf(_).wire))

  /** The runs and jobs running now, and those started since `weekAgo` that failed. */
  def counts(weekAgo: Instant)(using DbCon): Activity.Counts =
    val sql = sql"""SELECT count(*) FILTER (WHERE status IN (${listed(ActivityRules.RunningStates)})),
                                count(*) FILTER (WHERE status IN (${listed(
        ActivityRules.ProblemStates
      )}) AND created_at >= ${weekAgo.toString}::timestamptz)
                         FROM jobs"""
    sql
      .query[(Long, Long)]
      .run()
      .headOption
      .fold(Activity.Counts())((r, p) =>
        Activity.Counts(Activity.Counts.Running(r.toInt), Activity.Counts.ProblemsWeek(p.toInt))
      )
