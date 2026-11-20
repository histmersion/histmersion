package com.histmersion
package domain.catalogue.periods

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*
import com.histmersion.domain.catalogue.places.PlaceSql
import com.histmersion.util.database.RangeSql

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused

/** The named periods as a bundle carries them (docs/research-merge-import.md §4, §7.3): this collection's own, read out
  * in a deterministic order, and written in by an import.
  */
private[periods] object PeriodBundleRepo:

  /** The named periods of this collection's own in scope, or with one of `alsoIds`. */

  def periods(scope: BundleScope, alsoIds: List[BPeriodRow.Id] = Nil)(using DbCon): Vector[Bundle.Period] =
    val own = sql"p.dataset = 'local' AND ${where(scope, sql"p.uid", sql"p.updated_at")}"
    val sel = if alsoIds.isEmpty then own
    else sql"(($own) OR p.id = ANY(${alsoIds.distinct}::text[]))"
    sql"""SELECT p.uid, p.origin, p.id, p.label, p.authority, ${rangeCols(sql"p.period")}, p.region, p.parent_id,
                       ${Timestamps.iso(sql"p.updated_at")}
                FROM periods p WHERE $sel ORDER BY p.uid""".query[BPeriodRow].run().map { r =>
      Bundle.Period(
        r.uid,
        r.origin,
        Bundle.Period.Id(r.id.value),
        Bundle.Period.Label(r.label.value),
        r.authority.map(a => Bundle.Period.Authority(a.value)),
        range(
          util.data.exchange.Bundle.RangeIsNull(r.pNull.value),
          r.pLo.map(l => util.data.exchange.Bundle.RangeLower(l.value)),
          r.pHi.map(h => util.data.exchange.Bundle.RangeUpper(h.value))
        ),
        r.region.map(g => Bundle.Period.Region(g.value)),
        r.parentId.map(p => Bundle.Period.ParentId(p.value)),
        Bundle.Period.UpdatedAt(r.updatedAt.value)
      )
    }

  /** Which of `ids` name a period here. */
  def present(ids: List[BPeriodRow.Id])(using DbCon): Set[BPeriodRow.Id] =
    if ids.isEmpty then Set.empty
    else sql"SELECT id FROM periods WHERE id = ANY(${ids.distinct}::text[])".query[BPeriodRow.Id].run().toSet

  /** A bundle period with no id here that names a period here: the same label (reduced) and spans sharing a year.
    */
  def matchPeriods(ps: List[Bundle.Period])(using DbCon): Map[UUID, BPeriodRow.Id] =
    val norm = PlaceSql.normLabel(sql"p.label")
    ps.flatMap { b =>
      val (lo, hi) = (b.period.flatMap(_.start), b.period.flatMap(_.end))
      val window =
        RangeSql.range(
          sql"nullif(${lo.fold("")(_.toString)}, '')::bigint",
          sql"nullif(${hi.fold("")(_.toString)}, '')::bigint"
        )
      sql"""SELECT p.id FROM periods p
            WHERE $norm = ${PlaceSql.normLabel(sql"${b.label}")}
              AND p.period && $window
            ORDER BY CASE WHEN p.dataset = 'local' THEN 0 ELSE 1 END, p.id LIMIT 1"""
        .query[BPeriodRow.Id]
        .run()
        .headOption
        .map(b.uid -> _)
    }.toMap

  /** Named periods of the other collection's own (V052), then their parents. */
  def periodsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val periods = w.es.periods.filter(p => w.writes(p.uid))
    periods.foreach { p =>
      val range = rangeOrAll(p.period)
      sql"""INSERT INTO periods (uid, origin, id, label, authority, period, region, dataset, updated_at)
            VALUES (${p.uid}, ${p.origin}, ${p.id}, ${p.label}, ${p.authority},
              $range::int8range, ${p.region}, 'local', ${p.updatedAt}::timestamptz)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, label = EXCLUDED.label,
              authority = EXCLUDED.authority, period = EXCLUDED.period, region = EXCLUDED.region,
              dataset = 'local', updated_at = EXCLUDED.updated_at""".update.run()
    }
    periods.foreach { p =>
      Unused(
        sql"""UPDATE periods SET parent_id = (SELECT id FROM periods WHERE id = ${p.parentId}), updated_at = ${p.updatedAt}::timestamptz
            WHERE uid = ${p.uid}""".update.run(),
        Unused.Why.Idempotent
      )
    }
