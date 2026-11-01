package com.histmersion.db

import com.histmersion.domain.ReviewLedger.Ledger

import com.augustnagro.magnum.*

import javax.sql.DataSource

/** The store of the review ledgers (V073): one JSON value per article, read at the start of a `review-article` run and
  * written after each of its passes.
  */
object ReviewLedgerRepo:

  /** The article's ledger; None when it has none yet. A value that no longer decodes is an error (the run fails rather
    * than start over and lose its history).
    */
  def get(ds: DataSource, slug: String): Either[String, Option[Ledger]] =
    connect(ds)(sql"SELECT ledger::text FROM review_ledgers WHERE slug = $slug".query[String].run().headOption) match
      case None       => Right(None)
      case Some(json) =>
        Jsonb.decode[Ledger](json).left.map(e => s"the review ledger of '$slug' does not decode: $e").map(Some(_))

  /** Write the ledger (insert or replace). The article must exist. */
  def put(ds: DataSource, l: Ledger): Unit =
    val json = Jsonb.write(l)
    transact(ds):
      sql"""INSERT INTO review_ledgers (slug, ledger, updated_at) VALUES (${l.slug}, $json::jsonb, now())
            ON CONFLICT (slug) DO UPDATE SET ledger = EXCLUDED.ledger, updated_at = now()""".update.run()

  /** The articles that passed their review: a review ledger with no open MAJOR, and no open item of review manager
    * `reviewId` (a review queued or running is not passed yet).
    */
  def passedReview(ds: DataSource, reviewId: String): Set[String] =
    connect(ds):
      sql"""SELECT l.slug FROM review_ledgers l
            WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                              WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
              AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $reviewId AND i.target = l.slug
                              AND i.status IN ('pending', 'held', 'active'))""".query[String].run().toSet
