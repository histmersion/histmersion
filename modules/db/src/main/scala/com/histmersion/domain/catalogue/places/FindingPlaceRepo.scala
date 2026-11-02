package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given

import com.histmersion.api.*
import com.histmersion.domain.*
import com.histmersion.util.database.RangeSql

import com.augustnagro.magnum.*
import com.histmersion.util.database.{Param, Sql}

/** The MATERIALISATION of `finding_places` — the only place that decides which findings belong to which places: the
  * whole table rebuilt, one source's findings or some places' findings placed again inside the caller's transaction,
  * and the placeholder points that stand for a named place. The library reaches it through [[FindingPlacement]].
  */
private[places] object FindingPlaceRepo:
  import PlaceRepo.*

  def rebuildFindingPlaces(toleranceKm: Double)(using DbCon): PlaceRebuildResult =
    // Lock in a FIXED order — `findings` first, then `finding_places` — before touching anything.
    // Without it the INSERT…SELECT can see (in its snapshot) a finding another transaction has
    // just deleted and then fail the foreign key; and two rebuilds, or a rebuild and a
    // `put_source`, could take the two tables in opposite orders and deadlock. A rebuild therefore
    // holds up source writes for its (few seconds') duration — it is an explicit admin action.
    // (`materialiseFor` touches the same two tables in the same order, for the same reason.)
    val _ = sql"LOCK TABLE findings IN SHARE MODE".update.run()
    val _ = sql"LOCK TABLE finding_places IN EXCLUSIVE MODE".update.run()
    val _ = sql"DELETE FROM finding_places".update.run()
    val (byGeometry, byName) = insertAssignments(None, toleranceKm)
    val links = sql"SELECT count(*)::int FROM finding_places".query[Int].run().head
    val located = sql"SELECT count(DISTINCT finding_id)::int FROM finding_places".query[Int].run().head
    val total = sql"SELECT count(*)::int FROM findings".query[Int].run().head
    val places = sql"SELECT count(*)::int FROM places".query[Int].run().head
    PlaceRebuildResult(links, byGeometry, byName, located, total - located, places)

  /** Re-materialise `finding_places` for ONE citekey, inside the CALLER's transaction.
    *
    * This exists because every source write used to `DELETE FROM findings`, and `finding_places` cascades off
    * `findings(id)` — so `patch_source {notes}` silently un-located every finding of that source until somebody
    * remembered to run `places rebuild`. The write path now upserts the findings and calls this, in the same
    * transaction, so the gazetteer assignment is never observable as missing. Same table order as
    * [[rebuildFindingPlaces]]: findings, then finding_places.
    */
  def materialiseFor(citekey: String, toleranceKm: Double)(using DbCon): Int =
    val _ = sql"""DELETE FROM finding_places fp
                  USING findings f
                  WHERE f.id = fp.finding_id AND f.citekey = $citekey""".update.run()
    val (byGeometry, byName) = insertAssignments(Some(citekey), toleranceKm)
    byGeometry + byName

  /** Re-materialise the `finding_places` rows of the given PLACES, inside the CALLER's transaction — after a place's
    * kind, point or identity changed (`lint --rule place-kind --fix`, `--rule place-grid-point --fix`): the same
    * inserts as a rebuild, restricted to those places, so a fix takes seconds instead of a whole rebuild's half minute
    * (live 2026-10-09). Same table order as [[rebuildFindingPlaces]]. Returns the rows written.
    */
  def materialisePlaces(placeIds: List[String], toleranceKm: Double)(using DbCon): Int =
    val ids = placeIds.distinct
    if ids.isEmpty then 0
    else
      val _ = sql"LOCK TABLE findings IN SHARE MODE".update.run()
      val _ = sql"DELETE FROM finding_places WHERE place_id = ANY($ids::text[])".update.run()
      val (byGeometry, byName) = insertAssignments(None, toleranceKm, Some(ids))
      byGeometry + byName

  /** A seed place whose POINT stands for something larger than a point (2026-10-09), over a `places` row aliased
    * `alias`: a region, polity or people with no boundary (`hasBoundary` false — all 1 617 Pleiades and 75 curated
    * regions), or any Pleiades point that is only the centre of an atlas grid square ([[LocationPrecision.GridCell]]).
    * Such a point is no evidence of nearness: by distance the 13 Regiones of Rome each took the same 59 findings,
    * Indo-European language spread among them, and the Athenian tribe Aiantis 108 — Achilles' name, the Presocratics,
    * steppe ancestry. A finding belongs to one only by an EXPLICIT match ([[insertAssignments]]). Imported places have
    * their own, stricter rule.
    */
  private[histmersion] def explicitOnlySql(alias: String): String =
    s"coalesce(ST_Dimension($alias.geom) = 0 AND (coalesce($alias.kind, '') NOT IN ('site', 'feature')" +
      s" OR coalesce($alias.location_precision = 'grid-cell', false)), false)"

  /** The placeholder minimum the assignment's geocoder pass ranks with ([[Geocode.isPlaceholder]]). It MUST equal
    * `places.geocode-placeholder-min` in `reference.conf`; `PlaceRepoSuite` asserts it.
    */
  val DefaultPlaceholderMin: Int = 4

  /** The share of a placeholder finding's named place an area must cover to take the finding. It MUST equal
    * `places.placeholder-area-share` in `reference.conf`; `PlaceholderPointSuite` asserts it.
    */
  val DefaultPlaceholderAreaShare: Double = 0.25

  /** A finding whose POINT is a PLACEHOLDER (2026-10-09), over a `findings` row aliased `f` — a point that stands for
    * "somewhere in this area", not "here":
    *   - the centre of an atlas grid square ([[LocationPrecision.gridCentre]]): 87 findings, every one a broad name
    *     ("Greece", "Aegean", "Korean Peninsula", "Pontic-Caspian steppe") at round half-degrees — 12 of them on
    *     "Greece" at 37.5 N 22.5 E, the point Pleiades gives six places;
    *   - the representative point (`ST_PointOnSurface`) of an area or line of the curated gazetteer that is no site —
    *     what the geocoder gives a region's, sea's or river's name;
    *   - the point of an explicit-only place ([[explicitOnlySql]]: a region, polity or people known only by a point).
    * Such a point is no evidence of nearness: by the 10 km tolerance the "Greece" findings had joined Tegea, Hysiai and
    * the Temple of Athena Alea, and by containment the Peloponnese. A placeholder finding belongs to what its NAME says
    * ([[insertAssignments]]). Within 1e-6° (0.1 m) of the point.
    */
  private[histmersion] def placeholderSql(f: String): String =
    // the two EXISTS read the partial indexes `places_rep_point` and `places_explicit_point` (V080),
    // whose predicates these are word for word
    s"(CASE WHEN $f.geom IS NULL OR GeometryType($f.geom) <> 'POINT' THEN false" +
      s" WHEN histmersion_grid_centre(ST_X($f.geom), ST_Y($f.geom)) THEN true" +
      s" ELSE EXISTS (SELECT 1 FROM places q WHERE ST_PointOnSurface(q.geom) && ST_Expand($f.geom, 1e-6)" +
      s" AND ST_Dimension(q.geom) > 0 AND q.kind <> 'site' AND NOT ${importedSql("q")}" +
      s" AND ST_DWithin(ST_PointOnSurface(q.geom), $f.geom, 1e-6))" +
      s" OR EXISTS (SELECT 1 FROM places q WHERE q.geom && ST_Expand($f.geom, 1e-6)" +
      s" AND NOT ${importedSql("q")} AND ${explicitOnlySql("q")} AND ST_DWithin(q.geom, $f.geom, 1e-6)) END)"

  final private case class Placeholder(
      id: String,
      name: Option[String],
      from: Option[Long],
      to: Option[Long],
      citekey: String,
      lon: Double,
      lat: Double
  ) derives DbCodec

  /** Every placeholder finding: id, place name, period, citekey and point (`lint --rule finding-placeholder`). */
  val placeholderFindingsSql: String =
    s"SELECT f.id, nullif(btrim(f.region_name), ''), ${RangeSql.cols("f.period")}, f.citekey," +
      s" ST_X(f.geom), ST_Y(f.geom) FROM findings f WHERE f.geom IS NOT NULL AND ${placeholderSql("f")}"

  /** The placeholder findings whose place name the geocoder does not resolve, or that have none — what
    * `lint --rule finding-placeholder` lists, as (citekey, line), by citekey and finding id.
    */
  def unresolvedPlaceholdersIn()(using DbCon): List[(String, String)] =
    val ph =
      (Sql.raw(placeholderFindingsSql) ++ Sql.raw(" ORDER BY f.citekey, f.id")).frag.query[Placeholder].run().toList
    val resolved = resolve(ph).map(_._1).toSet
    ph.filterNot(p => resolved.contains(p.id)).map { p =>
      val at = String.format(java.util.Locale.ROOT, "%.4f N %.4f E", p.lat, p.lon)
      p.citekey -> p.name.fold(s"${p.id}: no place name, placeholder point at $at")(n =>
        s"${p.id}: '$n' names no gazetteer place; placeholder point at $at"
      )
    }

  /** Finding id → the place its name resolves to, for the placeholders that have a name the geocoder resolves (as a
    * region). One resolution per (name, period).
    */
  private def resolve(ph: List[Placeholder])(using DbCon): List[(String, String)] =
    ph.flatMap(p => p.name.map(n => (n, p)))
      .groupBy((n, p) => (n, Geocode.periodOf(p.from, p.to)))
      .toList
      .flatMap { case ((name, at), fs) =>
        Geocoder
          .resolveIn(name, Geocode.KindHint.Region, at, DefaultPlaceholderMin)
          .toList
          .flatMap(r => fs.map((_, f) => f.id -> r.placeId))
      }

  /** The placeholder findings ([[placeholderSql]]) — of `citekey`, or all — and, for those whose place NAME the
    * geocoder resolves ([[Geocoder.resolveIn]], as a region), the place it resolves to: (every placeholder id, finding
    * id → place id).
    */
  private def placeholders(citekey: Option[String])(using DbCon): (List[String], List[(String, String)]) =
    val ph = (Sql.raw(placeholderFindingsSql) ++
      citekey.fold(Sql.raw(""))(ck => Sql.of(" AND f.citekey = ?", Param.S(ck)))).frag.query[Placeholder].run().toList
    (ph.map(_.id), resolve(ph))

  /** The inserts that define "a finding belongs to a place", shared by the full rebuild and the per-citekey
    * re-materialisation — one definition, so they cannot disagree about what the assignment means. `citekey = None`
    * means the whole corpus. Returns (byGeometry, byName).
    *
    *   - an AREA (a polygon or line) holds the findings it covers;
    *   - a SITE or FEATURE point holds the findings within the point tolerance;
    *   - an EXPLICIT-ONLY point ([[explicitOnlySql]]: a point-only region, polity or people, a grid square's centre)
    *     holds the findings whose place name the geocoder resolves to that place and — unless it is a grid square's
    *     centre, which many places share — the findings placed ON that very point; an imported point only the latter;
    *   - a finding with no point is matched by its name to any other place's name;
    *   - a finding whose point is a PLACEHOLDER ([[placeholderSql]]) takes no site or feature by the tolerance; when
    *     its name resolves, it belongs to that place and to the areas covering at least [[DefaultPlaceholderAreaShare]]
    *     of it (or its point, when it has no shape) — "Greece" to Greece, the Balkan Peninsula and Mycenaean Greece,
    *     not to the Peloponnese — and not to areas merely covering the point; when it does not resolve, areas still
    *     take it by containment (`lint --rule finding-placeholder` lists those for a person).
    */
  private def insertAssignments(citekey: Option[String], toleranceKm: Double, places: Option[List[String]] = None)(using
      DbCon
  ): (Int, Int) =
    // `places`: only the links of these places (a reclassified, merged or moved place, [[materialisePlaces]])
    val anyPlace = places.isEmpty
    val pids = places.getOrElse(Nil)
    val meters = toleranceKm.max(0.0) * 1000.0
    val degrees = toleranceKm.max(0.0) / 111.32
    // placeholder points: no site by distance; a resolved name decides the areas ([[byPlaceholder]])
    val (phIds, phNamed) = placeholders(citekey)
    val phResolved = phNamed.map(_._1).distinct
    // TWO statements, not one CASE: a CASE around the spatial predicate hides it from the planner
    // and turns the join into a 964 x 16 700 cross product (minutes). Split, each side is an
    // index-driven nested loop over `places_geom_gist` and the whole rebuild takes a few seconds.
    val byArea =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p ON ST_CoveredBy(f.geom, p.geom)
            WHERE f.geom IS NOT NULL AND p.geom IS NOT NULL AND ST_Dimension(p.geom) > 0
              AND NOT ${SqlLiteral(importedSql("p"))}
              AND ($citekey::text IS NULL OR f.citekey = $citekey::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND (f.period IS NULL OR f.period && p.period)
              AND NOT (f.id = ANY($phResolved::text[]))
            ON CONFLICT DO NOTHING""".update.run()
    val byPoint =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p
              ON p.geom && ST_Expand(f.geom,
                   $degrees / greatest(cos(radians(ST_Y(f.geom))), 0.01))
            WHERE f.geom IS NOT NULL AND p.geom IS NOT NULL AND ST_Dimension(p.geom) = 0
              AND NOT ${SqlLiteral(importedSql("p"))}
              AND NOT ${SqlLiteral(explicitOnlySql("p"))}
              AND ($citekey::text IS NULL OR f.citekey = $citekey::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND NOT (f.id = ANY($phIds::text[]))
              AND ST_DWithin(f.geom::geography, p.geom::geography, $meters)
              AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()
    // a GeoNames/Wikidata point, and an explicit-only seed point, get the findings the geocoder put ON
    // it (the point it chose for the finding's place name), never its neighbours': a 10 km circle round
    // a modern village is no evidence (live 2026-10-09: 192 k such rows), nor one round a tribe's
    // representative point. A grid square's centre not even that: Pleiades puts six places on
    // 37.5 N 22.5 E, and 12 findings a model placed on "Greece" at those round numbers had joined all
    // six — such a place takes a finding only by its name ([[byResolution]])
    // (the CASE: ST_X of a polygon the tiny box also hits is an error, and only a CASE orders the
    // evaluation; the plain coordinate test is 3x cheaper than ST_DWithin over 1.2 M imported points)
    val byGeocode =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p ON p.geom && ST_Expand(f.geom, 1e-7)
            WHERE f.geom IS NOT NULL
              AND CASE WHEN GeometryType(p.geom) = 'POINT'
                       THEN abs(ST_X(p.geom) - ST_X(f.geom)) < 1e-7 AND abs(ST_Y(p.geom) - ST_Y(f.geom)) < 1e-7
                       ELSE false END
              AND (${SqlLiteral(importedSql("p"))}
                   OR (${SqlLiteral(explicitOnlySql("p"))} AND NOT coalesce(p.location_precision = 'grid-cell', false)))
              AND ($citekey::text IS NULL OR f.citekey = $citekey::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()
    val byName =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT DISTINCT f.id, p.id
            FROM findings f
            JOIN place_names n ON lower(n.name) = lower(f.region_name)
            JOIN places p ON p.id = n.place_id
            WHERE f.geom IS NULL AND f.region_name IS NOT NULL AND f.region_name <> ''
              AND NOT ${SqlLiteral(importedSql("p"))}
              AND NOT ${SqlLiteral(explicitOnlySql("p"))}
              AND ($citekey::text IS NULL OR f.citekey = $citekey::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND (f.period IS NULL OR f.period && p.period)
              AND (f.period IS NULL OR f.period && n.period)
            ON CONFLICT DO NOTHING""".update.run()
    (
      byArea + byPoint + byGeocode + byPlaceholder(phNamed, anyPlace, pids),
      byName + byResolution(citekey, anyPlace, pids)
    )

  /** The links of the placeholder findings whose name resolved (`named`: finding id → place id): the place itself
    * (unless imported — a GeoNames/Wikidata point takes only the findings on it) and every curated AREA covering at
    * least [[DefaultPlaceholderAreaShare]] of that place's shape, or, when the place has no shape, its point — when the
    * periods overlap. The shares are computed once per place. Returns the rows inserted.
    */
  private def byPlaceholder(named: List[(String, String)], anyPlace: Boolean, pids: List[String])(using DbCon): Int =
    if named.isEmpty then 0
    else
      val fids = named.map(_._1)
      val rids = named.map(_._2)
      val share = DefaultPlaceholderAreaShare
      // Each named place's shape is made valid once (`rs`); an area is tested only when a finding naming
      // that place overlaps it in time, and is clipped to the place's box before the intersection — at
      // 3 200 candidate areas for "Italy", the overlay of whole Cliopatria empires was most of the cost.
      // (Nested CASEs: only a CASE fixes the order of evaluation.)
      sql"""WITH r AS (SELECT * FROM unnest($fids::text[], $rids::text[]) AS r(fid, rid)),
                 rs AS MATERIALIZED (
                   SELECT rp.id AS rid, rp.geom,
                          CASE WHEN ST_Dimension(rp.geom) = 2 THEN ST_MakeValid(rp.geom) END AS g
                   FROM (SELECT DISTINCT rid FROM r) x JOIN places rp ON rp.id = x.rid
                   WHERE rp.geom IS NOT NULL),
                 cover AS MATERIALIZED (
                   SELECT rs.rid, a.id AS aid FROM rs JOIN places a ON a.geom && rs.geom
                   WHERE ST_Dimension(a.geom) = 2 AND a.id <> rs.rid AND NOT ${SqlLiteral(importedSql("a"))}
                     AND ($anyPlace OR a.id = ANY($pids::text[]))
                     AND CASE WHEN NOT EXISTS (SELECT 1 FROM r JOIN findings f ON f.id = r.fid
                                               WHERE r.rid = rs.rid AND (f.period IS NULL OR f.period && a.period))
                              THEN false
                              WHEN rs.g IS NOT NULL
                              THEN ST_Area(ST_Intersection(ST_MakeValid(ST_ClipByBox2D(a.geom, rs.geom::box2d)), rs.g))
                                   >= $share * ST_Area(rs.g)
                              ELSE ST_Covers(a.geom, ST_PointOnSurface(rs.geom)) END
                   UNION ALL
                   SELECT rs.rid, rp.id FROM rs JOIN places rp ON rp.id = rs.rid
                   WHERE NOT ${SqlLiteral(importedSql("rp"))} AND ($anyPlace OR rp.id = ANY($pids::text[])))
            INSERT INTO finding_places (finding_id, place_id)
            SELECT DISTINCT r.fid, c.aid FROM r JOIN cover c ON c.rid = r.rid
            JOIN findings f ON f.id = r.fid JOIN places a ON a.id = c.aid
            WHERE f.period IS NULL OR f.period && a.period
            ON CONFLICT DO NOTHING""".update.run()

  final private case class Named(id: String, name: String, from: Option[Long], to: Option[Long]) derives DbCodec

  /** The explicit NAME match for explicit-only points: a finding whose place name the geocoder ([[Geocoder.resolveIn]])
    * resolves to such a place belongs to it — whatever point the finding was given by hand. Only the names one of whose
    * readings ([[Geocode.attempts]]) is the name key of an explicit-only place are resolved at all, so a rebuild
    * resolves a few hundred names, not every finding's. Returns the rows inserted.
    */
  private def byResolution(citekey: Option[String], anyPlace: Boolean, pids: List[String])(using DbCon): Int =
    val named = sql"""SELECT f.id, btrim(f.region_name), ${SqlLiteral(RangeSql.cols("f.period"))}
                      FROM findings f
                      WHERE btrim(coalesce(f.region_name, '')) <> ''
                        AND ($citekey::text IS NULL OR f.citekey = $citekey::text)"""
      .query[Named]
      .run()
      .toList
    if named.isEmpty then 0
    else
      val keysOf = named
        .map(_.name)
        .distinct
        .map(n => n -> Geocode.attempts(n).flatMap(a => a.key :: a.within.map(Geocode.key).toList).distinct)
        .toMap
      val allKeys = keysOf.values.flatten.toList.distinct
      val hits = sql"""SELECT DISTINCT k FROM unnest($allKeys::text[]) AS k
                       WHERE EXISTS (SELECT 1 FROM places p WHERE histmersion_place_key(p.label) = k
                                       AND ($anyPlace OR p.id = ANY($pids::text[]))
                                       AND NOT ${SqlLiteral(importedSql("p"))} AND ${SqlLiteral(explicitOnlySql("p"))})
                          OR EXISTS (SELECT 1 FROM place_names n JOIN places p ON p.id = n.place_id
                                     WHERE histmersion_place_key(n.name) = k
                                       AND ($anyPlace OR p.id = ANY($pids::text[]))
                                       AND NOT ${SqlLiteral(importedSql("p"))} AND ${SqlLiteral(
          explicitOnlySql("p")
        )})"""
        .query[String]
        .run()
        .toSet
      val wanted = named.filter(f => keysOf(f.name).exists(hits.contains))
      val resolved =
        wanted.groupBy(f => (f.name, Geocode.periodOf(f.from, f.to))).toList.flatMap { case ((name, at), fs) =>
          Geocoder
            .resolveIn(name, Geocode.KindHint.Unknown, at, DefaultPlaceholderMin)
            .toList
            .flatMap(r => fs.map(f => (f.id, r.placeId)))
        }
      if resolved.isEmpty then 0
      else
        val fids = resolved.map(_._1)
        val pids = resolved.map(_._2)
        sql"""INSERT INTO finding_places (finding_id, place_id)
              SELECT r.fid, r.pid FROM unnest($fids::text[], $pids::text[]) AS r(fid, pid)
              JOIN findings f ON f.id = r.fid JOIN places p ON p.id = r.pid
              WHERE NOT ${SqlLiteral(importedSql("p"))} AND ${SqlLiteral(explicitOnlySql("p"))}
                AND ($anyPlace OR p.id = ANY($pids::text[]))
                AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()
