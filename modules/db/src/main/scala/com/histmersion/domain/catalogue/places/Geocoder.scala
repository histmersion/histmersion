package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given

import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Geocode.{Attempt, Candidate, KindHint, Move, Placement, Resolution}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import javax.sql.DataSource
import com.histmersion.util.database.Sql
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Finding

/** Place NAME → gazetteer point (#981): the ONE geocoder. It fetches the places a name can mean and hands them to
  * [[Geocode]] (core), which reads the name ([[Geocode.attempts]]) and ranks the candidates ([[Geocode.rank]]) — the
  * rules are documented there. Names are compared by the key `histmersion_place_key` (V067), index-backed on both
  * `places.label` and `place_names.name`.
  *
  * Used by `enrich-source` for every finding's place and by the `finding-geocode` lint repair.
  */
private[places] object Geocoder:

  private given DbCodec[Candidate] = DbCodec.derived

  /** The places whose label or one of whose names has `key`, with a geometry, optionally only those intersecting place
    * `within`. One row per place AND span of the matched name: `byLabel` when the LABEL matched; `from`/`to` are when
    * the place was called that — the place's own period, narrowed by a DATED name's (#1064: "Mikoyan" names its village
    * only 1935–1956), so the time rule of [[Geocode.rank]] reads the name's dates too. `tier` is who brought the place
    * in ([[Geocode.tierOf]]).
    */
  def candidates(key: Geocode.Key, within: Option[Place.Id])(using DbCon): List[Candidate] =
    if key.value.isEmpty then Nil
    else
      sql"""WITH hit AS (
              SELECT id, true AS by_label, NULL::bigint AS nlo, NULL::bigint AS nhi
              FROM places WHERE histmersion_place_key(label) = $key
              UNION ALL
              SELECT place_id, false, ${SqlLiteral(RangeSql.cols("period"))}
              FROM place_names WHERE histmersion_place_key(name) = $key)
            SELECT p.id, p.label, p.kind, bool_or(h.by_label),
                   greatest(${SqlLiteral(RangeSql.start("p.period"))}, h.nlo), least(${SqlLiteral(
          RangeSql.end("p.period")
        )}, h.nhi),
                   ST_Dimension(p.geom), ST_Area(p.geom),
                   CASE WHEN ST_Dimension(p.geom) = 0
                        THEN (SELECT count(*)::int FROM places q WHERE q.geom && p.geom AND ST_Equals(q.geom, p.geom))
                        ELSE 1 END,
                   ST_X(ST_PointOnSurface(p.geom)), ST_Y(ST_PointOnSurface(p.geom)),
                   ${SqlLiteral(tierSql)},
                   coalesce(p.location_precision = 'grid-cell', false)
            FROM hit h JOIN places p ON p.id = h.id
            WHERE p.geom IS NOT NULL
              AND ($within::text IS NULL
                   OR ST_Intersects(p.geom, (SELECT w.geom FROM places w WHERE w.id = $within::text)))
            GROUP BY p.id, h.nlo, h.nhi""".query[Candidate].run().toList

  /** SQL twin of [[Geocode.tierOf]] over a `places` row aliased `p`. */
  private[histmersion] val tierSql: String =
    "CASE WHEN p.import_source IS NULL THEN 0 WHEN p.import_source LIKE 'lpf:%' THEN 1" +
      " WHEN p.import_source = 'wikidata' THEN 2 WHEN p.import_source = 'tgn' THEN 3" +
      " WHEN p.import_source = 'geonames' THEN 4 ELSE 5 END"

  /** Where `name` is, for a finding of period `at` about a place of kind `hint`. `None` = no gazetteer place by that
    * name (nothing is ever guessed).
    */
  def resolve(
      name: Attempt.Name,
      hint: KindHint,
      at: Option[Period],
      placeholderMin: GeocodeSettings.PlaceholderMin
  )(using DbCon): Option[Resolution] =
    resolveIn(name, hint, at, placeholderMin)

  def resolveIn(name: Attempt.Name, hint: KindHint, at: Option[Period], placeholderMin: GeocodeSettings.PlaceholderMin)(
      using DbCon
  ): Option[Resolution] =
    Geocode
      .attempts(name)
      .iterator
      .flatMap(a => attempt(Resolution.Asked(name.value.trim), a, hint, at, placeholderMin))
      .nextOption()

  private def attempt(
      asked: Resolution.Asked,
      a: Attempt,
      hint: KindHint,
      at: Option[Period],
      min: GeocodeSettings.PlaceholderMin
  )(using
      DbCon
  ): Option[Resolution] =
    // the place a qualifier names ("…, Croatia"), or that stands in for an unknown one, is an AREA
    val h = if a.approx.exists(_.isInstanceOf[Geocode.Approximation.OnlyWithin]) then KindHint.Region else hint
    chosen(asked, a, h, at, min).map(toward(_, a))

  /** A dropped compass qualifier ("Eastern Mediterranean") moves the point into that part of the shape
    * ([[Geocode.compassBox]]): the point on the surface of the shape cut to that part of its bounding box. A point
    * place, or a cut that leaves nothing, keeps the point it had.
    */
  private def toward(r: Resolution, a: Attempt)(using DbCon): Resolution =
    a.approx.collect { case Geocode.Approximation.Qualifier(d) => d }.flatMap(Geocode.compassBox) match
      case None                       => r
      case Some((fx0, fy0, fx1, fy1)) =>
        sql"""WITH g AS (
                SELECT geom, ST_XMin(geom) AS x0, ST_YMin(geom) AS y0, ST_XMax(geom) AS x1, ST_YMax(geom) AS y1
                FROM places WHERE id = ${r.placeId} AND ST_Dimension(geom) = 2
              ), part AS (
                SELECT ST_PointOnSurface(ST_CollectionExtract(ST_Intersection(ST_MakeValid(geom), ST_MakeEnvelope(
                  x0 + (x1 - x0) * $fx0, y0 + (y1 - y0) * $fy0, x0 + (x1 - x0) * $fx1, y0 + (y1 - y0) * $fy1, 4326)), 3)) AS pt
                FROM g)
              SELECT ST_X(pt), ST_Y(pt) FROM part WHERE pt IS NOT NULL AND NOT ST_IsEmpty(pt)"""
          .query[(Double, Double)]
          .run()
          .headOption
          .fold(r)((lon, lat) => r.copy(lon = Longitude(lon), lat = Latitude(lat)))

  private def chosen(
      asked: Resolution.Asked,
      a: Attempt,
      h: KindHint,
      at: Option[Period],
      min: GeocodeSettings.PlaceholderMin
  )(using
      DbCon
  ): Option[Resolution] =
    val pm = Geocode.Placeholder.Min(min.value)
    a.within match
      case None     => Geocode.choose(asked, a, Geocode.rank(candidates(a.key, None), h, at, pm), pm)
      case Some(in) =>
        Geocode
          .rank(candidates(Geocode.key(in.value), None).filter(_.dimension.value == 2), KindHint.Region, at, pm)
          .headOption
          .flatMap(w => Geocode.choose(asked, a, Geocode.rank(candidates(a.key, Some(w.id)), h, at, pm), pm))

  // ── the repair ─────────────────────────────────────────────────────────────────────────────────

  final private case class Placed(
      id: Placed.Id,
      citekey: Citekey,
      name: Placed.Name,
      region: Option[Placed.Region],
      lon: com.histmersion.Longitude,
      lat: com.histmersion.Latitude,
      from: Option[Placed.From],
      to: Option[Placed.To]
  ) derives DbCodec

  object Placed:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Region = Region.T
    object Region extends Newtype[String, Pure]
    type From = From.T
    object From extends Newtype[Long, Pure]
    type To = To.T
    object To extends Newtype[Long, Pure]

  /** The findings whose stored point is EXACTLY the one the pre-#981 geocoder chose for their place name — the only
    * points the repair may move, so a hand-placed coordinate is never touched.
    *
    * The name is the one the model gave (from the run's `findings-<n>` checkpoint, matched by claim): the old pipeline
    * stored the chosen place's LABEL as `region_name`, so "Albania" became "[Hulwan]/Valashfarr/Beth
    * Lashpar/Chala/Albania". Without a checkpoint it is `region_name`.
    *
    * `legacy` is the old rule, verbatim: exact `lower()` match on a label or any name, then site < feature < polity <
    * region, then the smaller area, then the id; the point on its surface.
    */
  val legacySql: String =
    s"""WITH asked AS (
         SELECT DISTINCT ON (f.id) f.id, btrim(p->>'place') AS place
         FROM findings f
         JOIN jobs j ON j.kind = 'agent' AND j.pipeline = 'enrich-source' AND j.target_id = f.citekey
         JOIN agent_steps s ON s.job_id = j.id AND s.status = 'done' AND s.name LIKE 'findings-%'
         CROSS JOIN LATERAL jsonb_array_elements(s.output->'findings') p
         WHERE f.geom IS NOT NULL AND p->>'claim' = f.claim AND btrim(coalesce(p->>'place', '')) <> ''
         ORDER BY f.id, j.created_at DESC
       ), named AS (
         SELECT f.id, f.citekey, coalesce(a.place, btrim(f.region_name)) AS name, f.region_name,
                ST_X(f.geom) AS lon, ST_Y(f.geom) AS lat, ${RangeSql.cols("f.period", "p0", "p1")}
         FROM findings f LEFT JOIN asked a ON a.id = f.id
         WHERE f.geom IS NOT NULL AND coalesce(a.place, btrim(f.region_name), '') <> ''
       ), names AS (
         SELECT DISTINCT name FROM named
       ), hits AS (
         SELECT n.name, p.id FROM names n JOIN places p ON lower(p.label) = lower(n.name)
         UNION
         SELECT n.name, m.place_id FROM names n JOIN place_names m ON lower(m.name) = lower(n.name)
       ), legacy AS (
         SELECT DISTINCT ON (h.name) h.name, ST_PointOnSurface(p.geom) AS pt
         FROM hits h JOIN places p ON p.id = h.id
         WHERE p.geom IS NOT NULL
         ORDER BY h.name, CASE p.kind WHEN 'site' THEN 0 WHEN 'feature' THEN 1 WHEN 'polity' THEN 2 ELSE 3 END,
                  ST_Area(p.geom), p.id
       )
       SELECT n.id, n.citekey, n.name, n.region_name, n.lon, n.lat, n.p0, n.p1
       FROM named n JOIN legacy l ON l.name = n.name
       WHERE abs(ST_X(l.pt) - n.lon) < 1e-7 AND abs(ST_Y(l.pt) - n.lat) < 1e-7
       ORDER BY n.citekey, n.id"""

  /** The moves the repair would make: every old-geocoder point that the current ranking puts more than `repairKm` away.
    * Read-only.
    */
  def movesIn(s: GeocodeSettings)(using DbCon): List[Move] =
    Sql
      .raw(Sql.RawText(legacySql))
      .frag
      .query[Placed]
      .run()
      .toList
      .flatMap { p =>
        resolveIn(
          Attempt.Name(p.name.value),
          KindHint.Unknown,
          Geocode.periodOf(p.from.map(f => Year(f.value)), p.to.map(t => Year(t.value))),
          s.placeholderMin
        )
          .map(r =>
            Move(
              Finding.Id(p.id.value),
              com.histmersion.api.library.Citekey(p.citekey.value),
              Move.Name(p.name.value),
              p.region.map(g => Move.Name(g.value)),
              Move.FromLon(p.lon.value),
              Move.FromLat(p.lat.value),
              r
            )
          )
      }
      .filter(m => Geocode.worthMoving(m, Geocode.Move.MinKm(s.repairKm.value)))

  def moves(s: GeocodeSettings)(using DbCon): List[Move] = movesIn(s)

  /** Apply the moves: each finding gets the new point and the name that was asked as its region, but only while its
    * point is still the one the move was computed from (compare-and-set — a finding edited since is left alone). The
    * touched sources' `updated_at` moves and their `finding_places` are re-materialised, in the same transaction.
    * Returns the findings moved.
    */
  def apply(ms: List[Move], toleranceKm: FindingPlacement.Implementation.ToleranceKm)(using DbCon): RowCount =
    if ms.isEmpty then RowCount(0)
    else
      val moved = ms.filter { m =>
        sql"""UPDATE findings SET geom = ST_SetSRID(ST_MakePoint(${m.to.lon}, ${m.to.lat}), 4326),
                                  region_name = ${m.asked}
              WHERE id = ${m.findingId}
                AND abs(ST_X(geom) - ${m.fromLon}) < 1e-7 AND abs(ST_Y(geom) - ${m.fromLat}) < 1e-7""".update
          .run() > 0
      }
      moved.map(_.citekey).distinct.flatMap(c => Citekey.option(c.value)).foreach { ck =>
        val _ = sql"UPDATE sources SET updated_at = now() WHERE citekey = $ck".update.run()
        val _ = FindingPlaceRepo.materialiseFor(ck, toleranceKm)
      }
      RowCount(moved.size)

  // ── the fill (#987) ─────────────────────────────────────────────────────────────────────────────

  final private case class Unplaced(
      id: Unplaced.Id,
      citekey: Citekey,
      region: Unplaced.Region,
      from: Option[Unplaced.From],
      to: Option[Unplaced.To]
  ) derives DbCodec

  object Unplaced:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Region = Region.T
    object Region extends Newtype[String, Pure]
    type From = From.T
    object From extends Newtype[Long, Pure]
    type To = To.T
    object To extends Newtype[Long, Pure]

  /** The findings with a place name and no point — what `finding-unplaced` reads. */
  val unplacedSql: String =
    s"""SELECT f.id, f.citekey, btrim(f.region_name), ${RangeSql.cols("f.period")}
       FROM findings f
       WHERE f.geom IS NULL AND btrim(coalesce(f.region_name, '')) <> ''
       ORDER BY f.citekey, f.id"""

  /** Every unplaced finding whose name the gazetteer resolves NOW, and where to — the same geocoder `enrich-source`
    * uses (no kind hint: a stored finding has none; its period steers the choice). A finding that already has a point
    * is never listed. Read-only.
    */
  def placementsIn(s: GeocodeSettings)(using DbCon): List[Placement] =
    Sql.raw(Sql.RawText(unplacedSql)).frag.query[Unplaced].run().toList.flatMap { u =>
      resolveIn(
        Attempt.Name(u.region.value),
        KindHint.Unknown,
        Geocode.periodOf(u.from.map(f => Year(f.value)), u.to.map(t => Year(t.value))),
        s.placeholderMin
      )
        .map(r =>
          Placement(
            Finding.Id(u.id.value),
            com.histmersion.api.library.Citekey(u.citekey.value),
            Placement.Region(u.region.value),
            r
          )
        )
    }

  def placements(s: GeocodeSettings)(using DbCon): List[Placement] = placementsIn(s)

  /** Give each finding its point — only while it still has NONE (compare-and-set: a finding placed or edited since is
    * left alone; its name is never touched). The touched sources' `updated_at` moves and their `finding_places` are
    * re-materialised, in the same transaction. Returns the findings placed.
    */
  def place(ps: List[Placement], toleranceKm: FindingPlacement.Implementation.ToleranceKm)(using DbCon): RowCount =
    if ps.isEmpty then RowCount(0)
    else
      val placed = ps.filter { p =>
        sql"""UPDATE findings SET geom = ST_SetSRID(ST_MakePoint(${p.to.lon}, ${p.to.lat}), 4326)
              WHERE id = ${p.findingId} AND geom IS NULL AND btrim(coalesce(region_name, '')) = ${p.region}""".update
          .run() > 0
      }
      placed.map(_.citekey).distinct.flatMap(c => Citekey.option(c.value)).foreach { ck =>
        val _ = sql"UPDATE sources SET updated_at = now() WHERE citekey = $ck".update.run()
        val _ = FindingPlaceRepo.materialiseFor(ck, toleranceKm)
      }
      RowCount(placed.size)
