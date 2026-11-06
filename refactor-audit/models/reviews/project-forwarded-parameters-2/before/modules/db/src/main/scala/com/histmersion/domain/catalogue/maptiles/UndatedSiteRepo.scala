package com.histmersion.domain.catalogue.maptiles

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.catalogue.places.{PlaceSql, SiteTypesSql}

import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place

/** The sites with no known date, for the map when a viewer asks for them (V106): every site whose period is open on
  * both sides, in a tile's box ([[candidates]], over the partial index `places_site_undated`), and their counts by the
  * squares of the shallow tiles' grids, kept in `site_undated_cells` ([[kept]]) and rebuilt whole from `places`
  * ([[rebuild]]) when the `undated` group of `map_changes` has moved. The caller holds the connection or the
  * transaction.
  */
private[maptiles] object UndatedSiteRepo:

  /** The group of `map_changes` a write to an undated site moves (V106's trigger). */
  type Group = Group.T
  object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedSiteType = TypedSiteType.T
  object TypedSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesSiteType = CandidatesSiteType.T
  object CandidatesSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesExcept = CandidatesExcept.T
  object CandidatesExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsSiteType = ItemsSiteType.T
  object ItemsSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsExcept = ItemsExcept.T
  object ItemsExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountedSiteType = CountedSiteType.T
  object CountedSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeptSqlPerSide = KeptSqlPerSide.T
  object KeptSqlPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeptSqlSiteType = KeptSqlSiteType.T
  object KeptSqlSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeptPerSide = KeptPerSide.T
  object KeptPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeptSiteType = KeptSiteType.T
  object KeptSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionResult = VersionResult.T
  object VersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitSquarePart1 = UnitSquarePart1.T
  object UnitSquarePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitSquarePart2 = UnitSquarePart2.T
  object UnitSquarePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldV = HeldV.T
  object HeldV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebuildSqlPerSide = RebuildSqlPerSide.T
  object RebuildSqlPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebuildSqlMaxZoom = RebuildSqlMaxZoom.T
  object RebuildSqlMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebuildResult = RebuildResult.T
  object RebuildResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebuildVersion = RebuildVersion.T
  object RebuildVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebuildPerSide = RebuildPerSide.T
  object RebuildPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebuildMaxZoom = RebuildMaxZoom.T
  object RebuildMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Group: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.Group = "undated"

  /** A site with no known date that a map draws (a point, an informative name), over a `places` row `p` — word for word
    * the predicate of `places_site_undated`, then the label rule.
    */
  private val UndatedSiteSql =
    s"p.kind = '${Place.Kind.Site}' AND p.geom IS NOT NULL AND lower_inf(p.period) AND upper_inf(p.period)"

  private def typed(siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.TypedSiteType]): Sql =
    siteType
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .fold(Sql.empty)(t => Sql.of(s" AND ${SiteTypesSql.expr("p")} = ?", Param.S(t)))

  /** The undated sites in the box `envelope` (of type `siteType` when one is asked for), but those of `except`, as a
    * tile's candidates ([[TileRepo.cellsSql]]: no dates, each marked `ub`). Pure.
    */
  def candidates(siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.CandidatesSiteType], envelope: Sql, except: List[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.CandidatesExcept] = Nil): Sql =
    Sql.raw(
      "SELECT p.id AS key, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat, NULL::bigint AS ps, NULL::bigint AS pe, true AS ub" +
        " FROM places p CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(p.geom) = 'POINT' THEN p.geom" +
        s" ELSE ST_PointOnSurface(p.geom) END AS pt) q WHERE $UndatedSiteSql AND p.geom && "
    ) ++ envelope ++
      Sql.of(" AND p.label !~* ?", Param.S(PlaceSql.UninformativeLabel)) ++ typed(siteType) ++
      (if except.isEmpty then Sql.empty else Sql.of(" AND NOT (p.id = ANY(?::text[]))", Param.A(except)))

  /** Every undated site in tile `c`, each alone, but those of `except`. */
  def items(c: TileCoord, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.ItemsSiteType], except: List[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.ItemsExcept])(using DbCon): List[TileCell] =
    TileRepo.items(candidates(siteType, TileRepo.envelope(c), except), c)

  /** The undated sites in tile `c` counted by the grid's squares, read from the sites themselves. */
  def counted(c: TileCoord, grid: TileGrid, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.CountedSiteType])(using DbCon): List[GridCell] =
    TileRepo.gridCells(candidates(siteType, TileRepo.envelope(c)), c, grid)

  private given DbCodec[GridCell] = DbCodec.derived

  /** The kept counts of tile `c`'s squares (`perSide` a side), in the tile's units: of every type, or of `siteType`.
    * Pure.
    */
  def keptSql(c: TileCoord, perSide: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.KeptSqlPerSide, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.KeptSqlSiteType]): Sql =
    val side = c.side.toDouble
    val e = TileCoord.Extent
    val (gx0, gy0) = (c.x * perSide, c.y * perSide)
    Sql.raw(
      s"SELECT gx, gy, sum(n)::int, 0, (sum(sx) * $side - sum(n) * ${c.x}) * $e, (sum(sy) * $side - sum(n) * ${c.y}) * $e," +
        " 0::float8, 0::float8, NULL::float8, NULL::float8, min(w), min(s), max(e), max(nn), min(one)" +
        s" FROM site_undated_cells WHERE z = ${c.z} AND gx >= $gx0 AND gx < ${gx0 + perSide} AND gy >= $gy0 AND gy < ${gy0 + perSide}"
    ) ++
      siteType
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .fold(Sql.empty)(t => Sql.of(" AND site_type = ?", Param.S(t))) ++
      Sql.raw(" GROUP BY gx, gy")

  /** The kept counts of tile `c`'s squares ([[keptSql]]). */
  def kept(c: TileCoord, perSide: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.KeptPerSide, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.KeptSiteType])(using DbCon): List[GridCell] =
    keptSql(c, perSide, siteType).frag.query[GridCell].run().toList

  /** What the kept counts were built from: the `undated` group's version, the grid's squares a tile side, the deepest
    * zoom kept. None before the first build.
    */
  def built()(using DbCon): Option[UndatedBuild] =
    sql"SELECT version, per_side, max_zoom FROM site_undated_build"
      .query[(Long, Int, Int)]
      .run()
      .headOption
      .map((v, p, z) => UndatedBuild(v, p, z))

  /** The `undated` group's version now (0 before any write to an undated site). */
  def version()(using DbCon): com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.VersionResult =
    sql"SELECT coalesce(sum(weight), 0)::bigint FROM map_changes WHERE grp = $Group".query[Long].run().head

  /** The sites' places on Web Mercator's unit square, each axis held inside it (as [[TileRepo.unitsSql]]), over the
    * columns `lon` and `lat`.
    */
  private val unitSquare: (com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.UnitSquarePart1, com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.UnitSquarePart2) =
    val l = s"radians(greatest(${-TileCoord.MaxLat}, least(${TileCoord.MaxLat}, lat)))"
    def held(v: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.HeldV) = s"least(greatest($v, 0), 0.99999999999)"
    (held("(lon + 180) / 360.0"), held(s"(1 - ln(tan($l) + 1 / cos($l)) / pi()) / 2"))

  /** The statement that counts every undated site again, by type, into the squares of zooms 0 to `maxZoom` (`perSide` a
    * tile side). Pure.
    */
  def rebuildSql(perSide: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.RebuildSqlPerSide, maxZoom: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.RebuildSqlMaxZoom): Sql =
    val (mx, my) = unitSquare
    Sql.raw(
      "INSERT INTO site_undated_cells (z, gx, gy, site_type, n, sx, sy, w, s, e, nn, one)" +
        " SELECT z.z, floor(m.mx * ((1::bigint << z.z) * " + perSide + "))::bigint, floor(m.my * ((1::bigint << z.z) * " + perSide + "))::bigint," +
        " m.st, count(*)::int, sum(m.mx), sum(m.my), min(m.lon), min(m.lat), max(m.lon), max(m.lat), min(m.id)" +
        s" FROM (SELECT id, lon, lat, st, $mx AS mx, $my AS my FROM (SELECT p.id, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat," +
        s" coalesce(${SiteTypesSql.expr("p")}, '') AS st FROM places p CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(p.geom) = 'POINT'" +
        s" THEN p.geom ELSE ST_PointOnSurface(p.geom) END AS pt) q WHERE $UndatedSiteSql"
    ) ++
      Sql.of(" AND p.label !~* ?) s) m", Param.S(PlaceSql.UninformativeLabel)) ++
      Sql.of(" CROSS JOIN generate_series(0, ?) z(z) GROUP BY 1, 2, 3, 4", Param.I(maxZoom))

  /** Count every undated site again ([[rebuildSql]]) and record what from (`version` of the `undated` group, read
    * before), replacing the kept counts — in the caller's transaction, so readers see the old counts until it commits.
    * False when another rebuild holds the lock (it does the same work).
    */
  def rebuild(version: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.RebuildVersion, perSide: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.RebuildPerSide, maxZoom: com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.RebuildMaxZoom)(using DbCon): com.histmersion.domain.catalogue.maptiles.UndatedSiteRepo.RebuildResult =
    val locked =
      sql"SELECT pg_try_advisory_xact_lock(hashtext('histmersion.site_undated_cells'))".query[Boolean].run().head
    if locked then
      val _ = sql"DELETE FROM site_undated_cells".update.run()
      val _ = rebuildSql(perSide, maxZoom).frag.update.run()
      val _ =
        sql"""INSERT INTO site_undated_build (id, version, per_side, max_zoom, built_at) VALUES (true, $version, $perSide, $maxZoom, now())
                    ON CONFLICT (id) DO UPDATE SET version = EXCLUDED.version, per_side = EXCLUDED.per_side,
                    max_zoom = EXCLUDED.max_zoom, built_at = EXCLUDED.built_at""".update.run()
    locked
