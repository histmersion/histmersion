package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.domain.catalogue.places.{PlaceSql, SiteTypesSql}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.given
import com.histmersion.util.database.literal
import com.histmersion.util.database.Unused
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The sites with no known date, for the map when a viewer asks for them (V106): every site whose period is open on
  * both sides, in a tile's box ([[candidates]], over the partial index `places_site_undated`), and their counts by the
  * squares of the shallow tiles' grids, kept in `site_undated_cells` ([[kept]]) and rebuilt whole from `places`
  * ([[rebuild]]) when the `undated` group of `map_changes` has moved. The caller holds the connection or the
  * transaction.
  */
@atBoundary(BoundaryKind.Sql, "reads and writes the undated site tables and their row codecs")
private[maptiles] object UndatedSiteRepo:

  /** The group of `map_changes` a write to an undated site moves (V106's trigger). */
  type Group = Group.T
  object Group extends Newtype[String, Pure]
  export TileRepo.SiteType as CandidatesSiteType

  val UndatedGroup: Group = Group("undated")

  /** A site with no known date that a map draws (a point, an informative name), over a `places` row `p` — word for word
    * the predicate of `places_site_undated` (`'site'` is `Place.Kind.Site`, written in for the planner; TileRepoSuite
    * holds it), then the label rule.
    */
  private[maptiles] val UndatedSiteSql: Frag =
    sql"p.kind = 'site' AND p.geom IS NOT NULL AND lower_inf(p.period) AND upper_inf(p.period)"

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def typed(siteType: Option[TileRepo.SiteType]): Frag =
    siteType
      .map(_.value.trim.toLowerCase)
      .filter(_.nonEmpty)
      .fold(sql"")(t => sql" AND ${SiteTypesSql.expr(sql"p")} = $t")

  /** The undated sites in the box `envelope` (of type `siteType` when one is asked for), but those of `except`, as a
    * tile's candidates ([[TileRepo.cellsSql]]: no dates, each marked `ub`). Pure.
    */
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def candidates(
      siteType: Option[UndatedSiteRepo.CandidatesSiteType],
      envelope: Frag,
      except: List[TileRepo.SiteId] = Nil
  ): Frag =
    sql"SELECT p.id AS key, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat, NULL::bigint AS ps, NULL::bigint AS pe, true AS ub FROM places p CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(p.geom) = 'POINT' THEN p.geom ELSE ST_PointOnSurface(p.geom) END AS pt) q WHERE $UndatedSiteSql AND p.geom && $envelope AND p.label !~* ${PlaceSql.UninformativeLabel}${typed(siteType)}${
        if except.isEmpty then sql""
        else sql" AND NOT (p.id = ANY($except::text[]))"
      }"

  /** Every undated site in tile `c`, each alone, but those of `except`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def items(c: TileCoord, siteType: Option[TileRepo.SiteType], except: List[TileRepo.SiteId])(using
      DbCon
  ): List[TileCell] =
    TileRepo.items(candidates(siteType, TileRepo.envelope(c), except), c)

  /** The undated sites in tile `c` counted by the grid's squares, read from the sites themselves. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def counted(c: TileCoord, grid: TileGrid, siteType: Option[TileRepo.SiteType])(using DbCon): List[GridCell] =
    TileRepo.gridCells(candidates(siteType, TileRepo.envelope(c)), c, grid)

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[GridCell] = DbCodec.derived

  /** The kept counts of tile `c`'s squares (`perSide` a side), in the tile's units: of every type, or of `siteType`.
    * Pure.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def keptSql(c: TileCoord, perSide: UndatedBuild.PerSide, siteType: Option[TileRepo.SiteType]): Frag =
    val side = c.side.value.toDouble
    val e = TileCoord.Extent
    val (gx0, gy0) = (c.x.value * perSide.value, c.y.value * perSide.value)
    sql"SELECT gx, gy, sum(n)::int, 0, (sum(sx) * ${side.literal} - sum(n) * ${c.x.literal}) * ${e.literal}, (sum(sy) * ${side.literal} - sum(n) * ${c.y.literal}) * ${e.literal}, 0::float8, 0::float8, NULL::float8, NULL::float8, min(w), min(s), max(e), max(nn), min(one) FROM site_undated_cells WHERE z = ${c.z.literal} AND gx >= ${gx0.literal} AND gx < ${(gx0 + perSide.value).literal} AND gy >= ${gy0.literal} AND gy < ${(gy0 + perSide.value).literal}${siteType
        .map(_.value.trim.toLowerCase)
        .filter(_.nonEmpty)
        .fold(sql"")(t => sql" AND site_type = $t")} GROUP BY gx, gy"

  /** The kept counts of tile `c`'s squares ([[keptSql]]). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def kept(c: TileCoord, perSide: UndatedBuild.PerSide, siteType: Option[TileRepo.SiteType])(using
      DbCon
  ): List[GridCell] =
    keptSql(c, perSide, siteType).query[GridCell].run().toList

  /** What the kept counts were built from: the `undated` group's version, the grid's squares a tile side, the deepest
    * zoom kept. None before the first build.
    */
  def built()(using DbCon): Option[UndatedBuild] =
    sql"SELECT version, per_side, max_zoom FROM site_undated_build"
      .query[(UndatedBuild.Version, UndatedBuild.PerSide, UndatedBuild.MaxZoom)]
      .run()
      .headOption
      .map((v, p, z) => UndatedBuild(v, p, z))

  /** The `undated` group's version now (0 before any write to an undated site). */
  def version()(using DbCon): UndatedBuild.Version =
    sql"SELECT coalesce(sum(weight), 0)::bigint FROM map_changes WHERE grp = $UndatedGroup"
      .query[UndatedBuild.Version]
      .run()
      .head

  /** The sites' places on Web Mercator's unit square, each axis held inside it (as [[TileRepo.unitsSql]]), over the
    * columns `lon` and `lat`.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private val unitSquare: (Frag, Frag) =
    val l = sql"radians(greatest(${(-TileCoord.MaxLat.value).literal}, least(${TileCoord.MaxLat.literal}, lat)))"
    def held(v: Frag) = sql"least(greatest($v, 0), 0.99999999999)"
    (held(sql"(lon + 180) / 360.0"), held(sql"(1 - ln(tan($l) + 1 / cos($l)) / pi()) / 2"))

  /** The statement that counts every undated site again, by type, into the squares of zooms 0 to `maxZoom` (`perSide` a
    * tile side). Pure.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def rebuildSql(perSide: UndatedBuild.PerSide, maxZoom: UndatedBuild.MaxZoom): Frag =
    val (mx, my) = unitSquare
    sql"INSERT INTO site_undated_cells (z, gx, gy, site_type, n, sx, sy, w, s, e, nn, one) SELECT z.z, floor(m.mx * ((1::bigint << z.z) * ${perSide.literal}))::bigint, floor(m.my * ((1::bigint << z.z) * ${perSide.literal}))::bigint, m.st, count(*)::int, sum(m.mx), sum(m.my), min(m.lon), min(m.lat), max(m.lon), max(m.lat), min(m.id) FROM (SELECT id, lon, lat, st, $mx AS mx, $my AS my FROM (SELECT p.id, ST_X(q.pt) AS lon, ST_Y(q.pt) AS lat, coalesce(${SiteTypesSql.expr(sql"p")}, '') AS st FROM places p CROSS JOIN LATERAL (SELECT CASE WHEN GeometryType(p.geom) = 'POINT' THEN p.geom ELSE ST_PointOnSurface(p.geom) END AS pt) q WHERE $UndatedSiteSql AND p.label !~* ${PlaceSql.UninformativeLabel}) s) m CROSS JOIN generate_series(0, $maxZoom) z(z) GROUP BY 1, 2, 3, 4"

  /** Count every undated site again ([[rebuildSql]]) and record what from (`version` of the `undated` group, read
    * before), replacing the kept counts — in the caller's transaction, so readers see the old counts until it commits.
    * False when another rebuild holds the lock (it does the same work).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def rebuild(version: UndatedBuild.Version, perSide: UndatedBuild.PerSide, maxZoom: UndatedBuild.MaxZoom)(using
      DbCon
  ): Boolean =
    val locked =
      sql"SELECT pg_try_advisory_xact_lock(hashtext('histmersion.site_undated_cells'))".query[Boolean].run().head
    if locked then
      Unused(sql"DELETE FROM site_undated_cells".update.run(), Unused.Why.Idempotent)
      Unused(rebuildSql(perSide, maxZoom).update.run(), Unused.Why.Idempotent)
      val _ =
        sql"""INSERT INTO site_undated_build (id, version, per_side, max_zoom, built_at) VALUES (true, $version, $perSide, $maxZoom, now())
                    ON CONFLICT (id) DO UPDATE SET version = EXCLUDED.version, per_side = EXCLUDED.per_side,
                    max_zoom = EXCLUDED.max_zoom, built_at = EXCLUDED.built_at""".update.run()
    locked
