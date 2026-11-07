package com.histmersion.domain.catalogue.periods

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*
import com.histmersion.domain.catalogue.places.PlaceSql
import com.histmersion.util.database.RangeSql

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle

/** The named periods as a bundle carries them (docs/research-merge-import.md §4, §7.3): this collection's own, read out
  * in a deterministic order, and written in by an import.
  */
private[periods] object PeriodBundleRepo:

  /** The named periods of this collection's own in scope, or with one of `alsoIds`. */

  def periods(scope: BundleScope, alsoIds: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id] = Nil)(using DbCon): Vector[Bundle.Period] =
    val own = Sql.raw(Sql.Text("p.dataset = 'local' AND ")) ++ where(scope, "p.uid", "p.updated_at")
    val sel = if alsoIds.isEmpty then own
    else Sql.raw(com.histmersion.util.database.Sql.RawText("((")) ++ own ++ Sql.of(Sql.Text(") OR p.id = ANY(?::text[]))"), Param.A(alsoIds.distinct.map(i => Param.A.V(i.value))))
    (Sql.raw(Sql.Text(s"""SELECT p.uid, p.origin, p.id, p.label, p.authority, ${rangeCols("p.period")}, p.region, p.parent_id,
                       ${ts("p.updated_at")}
                FROM periods p WHERE """)) ++ sel ++ " ORDER BY p.uid").frag.query[BPeriodRow].run().map { r =>
      Bundle.Period(
        r.uid,
        r.origin,
        Bundle.Period.Id(r.id.value),
        Bundle.Period.Label(r.label.value),
        r.authority.map(a => Bundle.Period.Authority(a.value)),
        range(
          com.histmersion.util.data.exchange.Bundle.RangeIsNull(r.pNull.value),
          r.pLo.map(l => com.histmersion.util.data.exchange.Bundle.RangeLower(l.value)),
          r.pHi.map(h => com.histmersion.util.data.exchange.Bundle.RangeUpper(h.value))
        ),
        r.region.map(g => Bundle.Period.Region(g.value)),
        r.parentId.map(p => Bundle.Period.ParentId(p.value)),
        Bundle.Period.UpdatedAt(r.updatedAt.value)
      )
    }

  /** Which of `ids` name a period here. */
  def present(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id])(using DbCon): Set[com.histmersion.domain.catalogue.periods.BPeriodRow.Id] =
    if ids.isEmpty then Set.empty
    else sql"SELECT id FROM periods WHERE id = ANY(${ids.distinct}::text[])".query[com.histmersion.domain.catalogue.periods.BPeriodRow.Id].run().toSet

  /** A bundle period with no id here that names a period here: the same label (reduced) and spans sharing a year.
    */
  def matchPeriods(ps: List[Bundle.Period])(using DbCon): Map[UUID, com.histmersion.domain.catalogue.periods.BPeriodRow.Id] =
    val norm = PlaceSql.normLabelSql("p.label")
    ps.flatMap { b =>
      val (lo, hi) = (b.period.flatMap(_.start), b.period.flatMap(_.end))
      Sql
        .of(
          Sql.Text(s"""SELECT p.id FROM periods p
            WHERE $norm = ${PlaceSql.normLabelSql("?")}
              AND p.period && ${RangeSql.range("nullif(?, '')::bigint", "nullif(?, '')::bigint")}
            ORDER BY CASE WHEN p.dataset = 'local' THEN 0 ELSE 1 END, p.id LIMIT 1"""),
          Param.S(Param.S.V(b.label.value)),
          Param.S(Param.S.V(lo.fold("")(_.toString))),
          Param.S(Param.S.V(hi.fold("")(_.toString)))
        )
        .frag
        .query[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]
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
      sql"""UPDATE periods SET parent_id = (SELECT id FROM periods WHERE id = ${p.parentId}), updated_at = ${p.updatedAt}::timestamptz
            WHERE uid = ${p.uid}""".update.run()
    }
