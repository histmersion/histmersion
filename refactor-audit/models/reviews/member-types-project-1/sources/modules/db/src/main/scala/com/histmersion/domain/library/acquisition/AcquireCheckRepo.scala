package com.histmersion.domain.library.acquisition

import com.histmersion.domain.AcquireRecheck

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import java.time.Instant

/** The acquisition list's re-check (migration V104): one row per acquisition item that was checked. Every method runs
  * on the connection it is given.
  */
private[acquisition] object AcquireCheckRepo:

  // Times cross as epoch milliseconds (no timestamp codec in the native image).

  type RowTPart1 = RowTPart1.T
  object RowTPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowTPart2 = RowTPart2.T
  object RowTPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowTPart3 = RowTPart3.T
  object RowTPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowTPart4 = RowTPart4.T
  object RowTPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowTPart5 = RowTPart5.T
  object RowTPart5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowTPart6 = RowTPart6.T
  object RowTPart6 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfItems = OfItems.T
  object OfItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenResultPart1 = OpenResultPart1.T
  object OpenResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordItem = RecordItem.T
  object RecordItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordGeneration = RecordGeneration.T
  object RecordGeneration extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordReason = RecordReason.T
  object RecordReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSinceResultPart1 = CountSinceResultPart1.T
  object CountSinceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSinceResultPart2 = CountSinceResultPart2.T
  object CountSinceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldByDoiResultPart1 = HeldByDoiResultPart1.T
  object HeldByDoiResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldByDoiDois = HeldByDoiDois.T
  object HeldByDoiDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Select = SqlLiteral(
    "SELECT item_id, (extract(epoch FROM checked_at) * 1000)::bigint, generation, outcome, reason, checks FROM acquire_checks"
  )

  private def row(t: (com.histmersion.domain.library.acquisition.AcquireCheckRepo.RowTPart1, com.histmersion.domain.library.acquisition.AcquireCheckRepo.RowTPart2, com.histmersion.domain.library.acquisition.AcquireCheckRepo.RowTPart3, com.histmersion.domain.library.acquisition.AcquireCheckRepo.RowTPart4, com.histmersion.domain.library.acquisition.AcquireCheckRepo.RowTPart5, com.histmersion.domain.library.acquisition.AcquireCheckRepo.RowTPart6)): AcquireCheckRow =
    AcquireCheckRow(t._1, Instant.ofEpochMilli(t._2), t._3, t._4, t._5, t._6)

  /** The checks of these items (any that has none is absent). */
  def of(items: List[com.histmersion.domain.library.acquisition.AcquireCheckRepo.OfItems])(using DbCon): Map[com.histmersion.domain.library.acquisition.AcquireCheckRepo.OfResultPart1, AcquireCheckRow] =
    if items.isEmpty then Map.empty
    else
      val ids = items.distinct.map(_.toString)
      sql"$Select WHERE item_id::text = ANY($ids::text[])"
        .query[(Int, Long, String, String, String, Int)]
        .run()
        .map(row)
        .map(r => r.item -> r)
        .toMap

  /** The checks of every OPEN acquisition item. */
  def open()(using DbCon): Map[com.histmersion.domain.library.acquisition.AcquireCheckRepo.OpenResultPart1, AcquireCheckRow] =
    sql"""$Select WHERE item_id IN (SELECT id FROM queue_items WHERE list = 'acquire' AND status = 'open')"""
      .query[(Int, Long, String, String, String, Int)]
      .run()
      .map(row)
      .map(r => r.item -> r)
      .toMap

  /** Record one check of `item` (its previous one replaced, the count kept). */
  def record(item: com.histmersion.domain.library.acquisition.AcquireCheckRepo.RecordItem, at: Instant, generation: com.histmersion.domain.library.acquisition.AcquireCheckRepo.RecordGeneration, outcome: AcquireRecheck.Outcome, reason: com.histmersion.domain.library.acquisition.AcquireCheckRepo.RecordReason)(using
      DbCon
  ): Unit =
    val ms = at.toEpochMilli.toDouble / 1000
    val o = outcome.wire
    val _ =
      sql"""INSERT INTO acquire_checks (item_id, checked_at, generation, outcome, reason)
            VALUES ($item, to_timestamp($ms::float8), $generation, $o, $reason)
            ON CONFLICT (item_id) DO UPDATE SET checked_at = EXCLUDED.checked_at, generation = EXCLUDED.generation,
              outcome = EXCLUDED.outcome, reason = EXCLUDED.reason, checks = acquire_checks.checks + 1""".update.run()

  /** How many checks since `since`, in all and per outcome. */
  def countSince(since: Instant)(using DbCon): Map[com.histmersion.domain.library.acquisition.AcquireCheckRepo.CountSinceResultPart1, com.histmersion.domain.library.acquisition.AcquireCheckRepo.CountSinceResultPart2] =
    val ms = since.toEpochMilli.toDouble / 1000
    sql"""SELECT outcome, count(*)::int FROM acquire_checks WHERE checked_at >= to_timestamp($ms::float8)
          GROUP BY outcome""".query[(String, Int)].run().toMap

  /** The sources holding these DOIs (lower-cased): the citekey, and whether it has its original (a converted body or a
    * stored file). One per DOI — the first citekey.
    */
  def heldByDoi(dois: List[com.histmersion.domain.library.acquisition.AcquireCheckRepo.HeldByDoiDois])(using DbCon): Map[com.histmersion.domain.library.acquisition.AcquireCheckRepo.HeldByDoiResultPart1, AcquireRecheck.Held] =
    val ds0 = dois.map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
    if ds0.isEmpty then Map.empty
    else
      sql"""SELECT DISTINCT ON (lower(doi)) lower(doi), citekey,
                             (coalesce(ocr_markdown, '') <> '' OR sha256 IS NOT NULL)
                      FROM sources WHERE lower(doi) = ANY($ds0::text[]) ORDER BY lower(doi), citekey"""
        .query[(String, String, Boolean)]
        .run()
        .map((d, ck, has) => d -> AcquireRecheck.Held(ck, has))
        .toMap
