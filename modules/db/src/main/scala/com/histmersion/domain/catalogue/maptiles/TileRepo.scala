package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.util.database.{literal, RangeSql}

import com.histmersion.api.catalogue.{SiteTile, TileLayer}
import com.histmersion.domain.catalogue.events.EventSql
import com.histmersion.domain.catalogue.places.{PlaceSql, SiteTypesSql}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.given
import com.histmersion.util.database.Unused
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The map's point layers cut into vector tiles (`GET /api/tiles/…`, [[TileGrid]]): each layer's selection in a tile's
  * box ([[siteCandidates]], [[EventSql.tileCandidates]], another domain's layer's — every item, never a limit), placed
  * on the tile ([[cellsSql]]) and counted by cell where there are more than the grid keeps alone; then the records of
  * the items drawn alone. And the versions of what the layers read ([[versions]], V105).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the tile tables and their row codecs")
private[maptiles] object TileRepo:
  type GroupName = GroupName.T
  object GroupName extends Newtype[String, Pure]
  type SiteId = SiteId.T
  object SiteId extends Newtype[String, Pure]
  type SiteType = SiteType.T
  object SiteType extends Newtype[String, Pure]

  /** What a layer's tiles read, by the groups of V105's `map_changes`. */
  val Groups: Map[TileLayer, List[GroupName]] = Map(
    // an undated site is shown by its findings, or by an event, dated then
    TileLayer.Sites -> List(GroupName("places"), GroupName("corpus"), GroupName("events")),
    // under a filter of sources, an event is one of theirs; its anchor and place's name are its place's
    TileLayer.Events -> List(GroupName("events"), GroupName("places"), GroupName("corpus")),
    TileLayer.Media -> List(GroupName("media"))
  )

  /** Each group's version: the sum of its changes' weights (V105). */
  def versions(using DbCon): Map[GroupName, UndatedBuild.Version] =
    sql"SELECT grp, sum(weight)::bigint, count(*)::int FROM map_changes GROUP BY grp"
      .query[(String, Long, Int)]
      .run()
      .map((g, v, _) => GroupName(g) -> UndatedBuild.Version(v))
      .toMap

  /** How many change rows each group holds (a group of many is folded by [[compact]]). */
  def changeRows(using DbCon): Map[GroupName, RowCount] =
    sql"SELECT grp, count(*)::int FROM map_changes GROUP BY grp"
      .query[(String, Int)]
      .run()
      .map((g, n) => GroupName(g) -> RowCount(n))
      .toMap

  /** Fold `grp`'s change rows into one of their summed weight — its version unchanged. One fold at a time (an advisory
    * lock; a second caller does nothing).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def compact(grp: GroupName)(using DbCon): Unit =
    val locked = sql"SELECT pg_try_advisory_xact_lock(hashtext('histmersion.map_changes'))".query[Boolean].run().head
    if locked then
      Unused(
        sql"""WITH d AS (DELETE FROM map_changes WHERE grp = $grp RETURNING weight)
                      INSERT INTO map_changes (grp, weight) SELECT $grp, sum(weight) FROM d HAVING count(*) > 0""".update
          .run(),
        Unused.Why.Idempotent
      )

  /** The tile's box as a PostGIS envelope; a tile at the grid's top or bottom edge reaches the pole, so a place beyond
    * Web Mercator's last latitude is drawn on the edge rather than nowhere.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def envelope(c: TileCoord): Frag =
    val (w, s, e, n) = c.bounds
    val south = if c.y.value == c.side.value - 1 then -90.0 else s
    val north = if c.y.value == 0 then 90.0 else n
    sql"ST_MakeEnvelope($w, $south, $e, $north, 4326)"

  /** Web Mercator in SQL over the columns `lon` and `lat`: [[TileCoord.mercator]], each axis held inside the unit
    * square, then in units of tile `c` — `(x, y)`. `TileRepoSuite` holds it to the Scala.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def unitsSql(c: TileCoord): (Frag, Frag) =
    val l = sql"radians(greatest(${(-TileCoord.MaxLat.value).literal}, least(${TileCoord.MaxLat.literal}, lat)))"
    def held(v: Frag) = sql"least(greatest($v, 0), 0.99999999999)"
    val (side, extent) = (c.side.literal, TileCoord.Extent.literal)
    (
      sql"(${held(sql"(lon + 180) / 360.0")} * $side - ${c.x.literal}) * $extent",
      sql"(${held(sql"(1 - ln(tan($l) + 1 / cos($l)) / pi()) / 2")} * $side - ${c.y.literal}) * $extent"
    )

  /** The items of `candidates` placed in tile `c` (columns `key`, `lon`, `lat`, `ps`, `pe`, `ub`: an item's key, point,
    * dates and whether it has no known date) — each in exactly one tile of a zoom, its units `px`/`py` in `[0, extent)`
    * — as the CTEs `c`, `k`, `t` (`t` the tile's, with `mid` the middle of the dates). Pure.
    */
  private def placedSql(candidates: Frag, c: TileCoord): Frag =
    val (ux, uy) = unitsSql(c)
    val e = TileCoord.Extent
    sql"WITH c AS MATERIALIZED ($candidates), k AS MATERIALIZED (SELECT key, lon, lat, ub, CASE WHEN ps IS NOT NULL AND pe IS NOT NULL THEN (ps + pe) / 2.0 END AS mid, $ux AS px, $uy AS py FROM c), t AS MATERIALIZED (SELECT * FROM k WHERE px >= 0 AND px < ${e.literal} AND py >= 0 AND py < ${e.literal})"

  /** The cells of tile `c` over `candidates` ([[placedSql]]): every item alone when there are at most `maxPoints`, else
    * counted by cells of `cellUnits` (a cell of one item answers as the item). Rows as [[TileCell]]'s fields, `undated`
    * how many of a cell's items are marked `ub`. Pure.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def cellsSql(candidates: Frag, c: TileCoord, maxPoints: TileGrid.MaxPoints, cellUnits: TileGrid.CellUnits): Frag =
    val units = cellUnits.value.max(1)
    val perSide = (TileCoord.Extent.value / units).max(1)
    sql"${placedSql(candidates, c)}, total AS (SELECT count(*) AS n FROM t) SELECT key, floor(px)::int, floor(py)::int, 1, mid, mid, lon, lat, lon, lat, CASE WHEN ub THEN 1 ELSE 0 END FROM t WHERE (SELECT n FROM total) <= $maxPoints UNION ALL SELECT CASE WHEN g.n = 1 THEN g.one ELSE 'c${c.z.literal}/' || (${(c.x.value * perSide).literal} + g.gx) || '/' || (${(c.y.value * perSide).literal} + g.gy) END, floor(g.ax)::int, floor(g.ay)::int, g.n, g.lo, g.hi, g.w, g.s, g.e, g.nn, g.u FROM (SELECT floor(px / ${units.literal})::bigint AS gx, floor(py / ${units.literal})::bigint AS gy, count(*)::int AS n, avg(px) AS ax, avg(py) AS ay, min(mid) AS lo, max(mid) AS hi, min(lon) AS w, min(lat) AS s, max(lon) AS e, max(lat) AS nn, min(key) AS one, (count(*) FILTER (WHERE ub))::int AS u FROM t WHERE (SELECT n FROM total) > $maxPoints GROUP BY 1, 2) g"

  /** The items of `candidates` in tile `c` ([[placedSql]]) counted by the squares of `cellUnits`, whatever their
    * number: one [[GridCell]] a square (`dup` those marked `ub`), its square on the world's grid of the tile's zoom.
    * Pure.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def gridCellsSql(candidates: Frag, c: TileCoord, cellUnits: TileGrid.CellUnits): Frag =
    val cu = cellUnits.value.max(1)
    val perSide = (TileCoord.Extent.value / cu).max(1)
    sql"${placedSql(candidates, c)} SELECT ${(c.x.value * perSide).literal} + floor(px / ${cu.literal})::bigint, ${(c.y.value * perSide).literal} + floor(py / ${cu.literal})::bigint, count(*)::int, (count(*) FILTER (WHERE ub))::int, sum(px), sum(py), coalesce(sum(px) FILTER (WHERE ub), 0), coalesce(sum(py) FILTER (WHERE ub), 0), min(mid), max(mid), min(lon), min(lat), max(lon), max(lat), min(key) FROM t GROUP BY 1, 2"

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[TileCell] = DbCodec.derived
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[GridCell] = DbCodec.derived

  /** The cells of tile `c` over a layer's `candidates` ([[cellsSql]]). */
  def cells(candidates: Frag, c: TileCoord, grid: TileGrid)(using DbCon): List[TileCell] =
    cellsSql(candidates, c, grid.pointsAt(c), grid.cellUnits).query[TileCell].run().toList

  /** Every item of `candidates` in tile `c`, each alone. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def items(candidates: Frag, c: TileCoord)(using DbCon): List[TileCell] =
    cellsSql(candidates, c, TileGrid.MaxPoints(Int.MaxValue), TileGrid.CellUnits(TileCoord.Extent.value))
      .query[TileCell]
      .run()
      .toList

  /** The items of `candidates` in tile `c` counted by the grid's squares ([[gridCellsSql]]). */
  def gridCells(candidates: Frag, c: TileCoord, grid: TileGrid)(using DbCon): List[GridCell] =
    gridCellsSql(candidates, c, grid.cellUnits).query[GridCell].run().toList

  // ── the sites ───────────────────────────────────────────────────────────────────────────────

  /** The sites a time view of `window` shows ([[PlaceSql.inWindowSql]]: a dated site overlapping it, a present-day one
    * from `modern-from`, an undated one by findings or events dated in it — never an undated import), of type
    * `siteType` when one is asked for, in the tile's box `envelope`: every one the gazetteer has, imported from
    * Wikidata as much as curated (the places layer draws an imported place only once a finding names it; a site is no
    * less a site for being in no source yet). Over `places_site_map` (V105), whose predicate is stated word for word.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def siteCandidates(
      window: (DeepTime.SignedYear, DeepTime.SignedYear),
      siteType: Option[SiteType],
      modernFrom: DeepTime.SignedYear,
      envelope: Frag,
      markUndated: Boolean = false
  ): Frag =
    val tw = Some(window)
    // `ub`: a site of no known date the span shows by its findings or events — counted among the undated
    // sites too when they are asked for ([[UndatedSiteRepo]])
    val ub = if markUndated then sql"(lower_inf(p.period) AND upper_inf(p.period))" else sql"false"
    // `p.kind = 'site'` is `Place.Kind.Site` word for word, written in: `places_site_map`'s predicate (TileRepoSuite)
    sql"SELECT p.id AS key, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat, ${RangeSql.start(sql"p.period")} AS ps, ${RangeSql.end(sql"p.period")} AS pe, $ub AS ub FROM places p CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(p.geom) = 'POINT' THEN p.geom ELSE ST_PointOnSurface(p.geom) END AS pt) q WHERE p.kind = 'site' AND p.geom IS NOT NULL AND NOT (coalesce(p.import_source, '') IN ${PlaceSql.ImportedList} AND lower_inf(p.period) AND upper_inf(p.period)) AND p.geom && $envelope AND p.label !~* ${PlaceSql.UninformativeLabel} AND ${PlaceSql.inWindowSql(sql"p", tw, Some(Place.Kind.Wire(Place.Kind.Site)), Some(modernFrom))}${siteType
        .map(_.value.trim.toLowerCase)
        .filter(_.nonEmpty)
        .fold(sql"")(t => sql" AND ${SiteTypesSql.expr(sql"p")} = $t")}"

  /** The sites of `ids` as a sites tile draws one, by id. */
  def sitesOf(ids: List[SiteId])(using DbCon): Map[SiteId, SiteTile] =
    if ids.isEmpty then Map.empty
    else
      sql"SELECT p.id, p.label, p.kind, ${SiteTypesSql.expr(sql"p")}, ${PlaceSql.timeBasisSql(sql"p")}, ${RangeSql.start(sql"p.period")}, ${RangeSql.end(sql"p.period")} FROM places p WHERE p.id = ANY($ids)"
        .query[(String, String, String, Option[String], String, Option[Long], Option[Long])]
        .run()
        .map((id, label, kind, t, basis, ps, pe) =>
          SiteId(id) -> SiteTile(
            SiteTile.Id(id),
            SiteTile.Label(label),
            SiteTile.Kind(kind),
            t.map(x => SiteTile.SiteType(x)),
            SiteTile.Basis(basis),
            ps.map(y => Year(y)),
            pe.map(y => Year(y))
          )
        )
        .toMap

  /** A sites tile's cells. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def siteCells(
      c: TileCoord,
      grid: TileGrid,
      window: (DeepTime.SignedYear, DeepTime.SignedYear),
      siteType: Option[SiteType],
      modernFrom: DeepTime.SignedYear
  )(using DbCon): List[TileCell] =
    cells(siteCandidates(window, siteType, modernFrom, envelope(c)), c, grid)

  /** The sites tile `c` shows for `window` as candidates of [[items]] and [[gridCells]], an undated one the span shows
    * by its findings marked (it is among the undated sites too).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def datedSites(
      c: TileCoord,
      window: (DeepTime.SignedYear, DeepTime.SignedYear),
      siteType: Option[SiteType],
      modernFrom: DeepTime.SignedYear
  ): Frag =
    siteCandidates(window, siteType, modernFrom, envelope(c), markUndated = true)

  /** An events tile's cells, with the events of no known date `undatedWhere` selects
    * ([[EventSql.tileCandidatesWithUndated]]).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def eventCellsWithUndated(
      c: TileCoord,
      grid: TileGrid,
      where: Frag,
      undatedWhere: Frag
  )(using DbCon): List[TileCell] =
    cells(EventSql.tileCandidatesWithUndated(where, undatedWhere, envelope(c)), c, grid)

  /** An events tile's cells: the events `where` selects (over `events e` with its place `ep`, [[EventSql.listWhere]]).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def eventCells(c: TileCoord, grid: TileGrid, where: Frag)(using DbCon): List[TileCell] =
    cells(EventSql.tileCandidates(where, envelope(c)), c, grid)
