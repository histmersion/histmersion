package com.histmersion
package domain.research.workflows

import com.histmersion.domain.reviewledger.Ledger

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.json.RawJson
import com.histmersion.util.database.Jsonb
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.Slug
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The store of the review ledgers (V073): one JSON value per article, read at the start of a `review-article` run and
  * written after each of its passes.
  */
@atBoundary(BoundaryKind.Sql, "reads and writes the review ledger tables and their row codecs")
private[workflows] object ReviewLedgerRepo:
  type ReviewId = ReviewId.T
  object ReviewId extends Newtype[String, Pure]

  /** The article's ledger; None when it has none yet. A value that no longer decodes is an error (the run fails rather
    * than start over and lose its history).
    */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def get(slug: Slug)(using DbCon): Either[Problem, Option[Ledger]] =
    sql"SELECT ledger::text FROM review_ledgers WHERE slug = $slug".query[String].run().headOption match
      case None       => Right(None)
      case Some(json) =>
        RawJson
          .decode[Ledger](json)
          .left
          .map(e =>
            Problem.Io.Corrupt(
              Problem.Io.Format.Json,
              Problem.Where(s"the review ledger of '$slug'"),
              Problem.Io.Damage.Unreadable(External.Reported.of(e))
            )
          )
          .map(Some(_))

  /** Write the ledger (insert or replace). The article must exist. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def put(l: Ledger)(using DbCon)(using creator: Creator.New): Unit =
    val json = Jsonb.write(l)
    sql"""INSERT INTO review_ledgers (slug, ledger, updated_at, creator) VALUES (${l.slug}, $json::jsonb, now(), ${StoredCreator
        .column(creator)}::jsonb)
          ON CONFLICT (slug) DO UPDATE SET ledger = EXCLUDED.ledger, updated_at = now()""".update.run()

  /** The articles that passed their review: a review ledger with no open MAJOR, and no open item of review manager
    * `reviewId` (a review queued or running is not passed yet).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def passedReview(reviewId: ReviewId)(using DbCon): Set[Slug] =
    sql"""SELECT l.slug FROM review_ledgers l
          WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                            WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $reviewId AND i.target = l.slug
                            AND i.status IN ('pending', 'held', 'active'))""".query[Slug].run().toSet
