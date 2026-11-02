package com.histmersion.db

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.PlaceKind

/** The map layer draws each state ONCE (`PlaceFixtures.geoJson`): pure checks of the query, and — against a disposable
  * Postgres (`TestDb`) — the de-duplication on real shapes. Fixtures sit around 150–171°E, 36–41°S, where no other
  * fixture lives.
  */
class PlaceGeoSuite extends com.histmersion.db.DbSuite:
  import Param.*

  test("an as-of query ranks sources and drops shadowed shapes; the limit comes last"):
    val q = PlaceFixtures.geoJsonQuery(Some("Polity"), Some(-1900L), 50)
    assert(q.text.contains("hi.tier < lo.tier"), q.text)
    // a lone tier has no pairs: each side keeps only the tiers that can be in one
    assert(q.text.contains("WHERE tier > (SELECT min(tier) FROM live)) lo"), q.text)
    assert(q.text.contains("hi.norm = lo.norm"), q.text)
    assert(q.text.contains("NOT IN (SELECT id FROM shadowed)"), q.text)
    assert(q.text.contains("ORDER BY l.area DESC"), q.text)
    // the window of the time rule first (year, year, year ≥ modern-from), then the live set in its two
    // halves (curated, imported with a finding), each with the layer's own filters
    val half = List(S(PlaceFixtures.UninformativeLabel), S("polity"))
    assertEquals(
      q.params,
      List(L(-1900L), L(-1900L), L(-1900L), L(PlaceFixtures.DefaultModernFrom)) ++
        half ++ half ++ List(I(50))
    )
    assert(q.text.contains(PlaceFixtures.inTimeSql("p", Some(-1900L), Some("polity"))), q.text)

  test("without as-of nothing is collapsed (successive shapes across time are all real)"):
    val q = PlaceFixtures.geoJsonQuery(None, None, 10)
    assert(q.text.contains("shadowed AS (SELECT NULL::text AS id WHERE false)"), q.text)
    assertEquals(q.params, List(S(PlaceFixtures.UninformativeLabel), S(PlaceFixtures.UninformativeLabel), I(10)))

  test(
    "the base layers are the curated gazetteer: GeoNames and Wikidata only with a finding, and an undated one never at a year"
  ):
    val timed = PlaceFixtures.geoJsonQuery(Some("site"), Some(-1400L), 10).text
    // BaseLayerSql in its two halves: the curated places (V075's partial index), the imported with a finding
    assert(timed.contains(s"AND ${PlaceFixtures.CuratedSql} UNION ALL "), timed)
    // at a year the imported half says it holds dated imports only, the predicate of `places_dated_import` (V076)
    assert(
      timed.contains(
        s"AND NOT ${PlaceFixtures.CuratedSql} AND ${PlaceFixtures.DatedImportSql}" +
          " AND EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id OFFSET 0)"
      ),
      timed
    )
    assert(timed.contains(s"NOT ${PlaceFixtures.UndatedImportSql}"), timed)
    val untimed = PlaceFixtures.geoJsonQuery(Some("site"), None, 10).text
    assert(
      untimed.contains(s"AND NOT ${PlaceFixtures.CuratedSql} AND p.id IN (SELECT fp.place_id FROM finding_places fp)")
    )
    assert(!untimed.contains(s"NOT ${PlaceFixtures.UndatedImportSql}"))

  test("an undated polity known only by a point is left out at a year, in both halves; across all time it is drawn"):
    val timed = PlaceFixtures.geoJsonQuery(Some("polity"), Some(-200L), 10).text
    assertEquals(
      timed.split(java.util.regex.Pattern.quote(PlaceFixtures.UndatedPolityPointSql), -1).length - 1,
      2,
      timed
    )
    assert(!PlaceFixtures.geoJsonQuery(Some("polity"), None, 10).text.contains(PlaceFixtures.UndatedPolityPointSql))

  test("over a whole range: the window is the range, shadowing only among shapes that coexist, one version per state"):
    val q = PlaceFixtures.geoJsonTimeQuery(Some("polity"), GeoTime.Within(-2000, -1000, latest = false), 50)
    assert(q.text.contains(PlaceFixtures.inWindowSql("p", Some((-2000L, -1000L)), Some("polity"))), q.text)
    assert(q.text.contains("p.period && int8range(-2000::bigint, -1000::bigint, '[]')"), q.text)
    // a layer of areas: the two small tiers (sketches, OHM) find their partners through the index of
    // V090 (`places_area_time_geom`: non-site, period && …, geom && …), each partner held to `live`'s
    // own predicate — never every shape against every shape
    assert(!q.text.contains(PlaceFixtures.rangeShadowedSql), q.text)
    assert(
      q.text.contains("FROM (SELECT * FROM live WHERE tier = 3) s CROSS JOIN LATERAL") &&
        q.text.contains("FROM (SELECT * FROM live WHERE tier = 1) s CROSS JOIN LATERAL"),
      q.text
    )
    assert(
      q.text.contains(
        "WHERE p.kind <> 'site' AND p.kind = s.kind AND p.period && s.per AND p.geom && s.bb::geometry AND "
      ),
      q.text
    )
    assert(q.text.contains(") c WHERE c.tier < 3) c") && q.text.contains(") c WHERE c.tier = 2) c"), q.text)
    assert(q.text.contains(PlaceFixtures.rangeShadowTestSql), q.text)
    // the stored shape columns (V090), not the polygon read again per row
    assert(q.text.contains("p.label_norm AS norm, p.shape_area AS area, p.shape_box AS bb"), q.text)
    assert(!q.text.contains("ST_Area(p.geom)") && !q.text.contains("p.geom::box2d"), q.text)
    assert(q.text.contains("coalesce(p.web_geojson, ST_AsGeoJSON(coalesce(p.geom_clipped, p.geom), 5))"), q.text)
    assert(q.text.contains("min(es) OVER (PARTITION BY kind, coalesce(nullif(norm, ''), id))"), q.text)
    assert(q.text.contains("FROM (SELECT * FROM shown ORDER BY area DESC"), q.text)
    val half = List(S(PlaceFixtures.UninformativeLabel), S("polity"))
    // the live set's two halves, then the two pair halves (each holds a partner to the same filters)
    assertEquals(
      q.params,
      List(L(-2000L), L(-1000L), L(-1000L), L(PlaceFixtures.DefaultModernFrom)) ++ half ++ half ++
        half ++ half ++ List(I(50))
    )
    // the plain pairing — a site or kindless layer, and the reference below: the small tiers against `live`
    val plain =
      PlaceFixtures.geoJsonTimeQuery(
        Some("polity"),
        GeoTime.Within(-2000, -1000, latest = false),
        50,
        pairsByIndex = false
      )
    assert(
      plain.text.contains(PlaceFixtures.rangeShadowedSql) && plain.text.contains("AND hi.per && lo.per"),
      plain.text
    )
    assert(
      plain.text.contains("FROM live WHERE tier = 3) lo JOIN (SELECT * FROM live WHERE tier < 3) hi") &&
        plain.text.contains("FROM live WHERE tier = 2) lo JOIN (SELECT * FROM live WHERE tier = 1) hi"),
      plain.text
    )
    assertEquals(
      plain.params,
      List(L(-2000L), L(-1000L), L(-1000L), L(PlaceFixtures.DefaultModernFrom)) ++ half ++ half ++ List(I(50))
    )
    List(Some("site"), None).foreach(k =>
      assert(
        PlaceFixtures
          .geoJsonTimeQuery(k, GeoTime.Within(-2000, -1000, latest = false), 50)
          .text
          .contains(PlaceFixtures.rangeShadowedSql),
        s"kind $k"
      )
    )
    val late = PlaceFixtures.geoJsonTimeQuery(Some("polity"), GeoTime.Within(-2000, -1000, latest = true), 50).text
    assert(late.contains("max(ee) OVER") && late.contains("WHERE v.ee = v.anchor"), late)
    // a year is what it always was: none of the range's machinery
    val year = PlaceFixtures.geoJsonTimeQuery(Some("polity"), GeoTime.At(-1500), 50)
    assertEquals(year.text, PlaceFixtures.geoJsonQuery(Some("polity"), Some(-1500L), 50).text)
    assert(!year.text.contains(" per") && !year.text.contains("shown"), year.text)

  test("the pair test skips the polygon intersection only where the boxes rule it out"):
    val t = PlaceFixtures.rangeShadowTestSql
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
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  private def box(a: Double, b: Double, c: Double, d: Double) =
    s"""{"type":"Polygon","coordinates":[[[$a,$b],[$c,$b],[$c,$d],[$a,$d],[$a,$b]]]}"""
  private def polity(id: String, label: String, g: String, from: Long, to: Long, method: Option[String]) =
    PlaceInput(
      Place(
        id,
        label,
        PlaceKind.Polity,
        Some(g),
        Some(Period(from, to)),
        Some("fixture"),
        method.map(m => s"""{"method":"$m"}""")
      ),
      Nil
    )

  private val fixtures = List(
    polity("zzq_clio_mk", "Middle Kingdom of Egypt", box(150, -40, 154, -36), -2000, -1801, None),
    polity("zzq_clio_vassal", "Allegiance of Kush", box(151, -39, 152, -38), -2000, -1801, None),
    polity("zzq_agent_mk", "Middle Kingdom Egypt", box(149, -41, 155, -35), -2055, -1650, Some("agent-drawn")),
    polity("zzq_agent_crete", "Palatial Crete", box(152.5, -39.5, 153.5, -38.5), -2000, -1450, Some("agent-drawn")),
    polity("zzq_agent_alone", "Lonely Kingdom", box(170, -40, 171, -39), -2100, -1700, Some("agent-drawn")),
    polity("zzq_ohm_ok", "Old Kingdom Egypt", box(160, -40, 164, -36), -2686, -2181, Some("ohm-import")),
    polity("zzq_clio_ok", "Old Kingdom of Egypt", box(160.5, -39, 163, -37), -2500, -2301, None)
  )

  private def shownAt(year: Long): Set[String] =
    val json = PlaceFixtures.geoJson(ds, Some("polity"), Some(year), 500)
    "\"id\":\"(zzq_[a-z_]+)\"".r.findAllMatchIn(json).map(_.group(1)).toSet

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
  private val ranged = List(
    polity("zzr_hatti_a", "Zzr Hatti", box(172, -40, 175, -37), -1600, -1501, None),
    polity("zzr_hatti_vassal", "Zzr Hatti", box(178, -38, 179, -37), -1600, -1550, None),
    polity("zzr_hatti_b", "Zzr Hatti", box(172, -40, 176, -36), -1500, -1401, None),
    polity("zzr_hatti_c", "Zzr Hatti", box(172, -40, 177, -36), -1400, -1180, None),
    polity(
      "zzr_agent_empire",
      "Zzr Hittite Empire",
      box(172.1, -39.9, 175.9, -36.1),
      -1400,
      -1180,
      Some("agent-drawn")
    ),
    polity("zzr_agent_gap", "Zzr Interregnum", box(172, -40, 175, -37), -1170, -1110, Some("agent-drawn")),
    polity("zzr_phrygia", "Zzr Phrygia", box(172, -40, 175, -37), -1100, -700, None)
  )

  private def shownOver(lo: Long, hi: Long, latest: Boolean): Set[String] =
    val json = PlaceFixtures.geoJsonAt(ds, Some("polity"), GeoTime.Within(lo, hi, latest), 500)
    "\"id\":\"(zzr_[a-z_]+)\"".r.findAllMatchIn(json).map(_.group(1)).toSet

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
        .findAllMatchIn(PlaceFixtures.geoJson(ds, Some("polity"), Some(-1300L), 500))
        .map(_.group(1))
        .toSet,
      Set("zzr_hatti_c")
    )

  /** The whole answer of a geo statement, row by row. */
  private def rows(q: Sql): Vector[String] =
    import com.augustnagro.magnum.*
    connect(ds)(
      q.frag
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
      val t = GeoTime.Within(lo, hi, latest)
      val indexed = rows(PlaceFixtures.geoJsonTimeQuery(Some("polity"), t, limit))
      assertEquals(
        indexed,
        rows(PlaceFixtures.geoJsonTimeQuery(Some("polity"), t, limit, pairsByIndex = false)),
        s"$lo..$hi latest=$latest limit=$limit"
      )
    // and the collapse did happen: OHM over Cliopatria, Cliopatria over the sketch of the same state
    val over = rows(PlaceFixtures.geoJsonTimeQuery(Some("polity"), GeoTime.Within(-2700, -1600, false), 500)).mkString
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
    val round = PlaceInput(
      Place(
        "zzs_round",
        "The Kingdom of Zzs-Round (East)",
        PlaceKind.Polity,
        Some(circle(157.5, -38.5, 1.5, 720)),
        Some(Period(3000, 3100)),
        Some("fixture")
      ),
      Nil
    )
    val tiny = polity("zzs_tiny", "Zzs City", box(158.9, -38.05, 158.95, -38.0), 3000, 3100, None)
    val dot = PlaceInput(
      Place(
        "zzs_dot",
        "Zzs Dot",
        PlaceKind.Site,
        Some("""{"type":"Point","coordinates":[156.2,-38.2]}"""),
        Some(Period(3000, 3100)),
        Some("fixture")
      ),
      Nil
    )
    // a self-intersecting sketch: stored (and simplified) like any other, marked invalid
    val bowtie = polity(
      "zzs_bowtie",
      "Zzs Bowtie",
      """{"type":"Polygon","coordinates":[[[156.5,-37.5],[157.5,-36.5],[157.5,-37.5],[156.5,-36.5],[156.5,-37.5]]]}""",
      3000,
      3100,
      Some("agent-drawn")
    )
    val _ = PlaceFixtures.upsertPlaces(ds, List(round, tiny, dot, bowtie))
    type Cols = (String, Boolean, Boolean, Boolean, Boolean, Option[Int], Option[Int], Option[Double], Boolean, Boolean)
    def cols(): Vector[Cols] = connect(ds)(
      Sql
        .raw(s"""SELECT id, label_norm = ${PlaceFixtures.normLabelSql("label")}, shape_area = ST_Area(geom),
                 shape_box::text = (geom::box2d)::text, shape_dim = ST_Dimension(geom),
                 ST_NPoints(ST_GeomFromGeoJSON(web_geojson)), ST_NPoints(geom),
                 ST_HausdorffDistance(ST_GeomFromGeoJSON(web_geojson), geom),
                 shape_valid = ST_IsValid(geom),
                 -- the pair test's shortcut: a valid shape is what ST_MakeValid returns, byte for byte
                 NOT shape_valid OR ST_AsEWKB(ST_MakeValid(geom)) = ST_AsEWKB(geom)
          FROM places WHERE left(id, 4) = 'zzs_' ORDER BY id""")
        .frag
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
    val drawn = PlaceFixtures.geoJsonAt(ds, Some("polity"), GeoTime.Within(3000, 3100, latest = false), 500)
    val stored = connect(ds)(sql"SELECT web_geojson FROM places WHERE id = 'zzs_round'".query[String].run().head)
    assert(drawn.contains(s""""geometry":$stored"""), drawn.take(300))
    // a re-import with a new label and shape: every column follows
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(round.copy(place = round.place.copy(label = "Zzs Square", geometry = Some(box(156.5, -39.5, 158.5, -37.5)))))
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
    def site(id: String, label: String) =
      PlaceInput(
        Place(
          id,
          label,
          PlaceKind.Site,
          Some("""{"type":"Point","coordinates":[165.5,-38.5]}"""),
          None,
          Some("fixture"),
          None
        ),
        Nil
      )
    val sites = List(
      site("zzq_site_unnamed", "Unnamed place"),
      site("zzq_site_untitled", "Untitled"),
      site("zzq_site_villa", "Unnamed villa"),
      site("zzq_site_gate", "Unnamed Gate of the Themistoklean Wall"),
      site("zzq_site_named", "Leptis Magna")
    )
    val _ = PlaceFixtures.upsertPlaces(ds, sites)
    val json = PlaceFixtures.geoJson(ds, Some("site"), None, 100000)
    val shown = "\"id\":\"(zzq_site_[a-z_]+)\"".r.findAllMatchIn(json).map(_.group(1)).toSet
    assertEquals(shown, Set("zzq_site_gate", "zzq_site_named"))

  // #987: the Natural Earth cities are undated, so they were valid at every year the map is read at;
  // since the time rule (2026-10-09) they are drawn from `places.modern-from` on, never before it
  test("a present-day (modern) place is drawn only once the map reaches the modern era"):
    assert(
      PlaceFixtures.geoJsonQuery(Some("site"), None, 10).text.contains(s"NOT ${PlaceFixtures.ModernSql}"),
      "across all time, as before"
    )
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def site(id: String, props: Option[String]) =
      PlaceInput(
        Place(
          id,
          "Zzqmodern",
          PlaceKind.Site,
          Some("""{"type":"Point","coordinates":[166.5,-38.5]}"""),
          None,
          Some("fixture"),
          props
        ),
        Nil
      )
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        site("zzq_modern_city", Some("""{"modern":true}""")),
        site("zzq_ancient_site", None),
        site("zzq_unflagged_site", Some("""{"modern":false}"""))
      )
    )
    val json = PlaceFixtures.geoJson(ds, Some("site"), Some(-1400L), 100000)
    val shown = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r.findAllMatchIn(json).map(_.group(1)).toSet
    // undated and with no finding, the other two are in no time view either (the time rule)
    assertEquals(shown, Set.empty[String])
    val modernEra = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r
      .findAllMatchIn(PlaceFixtures.geoJson(ds, Some("site"), Some(1600L), 100000))
      .map(_.group(1))
      .toSet
    assertEquals(modernEra, Set("zzq_modern_city"))
    val allTime = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r
      .findAllMatchIn(PlaceFixtures.geoJson(ds, Some("site"), None, 100000))
      .map(_.group(1))
      .toSet
    assertEquals(allTime, Set("zzq_ancient_site", "zzq_unflagged_site"))

  // #362: footprints were a one-off copy (V016) — re-imported polities kept "traced by hand after …"
  test("a place's extent footprint follows the place on insert and re-import"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def fp = PlaceFixtures.footprints(ds, "zzq_fp_polity", None).filter(_.role == "extent")
    val first = polity("zzq_fp_polity", "Footprint Kingdom", box(166, -40, 167, -39), -1500, -1200, None)
    val _ = PlaceFixtures.upsertPlaces(ds, List(first.copy(place = first.place.copy(source = Some("traced by hand")))))
    assertEquals(fp.map(_.citekey), Vector(Some("traced by hand")))
    val again = polity("zzq_fp_polity", "Footprint Kingdom", box(166, -40, 168, -38), -1500, -1100, None)
    val _ =
      PlaceFixtures.upsertPlaces(ds, List(again.copy(place = again.place.copy(source = Some("drawn by an AI agent")))))
    assertEquals(fp.map(f => (f.citekey, f.period)), Vector((Some("drawn by an AI agent"), Some(Period(-1500, -1100)))))

  // #357: generalised outlines ran out to sea; with land loaded a polity is drawn on land only
  test("with land loaded a polity is drawn clipped to it, and its published shape is kept"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def area(sql: String) =
      val c = ds.getConnection
      try { val rs = c.createStatement().executeQuery(sql); rs.next(); rs.getDouble(1) }
      finally c.close()
    try
      // land is the western half of the polity's box
      val pieces = PlaceFixtures.replaceLand(ds, List(box(140, -40, 142, -36)))
      assert(pieces >= 1, pieces.toString)
      val before = polity("zzq_clip_early", "Coastal Kingdom", box(140, -40, 144, -36), -1500, -1200, None)
      val _ = PlaceFixtures.upsertPlaces(ds, List(before)) // the trigger clips it on the way in
      assertEqualsDouble(area("SELECT ST_Area(geom_clipped) FROM places WHERE id = 'zzq_clip_early'"), 8.0, 1e-6)
      assertEqualsDouble(area("SELECT ST_Area(geom) FROM places WHERE id = 'zzq_clip_early'"), 16.0, 1e-6)
      val drawn = PlaceFixtures.geoJson(ds, Some("polity"), Some(-1300L), 500)
      assert(drawn.contains("142"), drawn.take(400)) // the clip edge, not 144
      assert(!drawn.matches("(?s).*zzq_clip_early.*144.*"), drawn.take(400))
      // new land: reclip recomputes everything
      val _ = PlaceFixtures.replaceLand(ds, List(box(140, -40, 143, -36)))
      assert(PlaceFixtures.reclip(ds) >= 1)
      assertEqualsDouble(area("SELECT ST_Area(geom_clipped) FROM places WHERE id = 'zzq_clip_early'"), 12.0, 1e-6)
      // the stored web outline (V090) follows the new clip
      val redrawn = PlaceFixtures.geoJson(ds, Some("polity"), Some(-1300L), 500)
      assert(redrawn.matches("(?s).*zzq_clip_early.*143.*"), redrawn.take(400))
      assert(!redrawn.matches("(?s).*zzq_clip_early.*144.*"), redrawn.take(400))
    finally
      val _ = PlaceFixtures.replaceLand(ds, Nil)
      val _ = PlaceFixtures.reclip(ds)
