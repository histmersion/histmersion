package com.histmersion.db

import com.histmersion.core.*

/** The map layer draws each state ONCE (`PlaceRepo.geoJson`): pure checks of the query, and — against
  * a disposable Postgres (`TestDb`) — the de-duplication on real shapes. Fixtures sit around 150–171°E,
  * 36–41°S, where no other fixture lives. */
class PlaceGeoSuite extends munit.FunSuite:
  import Param.*

  test("an as-of query ranks sources and drops shadowed shapes; the limit comes last"):
    val q = PlaceRepo.geoJsonQuery(Some("Polity"), Some(-1900L), 50)
    assert(q.text.contains("hi.tier < lo.tier"), q.text)
    // a lone tier has no pairs: each side keeps only the tiers that can be in one
    assert(q.text.contains("WHERE tier > (SELECT min(tier) FROM live)) lo"), q.text)
    assert(q.text.contains("hi.norm = lo.norm"), q.text)
    assert(q.text.contains("NOT IN (SELECT id FROM shadowed)"), q.text)
    assert(q.text.contains("ORDER BY l.area DESC"), q.text)
    // the window of the time rule first (year, year, year ≥ modern-from), then the live set in its two
    // halves (curated, imported with a finding), each with the layer's own filters
    val half = List(S(PlaceRepo.UninformativeLabel), S("polity"))
    assertEquals(q.params, List(L(-1900L), L(-1900L), L(-1900L), L(PlaceRepo.DefaultModernFrom)) ++
      half ++ half ++ List(I(50)))
    assert(q.text.contains(PlaceRepo.inTimeSql("p", timed = true, Some("polity"))), q.text)

  test("without as-of nothing is collapsed (successive shapes across time are all real)"):
    val q = PlaceRepo.geoJsonQuery(None, None, 10)
    assert(q.text.contains("shadowed AS (SELECT NULL::text AS id WHERE false)"), q.text)
    assertEquals(q.params, List(S(PlaceRepo.UninformativeLabel), S(PlaceRepo.UninformativeLabel), I(10)))

  test("the base layers are the curated gazetteer: GeoNames and Wikidata only with a finding, and an undated one never at a year"):
    val timed = PlaceRepo.geoJsonQuery(Some("site"), Some(-1400L), 10).text
    // BaseLayerSql in its two halves: the curated places (V075's partial index), the imported with a finding
    assert(timed.contains(s"AND ${PlaceRepo.CuratedSql} UNION ALL "), timed)
    assert(timed.contains(s"AND NOT ${PlaceRepo.CuratedSql} AND p.id IN (SELECT fp.place_id FROM finding_places fp)"), timed)
    assert(timed.contains(s"NOT ${PlaceRepo.UndatedImportSql}"), timed)
    val untimed = PlaceRepo.geoJsonQuery(Some("site"), None, 10).text
    assert(untimed.contains(s"AND NOT ${PlaceRepo.CuratedSql} AND p.id IN (SELECT fp.place_id FROM finding_places fp)"))
    assert(!untimed.contains(s"NOT ${PlaceRepo.UndatedImportSql}"))

  test("V074 repairs stored rows with the configured window's default"):
    val v074 = String(getClass.getClassLoader.getResourceAsStream("db/migration/V074__imported_place_time.sql").readAllBytes(),
      java.nio.charset.StandardCharsets.UTF_8)
    assert(v074.contains(s"upper(period) - 1 - ${Gazetteer.DefaultOpenStartWindowYears}"), "V074 and the default disagree")
    assert(v074.contains("import_source IN ('wikidata', 'geonames')"), "only the bulk sources")

  // ── against Postgres ──────────────────────────────────────────────────────────────────────────

  private val testDb = TestDb.url
  private lazy val ds =
    val d = Db.dataSource(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  private def box(a: Double, b: Double, c: Double, d: Double) =
    s"""{"type":"Polygon","coordinates":[[[$a,$b],[$c,$b],[$c,$d],[$a,$d],[$a,$b]]]}"""
  private def polity(id: String, label: String, g: String, from: Long, to: Long, method: Option[String]) =
    PlaceInput(Place(id, label, PlaceKind.Polity, Some(g), Some(Period(from, to)), Some("fixture"),
      method.map(m => s"""{"method":"$m"}""")), Nil)

  private val fixtures = List(
    polity("zzq_clio_mk", "Middle Kingdom of Egypt", box(150, -40, 154, -36), -2000, -1801, None),
    polity("zzq_clio_vassal", "Allegiance of Kush", box(151, -39, 152, -38), -2000, -1801, None),
    polity("zzq_agent_mk", "Middle Kingdom Egypt", box(149, -41, 155, -35), -2055, -1650, Some("agent-drawn")),
    polity("zzq_agent_crete", "Palatial Crete", box(152.5, -39.5, 153.5, -38.5), -2000, -1450, Some("agent-drawn")),
    polity("zzq_agent_alone", "Lonely Kingdom", box(170, -40, 171, -39), -2100, -1700, Some("agent-drawn")),
    polity("zzq_ohm_ok", "Old Kingdom Egypt", box(160, -40, 164, -36), -2686, -2181, Some("ohm-import")),
    polity("zzq_clio_ok", "Old Kingdom of Egypt", box(160.5, -39, 163, -37), -2500, -2301, None))

  private def shownAt(year: Long): Set[String] =
    val json = PlaceRepo.geoJson(ds, Some("polity"), Some(year), 500)
    "\"id\":\"(zzq_[a-z_]+)\"".r.findAllMatchIn(json).map(_.group(1)).toSet

  test("each state is drawn once, from the best source, with agent sketches only as a fallback"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    val r = PlaceRepo.upsertPlaces(ds, fixtures)
    assertEquals(r.places, fixtures.size)
    assertEquals(shownAt(-1900),
      // agent_mk: same state by name; agent_crete: inside a Cliopatria shape; vassal: same source → kept
      Set("zzq_clio_mk", "zzq_clio_vassal", "zzq_agent_alone"))
    // OHM beats Cliopatria for the same state
    assertEquals(shownAt(-2400), Set("zzq_ohm_ok"))
    // before Cliopatria's Middle Kingdom starts, the agent sketch is all there is
    assertEquals(shownAt(-2030), Set("zzq_agent_mk", "zzq_agent_alone"))

  // #368: Pleiades' nameless sites ("Unnamed place" beside Misrata) were drawn with that as a label
  test("sites whose label names nothing are not drawn; a label that says more is"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def site(id: String, label: String) =
      PlaceInput(Place(id, label, PlaceKind.Site, Some("""{"type":"Point","coordinates":[165.5,-38.5]}"""),
        None, Some("fixture"), None), Nil)
    val sites = List(site("zzq_site_unnamed", "Unnamed place"), site("zzq_site_untitled", "Untitled"),
      site("zzq_site_villa", "Unnamed villa"), site("zzq_site_gate", "Unnamed Gate of the Themistoklean Wall"),
      site("zzq_site_named", "Leptis Magna"))
    val _ = PlaceRepo.upsertPlaces(ds, sites)
    val json = PlaceRepo.geoJson(ds, Some("site"), None, 100000)
    val shown = "\"id\":\"(zzq_site_[a-z_]+)\"".r.findAllMatchIn(json).map(_.group(1)).toSet
    assertEquals(shown, Set("zzq_site_gate", "zzq_site_named"))

  // #987: the Natural Earth cities are undated, so they were valid at every year the map is read at;
  // since the time rule (2026-10-09) they are drawn from `places.modern-from` on, never before it
  test("a present-day (modern) place is drawn only once the map reaches the modern era"):
    assert(PlaceRepo.geoJsonQuery(Some("site"), None, 10).text.contains(s"NOT ${PlaceRepo.ModernSql}"),
      "across all time, as before")
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def site(id: String, props: Option[String]) =
      PlaceInput(Place(id, "Zzqmodern", PlaceKind.Site, Some("""{"type":"Point","coordinates":[166.5,-38.5]}"""),
        None, Some("fixture"), props), Nil)
    val _ = PlaceRepo.upsertPlaces(ds, List(site("zzq_modern_city", Some("""{"modern":true}""")),
      site("zzq_ancient_site", None), site("zzq_unflagged_site", Some("""{"modern":false}"""))))
    val json  = PlaceRepo.geoJson(ds, Some("site"), Some(-1400L), 100000)
    val shown = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r.findAllMatchIn(json).map(_.group(1)).toSet
    // undated and with no finding, the other two are in no time view either (the time rule)
    assertEquals(shown, Set.empty[String])
    val modernEra = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r
      .findAllMatchIn(PlaceRepo.geoJson(ds, Some("site"), Some(1600L), 100000)).map(_.group(1)).toSet
    assertEquals(modernEra, Set("zzq_modern_city"))
    val allTime = "\"id\":\"(zzq_(?:modern|ancient|unflagged)_[a-z]+)\"".r
      .findAllMatchIn(PlaceRepo.geoJson(ds, Some("site"), None, 100000)).map(_.group(1)).toSet
    assertEquals(allTime, Set("zzq_ancient_site", "zzq_unflagged_site"))

  // #362: footprints were a one-off copy (V016) — re-imported polities kept "traced by hand after …"
  test("a place's extent footprint follows the place on insert and re-import"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def fp = PlaceRepo.footprints(ds, "zzq_fp_polity", None).filter(_.role == "extent")
    val first = polity("zzq_fp_polity", "Footprint Kingdom", box(166, -40, 167, -39), -1500, -1200, None)
    val _ = PlaceRepo.upsertPlaces(ds, List(first.copy(place = first.place.copy(source = Some("traced by hand")))))
    assertEquals(fp.map(_.citekey), Vector(Some("traced by hand")))
    val again = polity("zzq_fp_polity", "Footprint Kingdom", box(166, -40, 168, -38), -1500, -1100, None)
    val _ = PlaceRepo.upsertPlaces(ds, List(again.copy(place = again.place.copy(source = Some("drawn by an AI agent")))))
    assertEquals(fp.map(f => (f.citekey, f.period)), Vector((Some("drawn by an AI agent"), Some(Period(-1500, -1100)))))

  // #357: generalised outlines ran out to sea; with land loaded a polity is drawn on land only
  test("with land loaded a polity is drawn clipped to it, and its published shape is kept"):
    assume(testDb.nonEmpty, "HISTMERSION_TEST_DB_URL is not set")
    def area(sql: String) =
      val c = ds.getConnection
      try { val rs = c.createStatement().executeQuery(sql); rs.next(); rs.getDouble(1) } finally c.close()
    try
      // land is the western half of the polity's box
      val pieces = LandRepo.replace(ds, List(box(140, -40, 142, -36)))
      assert(pieces >= 1, pieces.toString)
      val before = polity("zzq_clip_early", "Coastal Kingdom", box(140, -40, 144, -36), -1500, -1200, None)
      val _ = PlaceRepo.upsertPlaces(ds, List(before)) // the trigger clips it on the way in
      assertEqualsDouble(area("SELECT ST_Area(geom_clipped) FROM places WHERE id = 'zzq_clip_early'"), 8.0, 1e-6)
      assertEqualsDouble(area("SELECT ST_Area(geom) FROM places WHERE id = 'zzq_clip_early'"), 16.0, 1e-6)
      val drawn = PlaceRepo.geoJson(ds, Some("polity"), Some(-1300L), 500)
      assert(drawn.contains("142"), drawn.take(400)) // the clip edge, not 144
      assert(!drawn.matches("(?s).*zzq_clip_early.*144.*"), drawn.take(400))
      // new land: reclip recomputes everything
      val _ = LandRepo.replace(ds, List(box(140, -40, 143, -36)))
      assert(LandRepo.reclip(ds) >= 1)
      assertEqualsDouble(area("SELECT ST_Area(geom_clipped) FROM places WHERE id = 'zzq_clip_early'"), 12.0, 1e-6)
    finally
      val _ = LandRepo.replace(ds, Nil)
      val _ = LandRepo.reclip(ds)
