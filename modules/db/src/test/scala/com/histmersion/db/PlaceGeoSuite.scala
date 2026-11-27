package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Migrate
import com.augustnagro.magnum.{sql, Frag}
import com.histmersion.api.catalogue.{GeoTime, Period, Place}
import com.histmersion.util.database.given
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.places.BPlaceRow
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.lint.{tolerated, LintRule}

/** The map layer draws each state ONCE (`PlaceFixtures.geoJson`): pure checks of the query, and — against a disposable
  * Postgres (`TestDb`) — the de-duplication on real shapes. Fixtures sit around 150–171°E, 36–41°S, where no other
  * fixture lives.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PlaceGeoSuite extends DbSuite:

  test("an as-of query ranks sources and drops shadowed shapes; the limit comes last"):
    val q = PlaceFixtures.geoJsonQuery(
      Some(domain.catalogue.Place.Kind.Wire("Polity")),
      Some(DeepTime.SignedYear(-1900L)),
      PlaceFixtures.Limit(50)
    )
    assert(q.sqlString.contains("hi.tier < lo.tier"), q.sqlString)
    // a lone tier has no pairs: each side keeps only the tiers that can be in one
    assert(q.sqlString.contains("WHERE tier > (SELECT min(tier) FROM live)) lo"), q.sqlString)
    assert(q.sqlString.contains("hi.norm = lo.norm"), q.sqlString)
    assert(q.sqlString.contains("NOT IN (SELECT id FROM shadowed)"), q.sqlString)
    assert(q.sqlString.contains("ORDER BY l.area DESC"), q.sqlString)
    // the window of the time rule first (year, year, year ≥ modern-from), then the live set in its two
    // halves (curated, imported with a finding), each with the layer's own filters
    val half = List(PlaceFixtures.UninformativeLabel, "polity")
    assertEquals(
      q.params,
      List(-1900L, -1900L, -1900L, PlaceFixtures.DefaultModernFrom) ++
        half ++ half ++ List(50)
    )
    assert(
      q.sqlString.contains(
        PlaceFixtures
          .inTimeSql(
            sql"p",
            Some(DeepTime.SignedYear(-1900L)),
            Some(domain.catalogue.Place.Kind.Wire("polity"))
          )
          .sqlString
      ),
      q.sqlString
    )

  test("without as-of nothing is collapsed (successive shapes across time are all real)"):
    val q = PlaceFixtures.geoJsonQuery(None, None, PlaceFixtures.Limit(10))
    assert(q.sqlString.contains("shadowed AS (SELECT NULL::text AS id WHERE false)"), q.sqlString)
    assertEquals(
      q.params,
      List(PlaceFixtures.UninformativeLabel, PlaceFixtures.UninformativeLabel, 10)
    )

  test(
    "the base layers are the curated gazetteer: GeoNames and Wikidata only with a finding, and an undated one never at a year"
  ):
    val timed = PlaceFixtures
      .geoJsonQuery(
        Some(domain.catalogue.Place.Kind.Wire("site")),
        Some(DeepTime.SignedYear(-1400L)),
        PlaceFixtures.Limit(10)
      )
      .sqlString
    // BaseLayerSql in its two halves: the curated places (V075's partial index), the imported with a finding
    val curated = PlaceFixtures.CuratedSql.sqlString
    val undatedImport = PlaceFixtures.UndatedImportSql.sqlString
    assert(timed.contains(s"AND $curated UNION ALL "), timed)
    // at a year the imported half says it holds dated imports only, the predicate of `places_dated_import` (V076)
    assert(
      timed.contains(
        s"AND NOT $curated AND ${PlaceFixtures.DatedImportSql.sqlString}" +
          " AND EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id OFFSET 0)"
      ),
      timed
    )
    assert(timed.contains(s"NOT $undatedImport"), timed)
    val untimed = PlaceFixtures
      .geoJsonQuery(Some(domain.catalogue.Place.Kind.Wire("site")), None, PlaceFixtures.Limit(10))
      .sqlString
    assert(
      untimed.contains(s"AND NOT $curated AND p.id IN (SELECT fp.place_id FROM finding_places fp)")
    )
    assert(!untimed.contains(s"NOT $undatedImport"))

  test("an undated polity known only by a point is left out at a year, in both halves; across all time it is drawn"):
    val timed = PlaceFixtures
      .geoJsonQuery(
        Some(domain.catalogue.Place.Kind.Wire("polity")),
        Some(DeepTime.SignedYear(-200L)),
        PlaceFixtures.Limit(10)
      )
      .sqlString
    assertEquals(
      timed.split(java.util.regex.Pattern.quote(PlaceFixtures.UndatedPolityPointSql.sqlString), -1).length - 1,
      2,
      timed
    )
    assert(
      !PlaceFixtures
        .geoJsonQuery(Some(domain.catalogue.Place.Kind.Wire("polity")), None, PlaceFixtures.Limit(10))
        .sqlString
        .contains(PlaceFixtures.UndatedPolityPointSql.sqlString)
    )

  test("over a whole range: the window is the range, shadowing only among shapes that coexist, one version per state"):
    val q = PlaceFixtures.geoJsonTimeQuery(
      Some(domain.catalogue.Place.Kind.Wire("polity")),
      GeoTime.Within(Year(-2000), Year(-1000), latest = GeoTime.Within.Latest(false)),
      PlaceFixtures.Limit(50)
    )
    assert(
      q.sqlString.contains(
        PlaceFixtures
          .inWindowSql(
            sql"p",
            Some((DeepTime.SignedYear(-2000L), DeepTime.SignedYear(-1000L))),
            Some(domain.catalogue.Place.Kind.Wire("polity"))
          )
          .sqlString
      ),
      q.sqlString
    )
    assert(q.sqlString.contains("p.period && int8range(-2000::bigint, -1000::bigint, '[]')"), q.sqlString)
    // a layer of areas: the two small tiers (sketches, OHM) find their partners through the index of
    // V090 (`places_area_time_geom`: non-site, period && …, geom && …), each partner held to `live`'s
    // own predicate — never every shape against every shape
    assert(!q.sqlString.contains(PlaceFixtures.rangeShadowedSql.sqlString), q.sqlString)
    assert(
      q.sqlString.contains("FROM (SELECT * FROM live WHERE tier = 3) s CROSS JOIN LATERAL") &&
        q.sqlString.contains("FROM (SELECT * FROM live WHERE tier = 1) s CROSS JOIN LATERAL"),
      q.sqlString
    )
    assert(
      q.sqlString.contains(
        "WHERE p.kind <> 'site' AND p.kind = s.kind AND p.period && s.per AND p.geom && s.bb::geometry AND "
      ),
      q.sqlString
    )
    assert(
      q.sqlString.contains(") c WHERE c.tier < 3) c") && q.sqlString.contains(") c WHERE c.tier = 2) c"),
      q.sqlString
    )
    assert(q.sqlString.contains(PlaceFixtures.rangeShadowTestSql.sqlString), q.sqlString)
    // the stored shape columns (V090), not the polygon read again per row
    assert(q.sqlString.contains("p.label_norm AS norm, p.shape_area AS area, p.shape_box AS bb"), q.sqlString)
    assert(!q.sqlString.contains("ST_Area(p.geom)") && !q.sqlString.contains("p.geom::box2d"), q.sqlString)
    assert(
      q.sqlString.contains("coalesce(p.web_geojson, ST_AsGeoJSON(coalesce(p.geom_clipped, p.geom), 5))"),
      q.sqlString
    )
    assert(q.sqlString.contains("min(es) OVER (PARTITION BY kind, coalesce(nullif(norm, ''), id))"), q.sqlString)
    assert(q.sqlString.contains("FROM (SELECT * FROM shown ORDER BY area DESC"), q.sqlString)
    val half = List(PlaceFixtures.UninformativeLabel, "polity")
    // the live set's two halves, then the two pair halves (each holds a partner to the same filters)
    assertEquals(
      q.params,
      List(
        -2000L,
        -1000L,
        -1000L,
        PlaceFixtures.DefaultModernFrom
      ) ++ half ++ half ++
        half ++ half ++ List(50)
    )
    // the plain pairing — a site or kindless layer, and the reference below: the small tiers against `live`
    val plain =
      PlaceFixtures.geoJsonTimeQuery(
        Some(domain.catalogue.Place.Kind.Wire("polity")),
        GeoTime.Within(Year(-2000), Year(-1000), latest = GeoTime.Within.Latest(false)),
        PlaceFixtures.Limit(50),
        pairsByIndex = PlaceFixtures.PairsByIndex(false)
      )
    assert(
      plain.sqlString.contains(PlaceFixtures.rangeShadowedSql.sqlString) &&
        plain.sqlString.contains("AND hi.per && lo.per"),
      plain.sqlString
    )
    assert(
      plain.sqlString.contains("FROM live WHERE tier = 3) lo JOIN (SELECT * FROM live WHERE tier < 3) hi") &&
        plain.sqlString.contains("FROM live WHERE tier = 2) lo JOIN (SELECT * FROM live WHERE tier = 1) hi"),
      plain.sqlString
    )
    assertEquals(
      plain.params,
      List(
        -2000L,
        -1000L,
        -1000L,
        PlaceFixtures.DefaultModernFrom
      ) ++ half ++ half ++ List(50)
    )
    List(Some(domain.catalogue.Place.Kind.Wire("site")), None).foreach(k =>
      assert(
        PlaceFixtures
          .geoJsonTimeQuery(
            k,
            GeoTime.Within(Year(-2000), Year(-1000), latest = GeoTime.Within.Latest(false)),
            PlaceFixtures.Limit(50)
          )
          .sqlString
          .contains(PlaceFixtures.rangeShadowedSql.sqlString),
        s"kind $k"
      )
    )
    val late = PlaceFixtures
      .geoJsonTimeQuery(
        Some(domain.catalogue.Place.Kind.Wire("polity")),
        GeoTime.Within(Year(-2000), Year(-1000), latest = GeoTime.Within.Latest(true)),
        PlaceFixtures.Limit(50)
      )
      .sqlString
    assert(late.contains("max(ee) OVER") && late.contains("WHERE v.ee = v.anchor"), late)
    // a year is what it always was: none of the range's machinery
    val year = PlaceFixtures.geoJsonTimeQuery(
      Some(domain.catalogue.Place.Kind.Wire("polity")),
      GeoTime.At(Year(-1500)),
      PlaceFixtures.Limit(50)
    )
    assertEquals(
      year.sqlString,
      PlaceFixtures
        .geoJsonQuery(
          Some(domain.catalogue.Place.Kind.Wire("polity")),
          Some(DeepTime.SignedYear(-1500L)),
          PlaceFixtures.Limit(50)
        )
        .sqlString
    )
    assert(!year.sqlString.contains(" per") && !year.sqlString.contains("shown"), year.sqlString)

  test("the pair test skips the polygon intersection only where the boxes rule it out"):
    val t = PlaceFixtures.rangeShadowTestSql.sqlString
    // the same name first; then the boxes' overlap (never negative: two negative sides would multiply
    // to a positive area) bounds the intersection from above; only then the polygons
    assert(
      t.contains(
        " WHERE pr.same_name OR (greatest(0, least(ST_XMax(pr.lbb), ST_XMax(pr.hbb))" +
          " - greatest(ST_XMin(pr.lbb), ST_XMin(pr.hbb))) * greatest(0, least(ST_YMax(pr.lbb), ST_YMax(pr.hbb))" +
          s" - greatest(ST_YMin(pr.lbb), ST_YMin(pr.hbb))) > ${PlaceFixtures.SameStateOverlap} * pr.small AND ST_Intersects"
      ),
      t
    )
    assert(t.indexOf("ST_Intersects") < t.indexOf("ST_Intersection"), t)

  // ── against Postgres ──────────────────────────────────────────────────────────────────────────

  private val testDb = TestDb.url
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(AppConfig.load().db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private def box(a: Double, b: Double, c: Double, d: Double) =
    s"""{"type":"Polygon","coordinates":[[[$a,$b],[$c,$b],[$c,$d],[$a,$d],[$a,$b]]]}"""
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def polity(
      id: Place.Id,
      label: Place.Label,
      g: Place.Geometry,
      from: Year,
      to: Year,
      method: Option[String]
  ) =
    Place.Input(
      Place(
        id,
        label,
        Place.KindValue(domain.catalogue.Place.Kind.Polity),
        Some(g),
        Some(Period(from, to)),
        Some(Place.Source("fixture")),
        method.map(m => Place.Properties(s"""{"method":"$m"}"""))
      ),
      Nil
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val fixtures = List(
    polity(
      Place.Id("zzq_clio_mk"),
      Place.Label("Middle Kingdom of Egypt"),
      Place.Geometry(box(150, -40, 154, -36)),
      Year(-2000),
      Year(-1801),
      None
    ),
    polity(
      Place.Id("zzq_clio_vassal"),
      Place.Label("Allegiance of Kush"),
      Place.Geometry(box(151, -39, 152, -38)),
      Year(-2000),
      Year(-1801),
      None
    ),
    polity(
      Place.Id("zzq_agent_mk"),
      Place.Label("Middle Kingdom Egypt"),
      Place.Geometry(box(149, -41, 155, -35)),
      Year(-2055),
      Year(-1650),
      Some("agent-drawn")
    ),
    polity(
      Place.Id("zzq_agent_crete"),
      Place.Label("Palatial Crete"),
      Place.Geometry(box(152.5, -39.5, 153.5, -38.5)),
      Year(-2000),
      Year(-1450),
      Some("agent-drawn")
    ),
    polity(
      Place.Id("zzq_agent_alone"),
      Place.Label("Lonely Kingdom"),
      Place.Geometry(box(170, -40, 171, -39)),
      Year(-2100),
      Year(-1700),
      Some("agent-drawn")
    ),
    polity(
      Place.Id("zzq_ohm_ok"),
      Place.Label("Old Kingdom Egypt"),
      Place.Geometry(box(160, -40, 164, -36)),
      Year(-2686),
      Year(-2181),
      Some("ohm-import")
    ),
    polity(
      Place.Id("zzq_clio_ok"),
      Place.Label("Old Kingdom of Egypt"),
      Place.Geometry(box(160.5, -39, 163, -37)),
      Year(-2500),
      Year(-2301),
      None
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def shownAt(year: Long): Set[String] =
    val json = PlaceFixtures.geoJson(
      ds,
      Some(domain.catalogue.Place.Kind.Wire("polity")),
      Some(DeepTime.SignedYear(year)),
      PlaceFixtures.Limit(500)
    )
    "\"id\":\"(zzq_[a-z_]+)\"".r.findAllMatchIn(json.value).map(_.group(1)).toSet

  test("each state is drawn once, from the best source, with agent sketches only as a fallback"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    val r = PlaceFixtures.upsertPlaces(ds, fixtures)
    assertEquals(r.places, fixtures.size)
    assertEquals(
      shownAt(-1900),
      // agent_mk: same state by name; agent_crete: inside a Cliopatria shape; vassal: same source → kept
      Set("zzq_clio_mk", "zzq_clio_vassal", "zzq_agent_alone")
    )
    // OHM beats Cliopatria for the same state
    assertEquals(shownAt(-2400), Set("zzq_ohm_ok"))
    // before Cliopatria's Middle Kingdom starts, the agent sketch is all there is
    assertEquals(shownAt(-2030), Set("zzq_agent_mk", "zzq_agent_alone"))

  // the "States shown" menu over a range (GeoPick): successive versions of one state (same label) are
  // one shape, the earliest or the latest; a different state on the same land (Phrygia after Hatti) is
  // its own; an agent sketch is shadowed only by a shape it coexists with. Fixtures at 172–179°E.
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ranged = List(
    polity(
      Place.Id("zzr_hatti_a"),
      Place.Label("Zzr Hatti"),
      Place.Geometry(box(172, -40, 175, -37)),
      Year(-1600),
      Year(-1501),
      None
    ),
    polity(
      Place.Id("zzr_hatti_vassal"),
      Place.Label("Zzr Hatti"),
      Place.Geometry(box(178, -38, 179, -37)),
      Year(-1600),
      Year(-1550),
      None
    ),
    polity(
      Place.Id("zzr_hatti_b"),
      Place.Label("Zzr Hatti"),
      Place.Geometry(box(172, -40, 176, -36)),
      Year(-1500),
      Year(-1401),
      None
    ),
    polity(
      Place.Id("zzr_hatti_c"),
      Place.Label("Zzr Hatti"),
      Place.Geometry(box(172, -40, 177, -36)),
      Year(-1400),
      Year(-1180),
      None
    ),
    polity(
      Place.Id("zzr_agent_empire"),
      Place.Label("Zzr Hittite Empire"),
      Place.Geometry(box(172.1, -39.9, 175.9, -36.1)),
      Year(-1400),
      Year(-1180),
      Some("agent-drawn")
    ),
    polity(
      Place.Id("zzr_agent_gap"),
      Place.Label("Zzr Interregnum"),
      Place.Geometry(box(172, -40, 175, -37)),
      Year(-1170),
      Year(-1110),
      Some("agent-drawn")
    ),
    polity(
      Place.Id("zzr_phrygia"),
      Place.Label("Zzr Phrygia"),
      Place.Geometry(box(172, -40, 175, -37)),
      Year(-1100),
      Year(-700),
      None
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def shownOver(lo: Long, hi: Long, latest: Boolean): Set[String] =
    val json = PlaceFixtures.geoJsonAt(
      ds,
      Some(domain.catalogue.Place.Kind.Wire("polity")),
      GeoTime.Within(Year(lo), Year(hi), GeoTime.Within.Latest(latest)),
      PlaceFixtures.Limit(500)
    )
    "\"id\":\"(zzr_[a-z_]+)\"".r.findAllMatchIn(json.value).map(_.group(1)).toSet

  test("over a range: every state alive in it, one version each — the earliest or the latest"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    val _ = PlaceFixtures.upsertPlaces(ds, ranged)
    // the first year of Hatti in the range shows two shapes of it (as the map at -1600 does)
    assertEquals(
      shownOver(-1600, -700, latest = false),
      Set("zzr_hatti_a", "zzr_hatti_vassal", "zzr_agent_gap", "zzr_phrygia")
    )
    assertEquals(shownOver(-1600, -700, latest = true), Set("zzr_hatti_c", "zzr_agent_gap", "zzr_phrygia"))
    // clipped to the range: Hatti's first version there is b, its last c; the sketch coexists with c → shadowed
    assertEquals(shownOver(-1450, -1300, latest = false), Set("zzr_hatti_b"))
    assertEquals(shownOver(-1450, -1300, latest = true), Set("zzr_hatti_c"))
    // where no better shape lives at the same time, the sketch is drawn
    assertEquals(shownOver(-1185, -1105, latest = false), Set("zzr_hatti_c", "zzr_agent_gap"))
    // a snapshot is unchanged: at -1300 the sketch is shadowed, Hatti c drawn
    assertEquals(
      "\"id\":\"(zzr_[a-z_]+)\"".r
        .findAllMatchIn(
          PlaceFixtures
            .geoJson(
              ds,
              Some(domain.catalogue.Place.Kind.Wire("polity")),
              Some(DeepTime.SignedYear(-1300L)),
              PlaceFixtures.Limit(500)
            )
            .value
        )
        .map(_.group(1))
        .toSet,
      Set("zzr_hatti_c")
    )

  /** The whole answer of a geo statement, row by row. */
  private def rows(q: Frag): Vector[String] =
    import com.augustnagro.magnum.*
    connect(ds)(
      q
        .query[
          (
              String,
              String,
              String,
              Option[Long],
              Option[Long],
              String,
              String,
              Boolean,
              String,
              Option[String],
              Option[String],
              Option[String],
              Option[String]
          )
        ]
        .run()
        .map(_.toString)
    )

  test("over a range, pairing through the index of V090 shadows exactly what pairing all of `live` does"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    // the sketches (tier 3) and OHM (tier 1) of both fixture sets, beside whatever the shared database holds
    val _ = PlaceFixtures.upsertPlaces(ds, fixtures ++ ranged)
    for
      (lo, hi) <- List(
        (-2700L, -1600L),
        (-2500L, -2300L),
        (-2100L, -1900L),
        (-1600L, -700L),
        (-1450L, -1300L),
        (-1185L, -1105L),
        (-3000L, 2000L)
      )
      latest <- List(false, true)
      limit <- List(2, 500)
    do
      val t = GeoTime.Within(Year(lo), Year(hi), GeoTime.Within.Latest(latest))
      val indexed = rows(
        PlaceFixtures.geoJsonTimeQuery(
          Some(domain.catalogue.Place.Kind.Wire("polity")),
          t,
          PlaceFixtures.Limit(limit)
        )
      )
      assertEquals(
        indexed,
        rows(
          PlaceFixtures.geoJsonTimeQuery(
            Some(domain.catalogue.Place.Kind.Wire("polity")),
            t,
            PlaceFixtures.Limit(limit),
            pairsByIndex = PlaceFixtures.PairsByIndex(false)
          )
        ),
        s"$lo..$hi latest=$latest limit=$limit"
      )
    // and the collapse did happen: OHM over Cliopatria, Cliopatria over the sketch of the same state
    val over = rows(
      PlaceFixtures.geoJsonTimeQuery(
        Some(domain.catalogue.Place.Kind.Wire("polity")),
        GeoTime.Within(Year(-2700), Year(-1600), GeoTime.Within.Latest(false)),
        PlaceFixtures.Limit(500)
      )
    ).mkString
    assert(
      over.contains("zzq_ohm_ok") && !over.contains("zzq_clio_ok") && !over.contains("zzq_agent_crete"),
      over.take(600)
    )

  // V090: what the area layer reads of a shape is stored with it. Fixtures at 156–159°E in 3000 CE,
  // where nothing else lives.
  private def circle(cx: Double, cy: Double, r: Double, n: Int): String =
    // formatted against Locale.ROOT: the default locale may write a decimal comma (AGENTS.md)
    val pts = (0 to n).map { i =>
      val a = 2 * math.Pi * (i % n) / n
      String.format(java.util.Locale.ROOT, "[%.6f,%.6f]", cx + r * math.cos(a), cy + r * math.sin(a))
    }
    s"""{"type":"Polygon","coordinates":[[${pts.mkString(",")}]]}"""

  test("V090: the shape columns are generated from the row, follow every write, and the web outline is simplified"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    import com.augustnagro.magnum.*
    val round = Place.Input(
      Place(
        Place.Id("zzs_round"),
        Place.Label("The Kingdom of Zzs-Round (East)"),
        Place.KindValue(domain.catalogue.Place.Kind.Polity),
        Some(Place.Geometry(circle(157.5, -38.5, 1.5, 720))),
        Some(Period(Year(3000), Year(3100))),
        Some(Place.Source("fixture"))
      ),
      Nil
    )
    val tiny = polity(
      Place.Id("zzs_tiny"),
      Place.Label("Zzs City"),
      Place.Geometry(box(158.9, -38.05, 158.95, -38.0)),
      Year(3000),
      Year(3100),
      None
    )
    val dot = Place.Input(
      Place(
        Place.Id("zzs_dot"),
        Place.Label("Zzs Dot"),
        Place.KindValue(domain.catalogue.Place.Kind.Site),
        Some(Place.Geometry("""{"type":"Point","coordinates":[156.2,-38.2]}""")),
        Some(Period(Year(3000), Year(3100))),
        Some(Place.Source("fixture"))
      ),
      Nil
    )
    // a self-intersecting sketch: stored (and simplified) like any other, marked invalid
    val bowtie = polity(
      Place.Id("zzs_bowtie"),
      Place.Label("Zzs Bowtie"),
      Place.Geometry(
        """{"type":"Polygon","coordinates":[[[156.5,-37.5],[157.5,-36.5],[157.5,-37.5],[156.5,-36.5],[156.5,-37.5]]]}"""
      ),
      Year(3000),
      Year(3100),
      Some("agent-drawn")
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(round, tiny, dot, bowtie))
    type Cols = (String, Boolean, Boolean, Boolean, Boolean, Option[Int], Option[Int], Option[Double], Boolean, Boolean)
    def cols(): Vector[Cols] = connect(ds)(
      sql"""SELECT id, label_norm = ${PlaceFixtures.normLabel(sql"label")}, shape_area = ST_Area(geom),
                 shape_box::text = (geom::box2d)::text, shape_dim = ST_Dimension(geom),
                 ST_NPoints(ST_GeomFromGeoJSON(web_geojson)), ST_NPoints(geom),
                 ST_HausdorffDistance(ST_GeomFromGeoJSON(web_geojson), geom),
                 shape_valid = ST_IsValid(geom),
                 -- the pair test's shortcut: a valid shape is what ST_MakeValid returns, byte for byte
                 NOT shape_valid OR ST_AsEWKB(ST_MakeValid(geom)) = ST_AsEWKB(geom)
          FROM places WHERE left(id, 4) = 'zzs_' ORDER BY id"""
        .query[Cols]
        .run()
    )
    def check(): Map[String, Cols] =
      val c = cols()
      assertEquals(c.map(_._1), Vector("zzs_bowtie", "zzs_dot", "zzs_round", "zzs_tiny"))
      c.foreach(r =>
        assert(r._2 && r._3 && r._4 && r._5 && r._9 && r._10, s"a generated column disagrees with its expression: $r")
      )
      c.map(r => r._1 -> r).toMap
    val first = check()
    // a point is written from `geom` as before; nothing stored for it
    assertEquals(first("zzs_dot")._6, None)
    // a large area loses its detail below the tolerance (0.02° for a shape this large) and stays within it
    val (_, _, _, _, _, web, raw, dist, _, _) = first("zzs_round")
    assert(web.get < raw.get / 2, s"$web of $raw vertices")
    assert(dist.get <= 0.02 + 1e-3, s"Hausdorff $dist") // + the 3-decimal rounding of a shape this large
    // a city state (0.05°) is simplified to a hundredth of its size: its box is drawn as it is
    assertEquals(first("zzs_tiny")._6, first("zzs_tiny")._7)
    val valid = connect(ds)(
      sql"SELECT id, shape_valid FROM places WHERE left(id, 4) = 'zzs_' AND shape_dim = 2 ORDER BY id"
        .query[(String, Boolean)]
        .run()
    )
    assertEquals(valid, Vector(("zzs_bowtie", false), ("zzs_round", true), ("zzs_tiny", true)))
    // the map draws the stored outline
    val drawn = PlaceFixtures
      .geoJsonAt(
        ds,
        Some(domain.catalogue.Place.Kind.Wire("polity")),
        GeoTime.Within(Year(3000), Year(3100), latest = GeoTime.Within.Latest(false)),
        PlaceFixtures.Limit(500)
      )
      .value
    val stored = connect(ds)(sql"SELECT web_geojson FROM places WHERE id = 'zzs_round'".query[String].run().head)
    assert(drawn.contains(s""""geometry":$stored"""), drawn.take(300))
    // a re-import with a new label and shape: every column follows
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        round
          .modify(_.place)
          .setTo(
            round.place
              .modify(_.label)
              .setTo(Place.Label("Zzs Square"))
              .modify(_.geometry)
              .setTo(Some(Place.Geometry(box(156.5, -39.5, 158.5, -37.5))))
          )
      )
    )
    val again = check()
    assertEquals(again("zzs_round")._6, Some(5))
    val norm = connect(ds)(
      sql"SELECT label_norm, shape_area FROM places WHERE id = 'zzs_round'".query[(String, Double)].run().head
    )
    assertEquals(norm._1, "zzssquare")
    assertEqualsDouble(norm._2, 4.0, 1e-9)
    // the shapes are the columns moved out of line, so the row's small ones stay in it
    val storage = connect(ds)(
      sql"""SELECT attname, attstorage::text FROM pg_attribute
      WHERE attrelid = 'places'::regclass AND attname IN ('geom', 'geom_clipped') ORDER BY attname"""
        .query[(String, String)]
        .run()
    )
    assertEquals(storage, Vector(("geom", "x"), ("geom_clipped", "x")))

  // #368: Pleiades' nameless sites ("Unnamed place" beside Misrata) were drawn with that as a label
  test("sites whose label names nothing are not drawn; a label that says more is"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def site(id: Place.Id, label: Place.Label) =
      Place.Input(
        Place(
          id,
          label,
          Place.KindValue(domain.catalogue.Place.Kind.Site),
          Some(Place.Geometry("""{"type":"Point","coordinates":[165.5,-38.5]}""")),
          None,
          Some(Place.Source("fixture")),
          None
        ),
        Nil
      )
    val sites = List(
      site(Place.Id("zzq_site_unnamed"), Place.Label("Unnamed place")),
      site(Place.Id("zzq_site_untitled"), Place.Label("Untitled")),
      site(Place.Id("zzq_site_villa"), Place.Label("Unnamed villa")),
      site(Place.Id("zzq_site_gate"), Place.Label("Unnamed Gate of the Themistoklean Wall")),
      site(Place.Id("zzq_site_named"), Place.Label("Leptis Magna"))
    )
    val _ = PlaceFixtures.upsertPlaces(ds, sites)
    val json = PlaceFixtures.geoJson(
      ds,
      Some(domain.catalogue.Place.Kind.Wire("site")),
      None,
      PlaceFixtures.Limit(100000)
    )
    val shown = "\"id\":\"(zzq_site_[a-z_]+)\"".r.findAllMatchIn(json.value).map(_.group(1)).toSet
    assertEquals(shown, Set("zzq_site_gate", "zzq_site_named"))

  // #987: the Natural Earth cities are undated, so they were valid at every year the map is read at;
  // since the time rule (2026-10-09) they are drawn from `places.modern-from` on, never before it
  test("a present-day (modern) place is drawn only once the map reaches the modern era"):
    assert(
      PlaceFixtures
        .geoJsonQuery(Some(domain.catalogue.Place.Kind.Wire("site")), None, PlaceFixtures.Limit(10))
        .sqlString
        .contains(s"NOT ${PlaceFixtures.ModernSql.sqlString}"),
      "across all time, as before"
    )
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def site(id: Place.Id, props: Option[Place.Properties]) =
      Place.Input(
        Place(
          id,
          Place.Label("Zzqmodern"),
          Place.KindValue(domain.catalogue.Place.Kind.Site),
          Some(Place.Geometry("""{"type":"Point","coordinates":[166.5,-38.5]}""")),
          None,
          Some(Place.Source("fixture")),
          props
        ),
        Nil
      )
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        site(Place.Id("zzq_modern_city"), Some(Place.Properties("""{"modern":true}"""))),
        site(Place.Id("zzq_ancient_site"), None),
        site(Place.Id("zzq_unflagged_site"), Some(Place.Properties("""{"modern":false}""")))
      )
    )
    val json = PlaceFixtures.geoJson(
      ds,
      Some(domain.catalogue.Place.Kind.Wire("site")),
      Some(DeepTime.SignedYear(-1400L)),
      PlaceFixtures.Limit(100000)
    )
    val shown =
      "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r.findAllMatchIn(json.value).map(_.group(1)).toSet
    // undated and with no finding, the other two are in no time view either (the time rule)
    assertEquals(shown, Set.empty[String])
    val modernEra = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r
      .findAllMatchIn(
        PlaceFixtures
          .geoJson(
            ds,
            Some(domain.catalogue.Place.Kind.Wire("site")),
            Some(DeepTime.SignedYear(1600L)),
            PlaceFixtures.Limit(100000)
          )
          .value
      )
      .map(_.group(1))
      .toSet
    assertEquals(modernEra, Set("zzq_modern_city"))
    val allTime = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r
      .findAllMatchIn(
        PlaceFixtures
          .geoJson(
            ds,
            Some(domain.catalogue.Place.Kind.Wire("site")),
            None,
            PlaceFixtures.Limit(100000)
          )
          .value
      )
      .map(_.group(1))
      .toSet
    assertEquals(allTime, Set("zzq_ancient_site", "zzq_unflagged_site"))

  // #362: footprints were a one-off copy (V016) — re-imported polities kept "traced by hand after …"
  test("a place's extent footprint follows the place on insert and re-import"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def fp = PlaceFixtures
      .footprints(ds, domain.catalogue.Place.Id("zzq_fp_polity"), None)
      .filter(_.role.value == "extent")
    val first = polity(
      Place.Id("zzq_fp_polity"),
      Place.Label("Footprint Kingdom"),
      Place.Geometry(box(166, -40, 167, -39)),
      Year(-1500),
      Year(-1200),
      None
    )
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(first.modify(_.place).setTo(first.place.modify(_.source).setTo(Some(Place.Source("traced by hand")))))
    )
    assertEquals(fp.map(_.citekey), Vector(Some("traced by hand")))
    val again = polity(
      Place.Id("zzq_fp_polity"),
      Place.Label("Footprint Kingdom"),
      Place.Geometry(box(166, -40, 168, -38)),
      Year(-1500),
      Year(-1100),
      None
    )
    val _ =
      PlaceFixtures.upsertPlaces(
        ds,
        List(
          again.modify(_.place).setTo(again.place.modify(_.source).setTo(Some(Place.Source("drawn by an AI agent"))))
        )
      )
    assertEquals(
      fp.map(f => (f.citekey, f.period)),
      Vector((Some("drawn by an AI agent"), Some(Period(Year(-1500), Year(-1100)))))
    )

  // #357: generalised outlines ran out to sea; with land loaded a polity is drawn on land only
  test("with land loaded a polity is drawn clipped to it, and its published shape is kept"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def area(sql: String) =
      val c = ds.getConnection
      try { val rs = c.createStatement().executeQuery(sql); rs.next(); rs.getDouble(1) }
      finally c.close()
    try
      // land is the western half of the polity's box
      val pieces = PlaceFixtures.replaceLand(ds, List(BPlaceRow.Geom(box(140, -40, 142, -36))))
      assert(pieces.value >= 1, pieces.toString)
      val before = polity(
        Place.Id("zzq_clip_early"),
        Place.Label("Coastal Kingdom"),
        Place.Geometry(box(140, -40, 144, -36)),
        Year(-1500),
        Year(-1200),
        None
      )
      val _ = PlaceFixtures.upsertPlaces(ds, List(before)) // the trigger clips it on the way in
      assertEqualsDouble(area("SELECT ST_Area(geom_clipped) FROM places WHERE id = 'zzq_clip_early'"), 8.0, 1e-6)
      assertEqualsDouble(area("SELECT ST_Area(geom) FROM places WHERE id = 'zzq_clip_early'"), 16.0, 1e-6)
      val drawn = PlaceFixtures
        .geoJson(
          ds,
          Some(domain.catalogue.Place.Kind.Wire("polity")),
          Some(DeepTime.SignedYear(-1300L)),
          PlaceFixtures.Limit(500)
        )
        .value
      assert(drawn.contains("142"), drawn.take(400)) // the clip edge, not 144
      assert(!drawn.matches("(?s).*zzq_clip_early.*144.*"), drawn.take(400))
      // new land: reclip recomputes everything
      val _ = PlaceFixtures.replaceLand(ds, List(BPlaceRow.Geom(box(140, -40, 143, -36))))
      assert(PlaceFixtures.reclip(ds).value >= 1)
      assertEqualsDouble(area("SELECT ST_Area(geom_clipped) FROM places WHERE id = 'zzq_clip_early'"), 12.0, 1e-6)
      // the stored web outline (V090) follows the new clip
      val redrawn = PlaceFixtures
        .geoJson(
          ds,
          Some(domain.catalogue.Place.Kind.Wire("polity")),
          Some(DeepTime.SignedYear(-1300L)),
          PlaceFixtures.Limit(500)
        )
        .value
      assert(redrawn.matches("(?s).*zzq_clip_early.*143.*"), redrawn.take(400))
      assert(!redrawn.matches("(?s).*zzq_clip_early.*144.*"), redrawn.take(400))
    finally
      val _ = PlaceFixtures.replaceLand(ds, Nil)
      val _ = PlaceFixtures.reclip(ds)
