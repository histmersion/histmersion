package com.histmersion
package domain.library.search

import com.histmersion.api.library.Tree

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Period
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.RegionNode

/** Period bounds of everything matching a filter — the raw input of [[com.histmersion.api.library.Aggregate]].
  *
  * The DB only SELECTS periods; **bucketing happens in Scala** (pure, unit-tested) so the strip (§1.7) and the wave-2
  * "by time" tree (§1.6) cannot drift apart, and so no SQL has to know about adaptive bucket sizes.
  */
private[search] object AggregateRepo:
  type Unit = Unit.T
  object Unit extends Newtype[String, Pure]

  private given DbCodec[Period.Row] = DbCodec.derived

  /** The SQL behind [[periods]] — a **pure** value (plan §1.0/§2.2), so the predicate can be asserted on without a
    * database.
    *
    * Built from the SHARED [[FilterSql]] fragments, exactly as the map feed is: the source-level criteria (`q`,
    * authors, topics, status, publication years) select the source, the finding-level ones (period overlap, viewport,
    * place, tags) are evaluated per finding. The window the caller wants bucketed is `filter.period` (the handler
    * always sets it).
    *
    *   - `unit = "findings"` → one row per matching finding, its own period.
    *   - `unit = "sources"` → one row per matching SOURCE (the unit of every result, §1.0): its own period, falling
    *     back to the envelope of its matching findings when it is undated. A source is in the window when its own
    *     period overlaps it OR one of its matching findings does; when the filter carries a finding-level
    *     place/tag/bbox criterion, at least one finding must match it (so a source with no findings at all drops out,
    *     as it always has for places).
    */
  def periodsQuery(unit: Unit, filter: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    if unit.value == "findings" then
      Sql.raw(
        Sql.Text(
          s"SELECT ${RangeSql.cols("f.period")}" +
            " FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE "
        )
      ) ++
        FilterSql.sourceWhere(filter) ++ Sql.raw(Sql.Text(" AND ")) ++ FilterSql.findingWhere(filter, scope = scope)
    else
      val gate =
        if FilterSql.hasFindingPlaceCriteria(filter) then Sql.raw(Sql.RawText(" AND fp.matched > 0")) else Sql.empty
      val ownPeriod = filter.period.fold(Sql.raw(Sql.RawText("TRUE")))(RangeSql.overlaps("s.period", _))
      Sql.raw(
        Sql.Text(
          s"SELECT COALESCE(${RangeSql.start("s.period")}, fp.min_start), COALESCE(${RangeSql.end("s.period")}, fp.max_end)" +
            " FROM sources s LEFT JOIN LATERAL (" +
            s"SELECT min(${RangeSql.start("f.period")}) AS min_start, max(${RangeSql.end("f.period")}) AS max_end," +
            " count(*) AS matched, count(*) FILTER (WHERE "
        )
      ) ++
        FilterSql.findingPeriodWhere(filter) ++
        Sql.raw(Sql.Text(") AS in_window FROM findings f WHERE f.citekey = s.citekey AND ")) ++
        FilterSql.findingPlaceWhere(filter, scope = scope) ++
        Sql.raw(Sql.Text(") fp ON true WHERE ")) ++ FilterSql.sourceWhere(filter) ++ gate ++
        Sql.raw(Sql.Text(" AND (")) ++ ownPeriod ++ Sql.raw(Sql.Text(" OR fp.in_window > 0)"))

  /** Run [[periodsQuery]]. `unit` is `sources` (default) or `findings`. */
  def periods(unit: Unit, filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): Vector[Period.Row] =
    periodsQuery(unit, filter, scope).frag.query[Period.Row].run()

  /** One row as SQL returns it — the place ids arrive as a `text[]`. */
  final private[search] case class TreeRowRaw(
      key: TreeRowRaw.Key,
      citekey: Citekey,
      title: TreeRowRaw.Title,
      periodStart: Option[DeepTime.SignedYear],
      periodEnd: Option[DeepTime.SignedYear],
      placeIds: List[TreeRowRaw.PlaceIds]
  ) derives DbCodec:
    def toRow: Tree.TreeRow =
      Tree.TreeRow(
        Tree.TreeRow.Key(key.value),
        com.histmersion.api.library.Citekey(citekey.value),
        com.histmersion.api.library.Title(title.value),
        periodStart.map(y => Year(y.value)),
        periodEnd.map(y => Year(y.value)),
        placeIds.distinct.map(p => RegionNode.Id(p.value))
      )

  object TreeRowRaw:
    type Key = Key.T
    object Key extends Newtype[String, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type PlaceIds = PlaceIds.T
    object PlaceIds extends Newtype[String, Pure]

  // ── the rows of the §1.6 aggregation tree ──────────────────────────────────────────────────────

  /** The gazetteer places one FINDING was materialised into, as an array (correlated on `fa`). */
  private def placesOf(fa: String): String =
    s"ARRAY(SELECT fp.place_id FROM finding_places fp WHERE fp.finding_id = $fa.id ORDER BY fp.place_id)"

  /** The SQL behind [[treeRows]] — a **pure** value, so the predicate can be asserted on without a database (plan
    * §1.0/§2.2).
    *
    *   - `unit = "findings"` → one row per matching finding: its own period and its own places.
    *   - `unit = "sources"` → one row per matching SOURCE (the unit of every result, §1.0): its own period, falling
    *     back to the envelope of its matching findings when it is undated, and the UNION of those findings' places. The
    *     window/gate logic mirrors [[AggregateRepo.periodsQuery]] exactly, so the tree and the timeline strip always
    *     agree about which sources are in scope.
    *
    * Ordering is the caller's `sort=`, which is what makes `samples` under a leaf the *top* sources for that bucket
    * rather than an arbitrary few.
    */
  def treeRowsQuery(unit: Unit, filter: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    if unit.value == "findings" then
      Sql.raw(
        Sql.Text(
          s"SELECT f.id, f.citekey, s.title, ${RangeSql.cols("f.period")}, ${placesOf("f")}" +
            " FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE "
        )
      ) ++
        FilterSql.sourceWhere(filter) ++ Sql.raw(Sql.Text(" AND ")) ++ FilterSql.findingWhere(filter, scope = scope) ++
        Sql.raw(Sql.Text(" ")) ++ FilterSql.orderBySql(filter)
    else
      val gate =
        if FilterSql.hasFindingPlaceCriteria(filter) then Sql.raw(Sql.RawText(" AND fp.matched > 0")) else Sql.empty
      val ownPeriod = filter.period.fold(Sql.raw(Sql.RawText("TRUE")))(RangeSql.overlaps("s.period", _))
      Sql.raw(
        Sql.Text(
          "SELECT s.citekey, s.citekey, s.title," +
            s" COALESCE(${RangeSql.start("s.period")}, fp.min_start), COALESCE(${RangeSql.end("s.period")}, fp.max_end)," +
            " coalesce(fp.place_ids, '{}'::text[])" +
            " FROM sources s LEFT JOIN LATERAL (" +
            s"SELECT min(${RangeSql.start("f.period")}) AS min_start, max(${RangeSql.end("f.period")}) AS max_end," +
            " count(DISTINCT f.id) AS matched," +
            " count(DISTINCT f.id) FILTER (WHERE "
        )
      ) ++
        FilterSql.findingPeriodWhere(filter) ++
        Sql.raw(Sql.Text(
          ") AS in_window, array_agg(DISTINCT l.place_id) FILTER (WHERE l.place_id IS NOT NULL) AS place_ids" +
            " FROM findings f LEFT JOIN finding_places l ON l.finding_id = f.id" +
            " WHERE f.citekey = s.citekey AND "
        )) ++
        FilterSql.findingPlaceWhere(filter, scope = scope) ++
        Sql.raw(Sql.Text(") fp ON true WHERE ")) ++ FilterSql.sourceWhere(filter) ++ gate ++
        Sql.raw(Sql.Text(" AND (")) ++ ownPeriod ++ Sql.raw(Sql.Text(" OR fp.in_window > 0) ")) ++
        FilterSql.orderBySql(filter)

  /** Run [[treeRowsQuery]]: the rows the tree buckets and nests ([[com.histmersion.api.library.Tree]], pure), hung off
    * the gazetteer the catalogue gives (`TreeRepo.gazetteer`). `unit` is `sources` (default) or `findings`.
    */
  def treeRows(unit: Unit, filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): Vector[Tree.TreeRow] =
    treeRowsQuery(unit, filter, scope).frag.query[TreeRowRaw].run().map(_.toRow)
