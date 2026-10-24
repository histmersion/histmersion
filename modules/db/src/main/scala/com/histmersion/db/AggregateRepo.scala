package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

given DbCodec[PeriodRow] = DbCodec.derived

/** Period bounds of everything matching a filter — the raw input of [[com.histmersion.api.Aggregate]].
  *
  * The DB only SELECTS periods; **bucketing happens in Scala** (pure, unit-tested) so the strip
  * (§1.7) and the wave-2 "by time" tree (§1.6) cannot drift apart, and so no SQL has to know about
  * adaptive bucket sizes. */
object AggregateRepo:

  /** The SQL behind [[periods]] — a **pure** value (plan §1.0/§2.2), so the predicate can be
    * asserted on without a database.
    *
    * Built from the SHARED [[FilterSql]] fragments, exactly as the map feed is: the source-level
    * criteria (`q`, authors, topics, status, publication years) select the source, the finding-level
    * ones (period overlap, viewport, place, tags) are evaluated per finding. The window the caller
    * wants bucketed is `filter.period` (the handler always sets it).
    *
    *  - `unit = "findings"` → one row per matching finding, its own period.
    *  - `unit = "sources"`  → one row per matching SOURCE (the unit of every result, §1.0): its own
    *    period, falling back to the envelope of its matching findings when it is undated. A source is
    *    in the window when its own period overlaps it OR one of its matching findings does; when the
    *    filter carries a finding-level place/tag/bbox criterion, at least one finding must match it
    *    (so a source with no findings at all drops out, as it always has for places). */
  def periodsQuery(unit: String, filter: Filter, scope: PlaceScope = PlaceScope.empty): Sql =
    if unit == "findings" then
      Sql.raw(s"SELECT ${PeriodSql.cols("f.period")}" +
        " FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ") ++
        FilterSql.sourceWhere(filter) ++ Sql.raw(" AND ") ++ FilterSql.findingWhere(filter, scope = scope)
    else
      val gate =
        if FilterSql.hasFindingPlaceCriteria(filter) then Sql.raw(" AND fp.matched > 0") else Sql.empty
      val ownPeriod = filter.period.fold(Sql.raw("TRUE"))(PeriodSql.overlaps("s.period", _))
      Sql.raw(
        s"SELECT COALESCE(${PeriodSql.start("s.period")}, fp.min_start), COALESCE(${PeriodSql.end("s.period")}, fp.max_end)" +
          " FROM sources s LEFT JOIN LATERAL (" +
          s"SELECT min(${PeriodSql.start("f.period")}) AS min_start, max(${PeriodSql.end("f.period")}) AS max_end," +
          " count(*) AS matched, count(*) FILTER (WHERE ") ++
        FilterSql.findingPeriodWhere(filter) ++
        Sql.raw(") AS in_window FROM findings f WHERE f.citekey = s.citekey AND ") ++
        FilterSql.findingPlaceWhere(filter, scope = scope) ++
        Sql.raw(") fp ON true WHERE ") ++ FilterSql.sourceWhere(filter) ++ gate ++
        Sql.raw(" AND (") ++ ownPeriod ++ Sql.raw(" OR fp.in_window > 0)")

  /** Run [[periodsQuery]]. `unit` is `sources` (default) or `findings`. */
  def periods(ds: DataSource, unit: String, filter: Filter,
      scope: PlaceScope = PlaceScope.empty): Vector[PeriodRow] =
    connect(ds)(periodsQuery(unit, filter, scope).frag.query[PeriodRow].run())
