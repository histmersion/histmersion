package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.domain.AcquireRecheck

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.Citekey

/** The acquisition list's re-check (migration V104): one row per acquisition item that was checked. Every method runs
  * on the connection it is given.
  */
private[acquisition] object AcquireCheckRepo:
  type CheckedAtMs = CheckedAtMs.T
  object CheckedAtMs extends Newtype[Long, Pure]

  // Times cross as epoch milliseconds (no timestamp codec in the native image).

  private val Select = SqlLiteral(
    "SELECT item_id, (extract(epoch FROM checked_at) * 1000)::bigint, generation, outcome, reason, checks FROM acquire_checks"
  )

  private def row(
      t: (
          AcquireCheckRow.Item,
          CheckedAtMs,
          AcquireRecheck.LastCheck.Generation,
          AcquireRecheck.Outcome.Wire,
          Problem,
          AcquireCheckRow.Checks
      )
  ): AcquireCheckRow =
    AcquireCheckRow(t._1, Instant.ofEpochMilli(t._2.value), t._3, t._4, t._5, t._6)

  /** The checks of these items (any that has none is absent). */
  def of(items: List[AcquireCheckRow.Item])(using DbCon): Map[AcquireCheckRow.Item, AcquireCheckRow] =
    if items.isEmpty then Map.empty
    else
      val ids = items.distinct.map(_.toString)
      sql"$Select WHERE item_id::text = ANY($ids::text[])"
        .query[
          (
              AcquireCheckRow.Item,
              CheckedAtMs,
              AcquireRecheck.LastCheck.Generation,
              AcquireRecheck.Outcome.Wire,
              Problem,
              AcquireCheckRow.Checks
          )
        ]
        .run()
        .map(row)
        .map(r => r.item -> r)
        .toMap

  /** The checks of every OPEN acquisition item. */
  def open()(using DbCon): Map[AcquireCheckRow.Item, AcquireCheckRow] =
    sql"""$Select WHERE item_id IN (SELECT id FROM queue_items WHERE list = 'acquire' AND status = 'open')"""
      .query[
        (
            AcquireCheckRow.Item,
            CheckedAtMs,
            AcquireRecheck.LastCheck.Generation,
            AcquireRecheck.Outcome.Wire,
            Problem,
            AcquireCheckRow.Checks
        )
      ]
      .run()
      .map(row)
      .map(r => r.item -> r)
      .toMap

  /** Record one check of `item` (its previous one replaced, the count kept). */
  def record(
      item: AcquireCheckRow.Item,
      at: Instant,
      generation: AcquireRecheck.LastCheck.Generation,
      outcome: AcquireRecheck.Outcome,
      reason: Problem
  )(using
      DbCon
  ): Unit =
    val ms = at.toEpochMilli.toDouble / 1000
    val o = outcome.wire
    val _ =
      sql"""INSERT INTO acquire_checks (item_id, checked_at, generation, outcome, reason)
            VALUES ($item, to_timestamp($ms::float8), $generation, $o, ${reason.sentence})
            ON CONFLICT (item_id) DO UPDATE SET checked_at = EXCLUDED.checked_at, generation = EXCLUDED.generation,
              outcome = EXCLUDED.outcome, reason = EXCLUDED.reason, checks = acquire_checks.checks + 1""".update.run()

  /** How many checks since `since`, in all and per outcome. */
  def countSince(since: Instant)(using DbCon): Map[AcquireRecheck.Outcome.Wire, RowCount] =
    val ms = since.toEpochMilli.toDouble / 1000
    sql"""SELECT outcome, count(*)::int FROM acquire_checks WHERE checked_at >= to_timestamp($ms::float8)
          GROUP BY outcome""".query[(AcquireRecheck.Outcome.Wire, RowCount)].run().toMap

  /** The sources holding these DOIs (lower-cased): the citekey, and whether it has its original (a converted body or a
    * stored file). One per DOI — the first citekey.
    */
  def heldByDoi(dois: List[Doi])(using DbCon): Map[Doi, AcquireRecheck.Held] =
    val ds0 = dois.map(_.value.trim.toLowerCase).filter(_.nonEmpty).distinct
    if ds0.isEmpty then Map.empty
    else
      sql"""SELECT DISTINCT ON (lower(doi)) lower(doi), citekey,
                             (coalesce(ocr_markdown, '') <> '' OR sha256 IS NOT NULL)
                      FROM sources WHERE lower(doi) = ANY($ds0::text[]) ORDER BY lower(doi), citekey"""
        .query[(Doi, Citekey, Boolean)]
        .run()
        .map((d, ck, has) => d -> AcquireRecheck.Held(ck, AcquireRecheck.Held.HasOriginal(has)))
        .toMap
