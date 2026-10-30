package com.histmersion.db

import com.histmersion.api.{ReviewApproval, ReviewGoal}
import com.histmersion.domain.ArticleReviewStatus.{Facts, ItemFacts, LedgerFacts}
import com.histmersion.values.ItemStatus

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** What the review manager and the review ledgers record about articles, read for their review state: per article the
  * newest `article-review` item, the ledger's findings COUNTED in SQL (a ledger runs to hundreds of KB; a list page
  * never decodes one), the pass a running review is in, and the approvals about it that wait for a person.
  */
object ArticleReviewRepo:

  final private case class FactsRow(
      slug: String,
      itemStatus: Option[String],
      outcome: Option[String],
      attempts: Option[Int],
      updatedAt: Option[String],
      findings: Option[Int],
      openMajors: Option[Int],
      openMinors: Option[Int],
      runs: Option[Int],
      round: Option[Int]
  )
  private given DbCodec[FactsRow] = DbCodec.derived

  final private case class ApprovalRow(subject: String, id: Long, kind: String)
  private given DbCodec[ApprovalRow] = DbCodec.derived

  final private case class GoalRow(slug: String, title: String, status: String)
  private given DbCodec[GoalRow] = DbCodec.derived

  /** The approval kinds that leave an article waiting for a person. */
  val WaitingKinds: List[String] = List("article-filter", "article-publish")

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** The review facts of each of `slugs`, and the time of its item's last change (UTC). A slug the manager and the
    * ledgers know nothing of maps to empty facts.
    */
  def facts(ds: DataSource, slugs: List[String]): Map[String, (Facts, Option[String])] =
    if slugs.isEmpty then Map.empty
    else
      connect(ds):
        val rows = sql"""
          SELECT a.slug, mi.status, mi.outcome, mi.attempts,
                 to_char(mi.updated_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)}),
                 rl.findings, rl.open_majors, rl.open_minors, rl.runs, st.round
          FROM unnest(${slugs.distinct}::text[]) AS a(slug)
          LEFT JOIN LATERAL (
            SELECT m.status, m.outcome, m.attempts, m.run_id, m.updated_at FROM manager_items m
            WHERE m.manager_id = 'review' AND m.kind = 'article-review' AND m.target = a.slug
            ORDER BY m.updated_at DESC, m.id DESC LIMIT 1) mi ON true
          LEFT JOIN LATERAL (
            SELECT (count(*) FILTER (WHERE u.ord = 1))::int AS findings,
                   (count(*) FILTER (WHERE u.ord = 1 AND e->>'status' = 'open' AND e->'finding'->>'severity' = 'major'))::int AS open_majors,
                   (count(*) FILTER (WHERE u.ord = 1 AND e->>'status' = 'open'
                                       AND e->'finding'->>'severity' IS DISTINCT FROM 'major'))::int AS open_minors,
                   (count(DISTINCT u.run) FILTER (WHERE u.run <> ''))::int AS runs
            FROM review_ledgers r
            CROSS JOIN LATERAL jsonb_array_elements(r.ledger->'entries') e
            CROSS JOIN LATERAL unnest(ARRAY[e->>'run', e->'closed'->>'run']) WITH ORDINALITY AS u(run, ord)
            WHERE r.slug = a.slug) rl ON true
          LEFT JOIN LATERAL (
            SELECT max(split_part(s.name, '-', 2)::int) AS round FROM agent_steps s
            WHERE mi.status = 'active' AND s.job_id = mi.run_id AND s.name ~ '^snapshot-[0-9]+') st ON true"""
          .query[FactsRow]
          .run()
        val waiting = approvals(slugs)
        rows.map { r =>
          val item = r.itemStatus.flatMap(ItemStatus.parse).map(s => ItemFacts(s, r.outcome, r.attempts.getOrElse(0)))
          val ledger = r.findings
            .filter(_ > 0)
            .map(n => LedgerFacts(r.openMajors.getOrElse(0), r.openMinors.getOrElse(0), n, r.runs.getOrElse(0)))
          r.slug -> (Facts(item, ledger, r.round, waiting.getOrElse(r.slug, Nil)), r.updatedAt)
        }.toMap

  private def approvals(slugs: List[String])(using DbCon): Map[String, List[ReviewApproval]] =
    sql"""SELECT subject, id, kind FROM approvals
          WHERE state = 'open' AND kind = ANY($WaitingKinds::text[]) AND subject = ANY(${slugs.distinct}::text[])
          ORDER BY id""".query[ApprovalRow].run().toList.groupMap(_.subject)(r => ReviewApproval(r.id, r.kind))

  /** When each of `runs` started (UTC), for the runs still recorded. */
  def runStarts(ds: DataSource, runs: List[String]): Map[String, String] =
    if runs.isEmpty then Map.empty
    else
      connect(ds):
        sql"""SELECT id, to_char(created_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)}) FROM jobs
              WHERE id = ANY(${runs.distinct}::text[])""".query[(String, String)].run().toMap

  /** The research goals that link the article (`goal_links` of kind `article`), by slug. */
  def goals(ds: DataSource, slug: String): List[ReviewGoal] =
    connect(ds):
      sql"""SELECT g.slug, g.title, g.status FROM goal_links l JOIN goals g ON g.slug = l.goal_slug
            WHERE l.kind = 'article' AND l.ref = $slug ORDER BY g.slug"""
        .query[GoalRow]
        .run()
        .toList
        .map(r => ReviewGoal(r.slug, r.title, r.status))
