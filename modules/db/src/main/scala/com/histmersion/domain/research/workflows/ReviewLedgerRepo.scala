package com.histmersion.domain.research.workflows

import com.histmersion.util.database.Jsonb
import com.histmersion.domain.ReviewLedger.Ledger

import com.augustnagro.magnum.*

/** The store of the review ledgers (V073): one JSON value per article, read at the start of a `review-article` run and
  * written after each of its passes.
  */
private[workflows] object ReviewLedgerRepo:
  type ReviewId = ReviewId.T
  object ReviewId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The article's ledger; None when it has none yet. A value that no longer decodes is an error (the run fails rather
    * than start over and lose its history).
    */

  def get(slug: com.histmersion.domain.research.Slug)(using DbCon): Either[String, Option[Ledger]] =
    sql"SELECT ledger::text FROM review_ledgers WHERE slug = $slug".query[String].run().headOption match
      case None       => Right(None)
      case Some(json) =>
        Jsonb.decode[Ledger](json).left.map(e => s"the review ledger of '$slug' does not decode: $e").map(Some(_))

  /** Write the ledger (insert or replace). The article must exist. */
  def put(l: Ledger)(using DbCon): Unit =
    val json = Jsonb.write(l)
    sql"""INSERT INTO review_ledgers (slug, ledger, updated_at) VALUES (${l.slug}, $json::jsonb, now())
          ON CONFLICT (slug) DO UPDATE SET ledger = EXCLUDED.ledger, updated_at = now()""".update.run()

  /** The articles that passed their review: a review ledger with no open MAJOR, and no open item of review manager
    * `reviewId` (a review queued or running is not passed yet).
    */
  def passedReview(reviewId: com.histmersion.domain.research.workflows.ReviewLedgerRepo.ReviewId)(using DbCon): Set[com.histmersion.domain.research.Slug] =
    sql"""SELECT l.slug FROM review_ledgers l
          WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                            WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $reviewId AND i.target = l.slug
                            AND i.status IN ('pending', 'held', 'active'))""".query[String].run().toSet
