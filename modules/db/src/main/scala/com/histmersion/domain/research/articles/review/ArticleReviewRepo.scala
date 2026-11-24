package com.histmersion
package domain.research.articles.review

import com.histmersion.api.research.{ReviewApproval, ReviewGoal}
import com.histmersion.domain.ArticleReviewStatus.{Facts, ItemFacts, LedgerFacts}
import com.histmersion.domain.automation.ItemStatus

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.ManagerPolicy
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** What the review manager and the review ledgers record about articles, read for their review state: per article the
  * newest `article-review` item, the ledger's findings COUNTED in SQL (a ledger runs to hundreds of KB; a list page
  * never decodes one), the pass a running review is in, and the approvals about it that wait for a person.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the article review tables and their row codecs")
private[review] object ArticleReviewRepo:

  final private case class FactsRow(
      slug: FactsRow.Slug,
      itemStatus: Option[ItemStatus.Wire],
      outcome: Option[ItemFacts.Outcome],
      attempts: Option[ItemFacts.Attempts],
      updatedAt: Option[FactsRow.UpdatedAt],
      findings: Option[LedgerFacts.Findings],
      openMajors: Option[LedgerFacts.OpenMajors],
      openMinors: Option[LedgerFacts.OpenMinors],
      runs: Option[LedgerFacts.Runs],
      round: Option[Facts.Round]
  ) derives DbCodec

  object FactsRow:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Slug = domain.research.Slug
    val Slug: domain.research.Slug.type = domain.research.Slug
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]

  final private case class ApprovalRow(subject: ApprovalRow.Subject, id: ApprovalRow.Id, kind: ApprovalRow.Kind)
      derives DbCodec

  object ApprovalRow:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Subject = domain.research.Slug
    val Subject: domain.research.Slug.type = domain.research.Slug
    export ManagerPolicy.ApprovalId as Id
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]

  final private case class GoalRow(slug: GoalRow.Slug, title: GoalRow.Title, status: GoalRow.Status) derives DbCodec

  object GoalRow:
    export Goal.Slug
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Title = domain.Title
    val Title: domain.Title.type = domain.Title
    export Goal.Status.Wire as Status

  /** The approval kinds that leave an article waiting for a person. */
  val WaitingKinds: List[ApprovalRow.Kind] =
    List(ApprovalRow.Kind("article-filter"), ApprovalRow.Kind("article-publish"))

  private val ts = sql"""'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** The review facts of each of `slugs`, and the time of its item's last change (UTC). A slug the manager and the
    * ledgers know nothing of maps to empty facts.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def facts(
      slugs: List[domain.research.Slug]
  )(using DbCon): Map[domain.research.Slug, (Facts, Option[FactsRow.UpdatedAt])] =
    if slugs.isEmpty then Map.empty
    else
      val rows = sql"""
          SELECT a.slug, mi.status, mi.outcome, mi.attempts,
                 to_char(mi.updated_at AT TIME ZONE 'UTC', $ts),
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
        val item = r.itemStatus
          .flatMap(ItemStatus.parse)
          .map(s => ItemFacts(s, r.outcome, r.attempts.getOrElse(ItemFacts.Attempts(0))))
        val ledger = r.findings
          .filter(_.value > 0)
          .map(n =>
            LedgerFacts(
              r.openMajors.getOrElse(LedgerFacts.OpenMajors(0)),
              r.openMinors.getOrElse(LedgerFacts.OpenMinors(0)),
              n,
              r.runs.getOrElse(LedgerFacts.Runs(0))
            )
          )
        r.slug -> (Facts(item, ledger, r.round, waiting.getOrElse(r.slug, Nil)), r.updatedAt)
      }.toMap

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def approvals(
      slugs: List[domain.research.Slug]
  )(using DbCon): Map[domain.research.Slug, List[ReviewApproval]] =
    sql"""SELECT subject, id, kind FROM approvals
          WHERE state = 'open' AND kind = ANY($WaitingKinds::text[]) AND subject = ANY(${slugs.distinct}::text[])
          ORDER BY id"""
      .query[ApprovalRow]
      .run()
      .toList
      .groupMap(_.subject)(r => ReviewApproval(ReviewApproval.Id(r.id.value), ReviewApproval.Kind(r.kind.value)))

  /** When each of `runs` started (UTC), for the runs still recorded. */
  def runStarts(runs: List[Run.Id])(using DbCon): Map[Run.Id, String] =
    if runs.isEmpty then Map.empty
    else
      sql"""SELECT id, to_char(created_at AT TIME ZONE 'UTC', $ts) FROM jobs
              WHERE id = ANY(${runs.distinct}::text[])"""
        .query[(String, String)]
        .run()
        .map((id, at) => Run.Id.assume(id) -> at)
        .toMap

  /** The research goals that link the article (`goal_links` of kind `article`), by slug. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def goals(slug: domain.research.Slug)(using DbCon): List[ReviewGoal] =
    sql"""SELECT g.slug, g.title, g.status FROM goal_links l JOIN goals g ON g.slug = l.goal_slug
            WHERE l.kind = 'article' AND l.ref = $slug ORDER BY g.slug"""
      .query[GoalRow]
      .run()
      .toList
      .map(r =>
        ReviewGoal(ReviewGoal.Slug(r.slug.value), ReviewGoal.Title(r.title.value), ReviewGoal.Status(r.status.value))
      )
