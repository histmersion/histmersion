package com.histmersion.domain.catalogue.maptiles

import com.histmersion.util.database.RangeSql

import com.histmersion.api.catalogue.{SiteTile, TileLayer}
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.catalogue.events.EventSql
import com.histmersion.domain.catalogue.places.{PlaceSql, SiteTypesSql}

import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place

/** The map's point layers cut into vector tiles (`GET /api/tiles/…`, [[TileGrid]]): each layer's selection in a tile's
  * box ([[siteCandidates]], [[EventSql.tileCandidates]], another domain's layer's — every item, never a limit), placed
  * on the tile ([[cellsSql]]) and counted by cell where there are more than the grid keeps alone; then the records of
  * the items drawn alone. And the versions of what the layers read ([[versions]], V105).
  */
private[maptiles] object TileRepo:

  /** What a layer's tiles read, by the groups of V105's `map_changes`. */
  type UnitsSqlHeldV = UnitsSqlHeldV.T
  object UnitsSqlHeldV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type GroupsPart2 = GroupsPart2.T
  object GroupsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionsResultPart1 = VersionsResultPart1.T
  object VersionsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionsResultPart2 = VersionsResultPart2.T
  object VersionsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangeRowsResultPart1 = ChangeRowsResultPart1.T
  object ChangeRowsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangeRowsResultPart2 = ChangeRowsResultPart2.T
  object ChangeRowsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompactGrp = CompactGrp.T
  object CompactGrp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitsSqlResultPart1 = UnitsSqlResultPart1.T
  object UnitsSqlResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitsSqlResultPart2 = UnitsSqlResultPart2.T
  object UnitsSqlResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellsSqlMaxPoints = CellsSqlMaxPoints.T
  object CellsSqlMaxPoints extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellsSqlCellUnits = CellsSqlCellUnits.T
  object CellsSqlCellUnits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GridCellsSqlCellUnits = GridCellsSqlCellUnits.T
  object GridCellsSqlCellUnits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCandidatesWindowPart1 = SiteCandidatesWindowPart1.T
  object SiteCandidatesWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCandidatesWindowPart2 = SiteCandidatesWindowPart2.T
  object SiteCandidatesWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCandidatesSiteType = SiteCandidatesSiteType.T
  object SiteCandidatesSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCandidatesModernFrom = SiteCandidatesModernFrom.T
  object SiteCandidatesModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCandidatesMarkUndated = SiteCandidatesMarkUndated.T
  object SiteCandidatesMarkUndated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesOfResultPart1 = SitesOfResultPart1.T
  object SitesOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesOfIds = SitesOfIds.T
  object SitesOfIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCellsWindowPart1 = SiteCellsWindowPart1.T
  object SiteCellsWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCellsWindowPart2 = SiteCellsWindowPart2.T
  object SiteCellsWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCellsSiteType = SiteCellsSiteType.T
  object SiteCellsSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteCellsModernFrom = SiteCellsModernFrom.T
  object SiteCellsModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatedSitesWindowPart1 = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart1
  val DatedSitesWindowPart1: _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart1.type = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart1
  type DatedSitesWindowPart2 = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart2
  val DatedSitesWindowPart2: _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart2.type = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart2
  type DatedSitesSiteType = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesSiteType
  val DatedSitesSiteType: _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesSiteType.type = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesSiteType
  type DatedSitesModernFrom = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesModernFrom
  val DatedSitesModernFrom: _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesModernFrom.type = _root_.com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesModernFrom

  val Groups: Map[TileLayer, List[com.histmersion.domain.catalogue.maptiles.TileRepo.GroupsPart2]] = Map(
    // an undated site is shown by its findings, or by an event, dated then
    TileLayer.Sites -> List("places", "corpus", "events"),
    // under a filter of sources, an event is one of theirs; its anchor and place's name are its place's
    TileLayer.Events -> List("events", "places", "corpus"),
    TileLayer.Media -> List("media")
  )

  /** Each group's version: the sum of its changes' weights (V105). */
  def versions(using DbCon): Map[com.histmersion.domain.catalogue.maptiles.TileRepo.VersionsResultPart1, com.histmersion.domain.catalogue.maptiles.TileRepo.VersionsResultPart2] =
    sql"SELECT grp, sum(weight)::bigint, count(*)::int FROM map_changes GROUP BY grp"
      .query[(String, Long, Int)]
      .run()
      .map((g, v, _) => g -> v)
      .toMap

  /** How many change rows each group holds (a group of many is folded by [[compact]]). */
  def changeRows(using DbCon): Map[com.histmersion.domain.catalogue.maptiles.TileRepo.ChangeRowsResultPart1, com.histmersion.domain.catalogue.maptiles.TileRepo.ChangeRowsResultPart2] =
    sql"SELECT grp, count(*)::int FROM map_changes GROUP BY grp".query[(String, Int)].run().toMap

  /** Fold `grp`'s change rows into one of their summed weight — its version unchanged. One fold at a time (an advisory
    * lock; a second caller does nothing).
    */
  def compact(grp: com.histmersion.domain.catalogue.maptiles.TileRepo.CompactGrp)(using DbCon): Unit =
    val locked = sql"SELECT pg_try_advisory_xact_lock(hashtext('histmersion.map_changes'))".query[Boolean].run().head
    if locked then
      val _ = sql"""WITH d AS (DELETE FROM map_changes WHERE grp = $grp RETURNING weight)
                      INSERT INTO map_changes (grp, weight) SELECT $grp, sum(weight) FROM d HAVING count(*) > 0""".update
        .run()

  /** The tile's box as a PostGIS envelope; a tile at the grid's top or bottom edge reaches the pole, so a place beyond
    * Web Mercator's last latitude is drawn on the edge rather than nowhere.
    */
  def envelope(c: TileCoord): Sql =
    val (w, s, e, n) = c.bounds
    val south = if c.y == c.side - 1 then -90.0 else s
    val north = if c.y == 0 then 90.0 else n
    Sql.of("ST_MakeEnvelope(?, ?, ?, ?, 4326)", Param.D(w), Param.D(south), Param.D(e), Param.D(north))

  /** Web Mercator in SQL over the columns `lon` and `lat`: [[TileCoord.mercator]], each axis held inside the unit
    * square, then in units of tile `c` — `(x, y)`. `TileRepoSuite` holds it to the Scala.
    */
  def unitsSql(c: TileCoord): (com.histmersion.domain.catalogue.maptiles.TileRepo.UnitsSqlResultPart1, com.histmersion.domain.catalogue.maptiles.TileRepo.UnitsSqlResultPart2) =
    val l = s"radians(greatest(${-TileCoord.MaxLat}, least(${TileCoord.MaxLat}, lat)))"
    def held(v: com.histmersion.domain.catalogue.maptiles.TileRepo.UnitsSqlHeldV) = s"least(greatest($v, 0), 0.99999999999)"
    (
      s"(${held("(lon + 180) / 360.0")} * ${c.side} - ${c.x}) * ${TileCoord.Extent}",
      s"(${held(s"(1 - ln(tan($l) + 1 / cos($l)) / pi()) / 2")} * ${c.side} - ${c.y}) * ${TileCoord.Extent}"
    )

  /** The items of `candidates` placed in tile `c` (columns `key`, `lon`, `lat`, `ps`, `pe`, `ub`: an item's key, point,
    * dates and whether it has no known date) — each in exactly one tile of a zoom, its units `px`/`py` in `[0, extent)`
    * — as the CTEs `c`, `k`, `t` (`t` the tile's, with `mid` the middle of the dates). Pure.
    */
  private def placedSql(candidates: Sql, c: TileCoord): Sql =
    val (ux, uy) = unitsSql(c)
    val e = TileCoord.Extent
    Sql.raw("WITH c AS MATERIALIZED (") ++ candidates ++ Sql.raw(
      "), k AS MATERIALIZED (SELECT key, lon, lat, ub, CASE WHEN ps IS NOT NULL AND pe IS NOT NULL THEN (ps + pe) / 2.0 END AS mid," +
        s" $ux AS px, $uy AS py FROM c)" +
        s", t AS MATERIALIZED (SELECT * FROM k WHERE px >= 0 AND px < $e AND py >= 0 AND py < $e)"
    )

  /** The cells of tile `c` over `candidates` ([[placedSql]]): every item alone when there are at most `maxPoints`, else
    * counted by cells of `cellUnits` (a cell of one item answers as the item). Rows as [[TileCell]]'s fields, `undated`
    * how many of a cell's items are marked `ub`. Pure.
    */
  def cellsSql(candidates: Sql, c: TileCoord, maxPoints: com.histmersion.domain.catalogue.maptiles.TileRepo.CellsSqlMaxPoints, cellUnits: com.histmersion.domain.catalogue.maptiles.TileRepo.CellsSqlCellUnits): Sql =
    val perSide = (TileCoord.Extent / cellUnits.max(1)).max(1)
    placedSql(candidates, c) ++ Sql.raw(
      ", total AS (SELECT count(*) AS n FROM t)" +
        " SELECT key, floor(px)::int, floor(py)::int, 1, mid, mid, lon, lat, lon, lat, CASE WHEN ub THEN 1 ELSE 0 END FROM t"
    ) ++
      Sql.of(" WHERE (SELECT n FROM total) <= ?", Param.I(maxPoints)) ++
      Sql.raw(
        s" UNION ALL SELECT CASE WHEN g.n = 1 THEN g.one ELSE 'c${c.z}/' || (${c.x * perSide} + g.gx) || '/' ||" +
          s" (${c.y * perSide} + g.gy) END, floor(g.ax)::int, floor(g.ay)::int, g.n, g.lo, g.hi, g.w, g.s, g.e, g.nn, g.u" +
          s" FROM (SELECT floor(px / ${cellUnits.max(1)})::bigint AS gx, floor(py / ${cellUnits.max(1)})::bigint AS gy," +
          " count(*)::int AS n, avg(px) AS ax, avg(py) AS ay, min(mid) AS lo, max(mid) AS hi," +
          " min(lon) AS w, min(lat) AS s, max(lon) AS e, max(lat) AS nn, min(key) AS one," +
          " (count(*) FILTER (WHERE ub))::int AS u FROM t"
      ) ++
      Sql.of(" WHERE (SELECT n FROM total) > ? GROUP BY 1, 2) g", Param.I(maxPoints))

  /** The items of `candidates` in tile `c` ([[placedSql]]) counted by the squares of `cellUnits`, whatever their
    * number: one [[GridCell]] a square (`dup` those marked `ub`), its square on the world's grid of the tile's zoom.
    * Pure.
    */
  def gridCellsSql(candidates: Sql, c: TileCoord, cellUnits: com.histmersion.domain.catalogue.maptiles.TileRepo.GridCellsSqlCellUnits): Sql =
    val perSide = (TileCoord.Extent / cellUnits.max(1)).max(1)
    val cu = cellUnits.max(1)
    placedSql(candidates, c) ++ Sql.raw(
      s" SELECT ${c.x * perSide} + floor(px / $cu)::bigint, ${c.y * perSide} + floor(py / $cu)::bigint," +
        " count(*)::int, (count(*) FILTER (WHERE ub))::int, sum(px), sum(py)," +
        " coalesce(sum(px) FILTER (WHERE ub), 0), coalesce(sum(py) FILTER (WHERE ub), 0), min(mid), max(mid)," +
        " min(lon), min(lat), max(lon), max(lat), min(key) FROM t GROUP BY 1, 2"
    )

  private given DbCodec[TileCell] = DbCodec.derived
  private given DbCodec[GridCell] = DbCodec.derived

  /** The cells of tile `c` over a layer's `candidates` ([[cellsSql]]). */
  def cells(candidates: Sql, c: TileCoord, grid: TileGrid)(using DbCon): List[TileCell] =
    cellsSql(candidates, c, grid.pointsAt(c), grid.cellUnits).frag.query[TileCell].run().toList

  /** Every item of `candidates` in tile `c`, each alone. */
  def items(candidates: Sql, c: TileCoord)(using DbCon): List[TileCell] =
    cellsSql(candidates, c, Int.MaxValue, TileCoord.Extent).frag.query[TileCell].run().toList

  /** The items of `candidates` in tile `c` counted by the grid's squares ([[gridCellsSql]]). */
  def gridCells(candidates: Sql, c: TileCoord, grid: TileGrid)(using DbCon): List[GridCell] =
    gridCellsSql(candidates, c, grid.cellUnits).frag.query[GridCell].run().toList

  // ── the sites ───────────────────────────────────────────────────────────────────────────────

  /** The sites a time view of `window` shows ([[PlaceSql.inWindowSql]]: a dated site overlapping it, a present-day one
    * from `modern-from`, an undated one by findings or events dated in it — never an undated import), of type
    * `siteType` when one is asked for, in the tile's box `envelope`: every one the gazetteer has, imported from
    * Wikidata as much as curated (the places layer draws an imported place only once a finding names it; a site is no
    * less a site for being in no source yet). Over `places_site_map` (V105), whose predicate is stated word for word.
    */
  def siteCandidates(
      window: (com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart1, com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesWindowPart2),
      siteType: Option[com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesSiteType],
      modernFrom: com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesModernFrom,
      envelope: Sql,
      markUndated: com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCandidatesMarkUndated = false
  ): Sql =
    val tw = Some(window)
    // `ub`: a site of no known date the span shows by its findings or events — counted among the undated
    // sites too when they are asked for ([[UndatedSiteRepo]])
    val ub = if markUndated then "(lower_inf(p.period) AND upper_inf(p.period))" else "false"
    Sql.raw(
      "SELECT p.id AS key, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat," +
        s" ${RangeSql.start("p.period")} AS ps, ${RangeSql.end("p.period")} AS pe, $ub AS ub" +
        " FROM places p CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(p.geom) = 'POINT' THEN p.geom" +
        " ELSE ST_PointOnSurface(p.geom) END AS pt) q" +
        s" WHERE p.kind = '${Place.Kind.Site}' AND p.geom IS NOT NULL" +
        s" AND NOT (coalesce(p.import_source, '') IN ${PlaceSql.ImportedList} AND lower_inf(p.period) AND upper_inf(p.period))" +
        " AND p.geom && "
    ) ++ envelope ++
      Sql.of(" AND p.label !~* ?", Param.S(PlaceSql.UninformativeLabel)) ++
      Sql.raw(s" AND ${PlaceSql.inWindowSql("p", tw, Some(Place.Kind.Site), Some(modernFrom))}") ++
      siteType
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .fold(Sql.empty)(t => Sql.of(s" AND ${SiteTypesSql.expr("p")} = ?", Param.S(t)))

  /** The sites of `ids` as a sites tile draws one, by id. */
  def sitesOf(ids: List[com.histmersion.domain.catalogue.maptiles.TileRepo.SitesOfIds])(using DbCon): Map[com.histmersion.domain.catalogue.maptiles.TileRepo.SitesOfResultPart1, SiteTile] =
    if ids.isEmpty then Map.empty
    else
      Sql
        .of(
          s"SELECT p.id, p.label, p.kind, ${SiteTypesSql.expr("p")}, ${PlaceSql.timeBasisSql("p")}," +
            s" ${RangeSql.start("p.period")}, ${RangeSql.end("p.period")} FROM places p WHERE p.id = ANY(?)",
          Param.A(ids)
        )
        .frag
        .query[(String, String, String, Option[String], String, Option[Long], Option[Long])]
        .run()
        .map((id, label, kind, t, basis, ps, pe) => id -> SiteTile(id, label, kind, t, basis, ps, pe))
        .toMap

  /** A sites tile's cells. */
  def siteCells(
      c: TileCoord,
      grid: TileGrid,
      window: (com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCellsWindowPart1, com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCellsWindowPart2),
      siteType: Option[com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCellsSiteType],
      modernFrom: com.histmersion.domain.catalogue.maptiles.TileRepo.SiteCellsModernFrom
  )(using DbCon): List[TileCell] =
    cells(siteCandidates(window, siteType, modernFrom, envelope(c)), c, grid)

  /** The sites tile `c` shows for `window` as candidates of [[items]] and [[gridCells]], an undated one the span shows
    * by its findings marked (it is among the undated sites too).
    */
  def datedSites(c: TileCoord, window: (com.histmersion.domain.catalogue.maptiles.TileRepo.DatedSitesWindowPart1, com.histmersion.domain.catalogue.maptiles.TileRepo.DatedSitesWindowPart2), siteType: Option[com.histmersion.domain.catalogue.maptiles.TileRepo.DatedSitesSiteType], modernFrom: com.histmersion.domain.catalogue.maptiles.TileRepo.DatedSitesModernFrom): Sql =
    siteCandidates(window, siteType, modernFrom, envelope(c), markUndated = true)

  /** An events tile's cells, with the events of no known date `undatedWhere` selects
    * ([[EventSql.tileCandidatesWithUndated]]).
    */
  def eventCellsWithUndated(
      c: TileCoord,
      grid: TileGrid,
      where: Sql,
      undatedWhere: Sql
  )(using DbCon): List[TileCell] =
    cells(EventSql.tileCandidatesWithUndated(where, undatedWhere, envelope(c)), c, grid)

  /** An events tile's cells: the events `where` selects (over `events e` with its place `ep`, [[EventSql.listWhere]]).
    */
  def eventCells(c: TileCoord, grid: TileGrid, where: Sql)(using DbCon): List[TileCell] =
    cells(EventSql.tileCandidates(where, envelope(c)), c, grid)
