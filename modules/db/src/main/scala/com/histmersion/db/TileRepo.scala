package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.domain.*
import com.histmersion.domain.maptiles.GridCell
import com.histmersion.values.PlaceKind

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** [[SiteTypes.of]] in SQL, over a `places` row aliased `a` (a site or a feature): the stored type, else the Pleiades
  * feature types (the most specific by [[SiteTypes]]' preference), else the GeoNames feature code, else the Wikidata
  * item's own P31 classes (`properties.p31`, the most specific), else the Wikidata import's class — generated from the
  * same tables, so the map's site-type filter and the type a site is drawn with cannot disagree. `SiteTypesSqlSuite`
  * holds it to [[SiteTypes.of]].
  */
object SiteTypesSql:

  private def quote(s: String): String = "'" + s.replace("'", "''") + "'"
  private def list(xs: Iterable[String]): String = xs.map(quote).mkString(", ")

  /** The preference [[SiteTypes]] picks a type by when a place lists several. */
  private val preference: List[String] = SiteTypes.preference

  def expr(a: String): String =
    val stored = s"CASE WHEN $a.site_type IN (${list(SiteTypes.all.map(_.id))}) THEN $a.site_type END"
    // the feature types as a list: lower case, each without its `-2` variant suffix
    val types = s"regexp_split_to_array(btrim(regexp_replace(lower($a.properties->>'feature_types'), " +
      "'-[0-9]+\\s*(,|$)', '\\1', 'g')), '\\s*,\\s*')"
    val byType = SiteTypes.pleiadesTypes.toList.groupMap(_._2)(_._1)
    val pleiades = preference
      .flatMap(t => byType.get(t).map(ks => s"WHEN ft.v && ARRAY[${list(ks.sorted)}] THEN ${quote(t)}"))
      .mkString(s"(SELECT CASE ", " ", s" END FROM (SELECT $types AS v) ft)")
    val fcode = s"upper(btrim($a.properties->>'fcode'))"
    val geonames = SiteTypes.geonamesCodes.toList.sorted
      .map((c, t) => s"WHEN ${quote(c)} THEN ${quote(t)}")
      .mkString(s"CASE $fcode ", " ", s" ELSE CASE WHEN $fcode LIKE 'P.%' THEN ${quote(SiteTypes.Settlement)} END END")
    // the item's own P31 classes: a JSON array in `properties.p31` (anything else counts as none)
    val p31 = s"ARRAY(SELECT btrim(x) FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof($a.properties->'p31') = " +
      "'array' THEN " + s"$a.properties->'p31' ELSE '[]'::jsonb END) x)"
    val byClass = SiteTypes.wikidataClasses.toList.groupMap(_._2)(_._1)
    val own = preference
      .flatMap(t => byClass.get(t).map(ks => s"WHEN w.v && ARRAY[${list(ks.sorted)}] THEN ${quote(t)}"))
      .mkString(s"(SELECT CASE ", " ", s" END FROM (SELECT $p31 AS v) w)")
    val wikidata = SiteTypes.wikidataClasses.toList.sorted
      .map((c, t) => s"WHEN ${quote(c)} THEN ${quote(t)}")
      .mkString(s"CASE btrim($a.properties->>'class') ", " ", " END")
    s"(CASE WHEN $a.kind IN (${quote(PlaceKind.Site)}, ${quote(PlaceKind.Feature)}) THEN " +
      s"coalesce($stored, $pleiades, $geonames, $own, $wikidata) END)"

/** The map's point layers cut into vector tiles (`GET /api/tiles/…`, [[TileGrid]]): each layer's selection in a tile's
  * box ([[siteCandidates]], [[EventRepo.tileCandidates]], [[MediaRepo.tileCandidates]] — every item, never a limit),
  * placed on the tile ([[cellsSql]]) and counted by cell where there are more than the grid keeps alone; then the
  * records of the items drawn alone. And the versions of what the layers read ([[versions]], V105).
  */
object TileRepo:

  /** What a layer's tiles read, by the groups of V105's `map_changes`. */
  val Groups: Map[TileLayer, List[String]] = Map(
    // an undated site is shown by its findings, or by an event, dated then
    TileLayer.Sites -> List("places", "corpus", "events"),
    // under a filter of sources, an event is one of theirs; its anchor and place's name are its place's
    TileLayer.Events -> List("events", "places", "corpus"),
    TileLayer.Media -> List("media")
  )

  /** Each group's version: the sum of its changes' weights (V105). */
  def versions(ds: DataSource): Map[String, Long] =
    connect(ds):
      sql"SELECT grp, sum(weight)::bigint, count(*)::int FROM map_changes GROUP BY grp"
        .query[(String, Long, Int)]
        .run()
        .map((g, v, _) => g -> v)
        .toMap

  /** How many change rows each group holds (a group of many is folded by [[compact]]). */
  def changeRows(ds: DataSource): Map[String, Int] =
    connect(ds):
      sql"SELECT grp, count(*)::int FROM map_changes GROUP BY grp".query[(String, Int)].run().toMap

  /** Fold `grp`'s change rows into one of their summed weight — its version unchanged. One fold at a time (an advisory
    * lock; a second caller does nothing).
    */
  def compact(ds: DataSource, grp: String): Unit =
    transact(ds):
      val locked = sql"SELECT pg_try_advisory_xact_lock(hashtext('histmersion.map_changes'))".query[Boolean].run().head
      if locked then
        val _ = sql"""WITH d AS (DELETE FROM map_changes WHERE grp = $grp RETURNING weight)
                      INSERT INTO map_changes (grp, weight) SELECT $grp, sum(weight) FROM d HAVING count(*) > 0""".update
          .run()

  /** The tile's box as a PostGIS envelope; a tile at the grid's top or bottom edge reaches the pole, so a place beyond
    * Web Mercator's last latitude is drawn on the edge rather than nowhere.
    */
  private[db] def envelope(c: TileCoord): Sql =
    val (w, s, e, n) = c.bounds
    val south = if c.y == c.side - 1 then -90.0 else s
    val north = if c.y == 0 then 90.0 else n
    Sql.of("ST_MakeEnvelope(?, ?, ?, ?, 4326)", Param.D(w), Param.D(south), Param.D(e), Param.D(north))

  /** Web Mercator in SQL over the columns `lon` and `lat`: [[TileCoord.mercator]], each axis held inside the unit
    * square, then in units of tile `c` — `(x, y)`. `TileRepoSuite` holds it to the Scala.
    */
  private[db] def unitsSql(c: TileCoord): (String, String) =
    val l = s"radians(greatest(${-TileCoord.MaxLat}, least(${TileCoord.MaxLat}, lat)))"
    def held(v: String) = s"least(greatest($v, 0), 0.99999999999)"
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
  private[db] def cellsSql(candidates: Sql, c: TileCoord, maxPoints: Int, cellUnits: Int): Sql =
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
  private[db] def gridCellsSql(candidates: Sql, c: TileCoord, cellUnits: Int): Sql =
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
  def cells(ds: DataSource, candidates: Sql, c: TileCoord, grid: TileGrid): List[TileCell] =
    connect(ds):
      cellsSql(candidates, c, grid.pointsAt(c), grid.cellUnits).frag.query[TileCell].run().toList

  /** Every item of `candidates` in tile `c`, each alone. */
  def items(candidates: Sql, c: TileCoord)(using DbCon): List[TileCell] =
    cellsSql(candidates, c, Int.MaxValue, TileCoord.Extent).frag.query[TileCell].run().toList

  /** The items of `candidates` in tile `c` counted by the grid's squares ([[gridCellsSql]]). */
  def gridCells(candidates: Sql, c: TileCoord, grid: TileGrid)(using DbCon): List[GridCell] =
    gridCellsSql(candidates, c, grid.cellUnits).frag.query[GridCell].run().toList

  // ── the sites ───────────────────────────────────────────────────────────────────────────────

  /** The sites a time view of `window` shows ([[PlaceRepo.inWindowSql]]: a dated site overlapping it, a present-day one
    * from `modern-from`, an undated one by findings or events dated in it — never an undated import), of type
    * `siteType` when one is asked for, in the tile's box `envelope`: every one the gazetteer has, imported from
    * Wikidata as much as curated (the places layer draws an imported place only once a finding names it; a site is no
    * less a site for being in no source yet). Over `places_site_map` (V105), whose predicate is stated word for word.
    */
  private[db] def siteCandidates(
      window: (Long, Long),
      siteType: Option[String],
      modernFrom: Long,
      envelope: Sql,
      markUndated: Boolean = false
  ): Sql =
    val tw = Some(window)
    // `ub`: a site of no known date the span shows by its findings or events — counted among the undated
    // sites too when they are asked for ([[UndatedSiteRepo]])
    val ub = if markUndated then "(lower_inf(p.period) AND upper_inf(p.period))" else "false"
    Sql.raw(
      "SELECT p.id AS key, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat," +
        s" ${PeriodSql.start("p.period")} AS ps, ${PeriodSql.end("p.period")} AS pe, $ub AS ub" +
        " FROM places p CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(p.geom) = 'POINT' THEN p.geom" +
        " ELSE ST_PointOnSurface(p.geom) END AS pt) q" +
        s" WHERE p.kind = '${PlaceKind.Site}' AND p.geom IS NOT NULL" +
        s" AND NOT (coalesce(p.import_source, '') IN ${PlaceRepo.ImportedList} AND lower_inf(p.period) AND upper_inf(p.period))" +
        " AND p.geom && "
    ) ++ envelope ++
      Sql.of(" AND p.label !~* ?", Param.S(PlaceRepo.UninformativeLabel)) ++
      Sql.raw(s" AND ${PlaceRepo.inWindowSql("p", tw, Some(PlaceKind.Site), Some(modernFrom))}") ++
      siteType
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .fold(Sql.empty)(t => Sql.of(s" AND ${SiteTypesSql.expr("p")} = ?", Param.S(t)))

  /** The sites of `ids` as a sites tile draws one, by id. */
  def sitesOf(ds: DataSource, ids: List[String]): Map[String, SiteTile] =
    if ids.isEmpty then Map.empty
    else
      connect(ds):
        Sql
          .of(
            s"SELECT p.id, p.label, p.kind, ${SiteTypesSql.expr("p")}, ${PlaceRepo.timeBasisSql("p")}," +
              s" ${PeriodSql.start("p.period")}, ${PeriodSql.end("p.period")} FROM places p WHERE p.id = ANY(?)",
            Param.A(ids)
          )
          .frag
          .query[(String, String, String, Option[String], String, Option[Long], Option[Long])]
          .run()
          .map((id, label, kind, t, basis, ps, pe) => id -> SiteTile(id, label, kind, t, basis, ps, pe))
          .toMap

  /** A sites tile's cells. */
  def siteCells(
      ds: DataSource,
      c: TileCoord,
      grid: TileGrid,
      window: (Long, Long),
      siteType: Option[String],
      modernFrom: Long
  ): List[TileCell] =
    cells(ds, siteCandidates(window, siteType, modernFrom, envelope(c)), c, grid)

  /** The sites tile `c` shows for `window` as candidates of [[items]] and [[gridCells]], an undated one the span shows
    * by its findings marked (it is among the undated sites too).
    */
  def datedSites(c: TileCoord, window: (Long, Long), siteType: Option[String], modernFrom: Long): Sql =
    siteCandidates(window, siteType, modernFrom, envelope(c), markUndated = true)

  /** An events tile's cells, with the events of no known date `undatedWhere` selects
    * ([[EventRepo.tileCandidatesWithUndated]]).
    */
  def eventCellsWithUndated(
      ds: DataSource,
      c: TileCoord,
      grid: TileGrid,
      where: Sql,
      undatedWhere: Sql
  ): List[TileCell] =
    cells(ds, EventRepo.tileCandidatesWithUndated(where, undatedWhere, envelope(c)), c, grid)

  /** An events tile's cells: the events `where` selects (over `events e` with its place `ep`, [[EventRepo.listWhere]]).
    */
  def eventCells(ds: DataSource, c: TileCoord, grid: TileGrid, where: Sql): List[TileCell] =
    cells(ds, EventRepo.tileCandidates(where, envelope(c)), c, grid)

  /** A media tile's cells. */
  def mediaCells(
      ds: DataSource,
      c: TileCoord,
      grid: TileGrid,
      kind: Option[String],
      provider: Option[String],
      license: Option[String],
      window: Option[(Long, Long)]
  ): List[TileCell] =
    cells(ds, MediaRepo.tileCandidates(kind, provider, license, window, envelope(c)), c, grid)
