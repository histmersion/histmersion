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
    assert(q.text.contains("hi.norm = lo.norm"), q.text)
    assert(q.text.contains("NOT IN (SELECT id FROM shadowed)"), q.text)
    assert(q.text.contains("ORDER BY l.area DESC"), q.text)
    assertEquals(q.params, List(S("polity"), L(-1900L), I(50)))

  test("without as-of nothing is collapsed (successive shapes across time are all real)"):
    val q = PlaceRepo.geoJsonQuery(None, None, 10)
    assert(q.text.contains("shadowed AS (SELECT NULL::text AS id WHERE false)"), q.text)
    assertEquals(q.params, List(I(10)))

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
