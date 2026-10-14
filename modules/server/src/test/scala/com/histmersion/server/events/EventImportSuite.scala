package com.histmersion.server.events

import com.histmersion.core.*
import com.histmersion.db.{Db, EventCatalogRepo, EventRepo, Migrate, SourceRepo, TestDb}
import com.histmersion.server.Deps
import com.histmersion.server.media.FakeMediaHttp

import com.augustnagro.magnum.*

/** #1449 end to end against FAKE catalogues (recorded answers, no socket) and a real PostGIS
  * (HISTMERSION_TEST_DB_URL; skipped otherwise): a dry run stores nothing; GVP then NOAA merge the same
  * eruption into one event; a second run resumes (no part read twice); Wikidata adds labels and
  * aliases; a source naming an event is proposed, confirmed into an attestation from its passage, and a
  * source no passage of which names it is refused. */
class EventImportSuite extends munit.FunSuite:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private def fixture(name: String): String =
    scala.io.Source.fromResource(s"events/$name")(using scala.io.Codec.UTF8).mkString

  private val emptyGvp = """{"type":"FeatureCollection","features":[],"numberMatched":0}"""
  private val emptySparql = """{"head":{"vars":[]},"results":{"bindings":[]}}"""
  private val names = """{"entities":{"Q131386":{"labels":{"en":{"value":"Battle of Alesia"},"fr":{"value":"Siège d'Alésia"}},
    "aliases":{"en":[{"value":"Siege of Alesia"}]}}}}"""

  /** The three catalogues, answering from the fixtures; every request recorded. */
  private def http = FakeMediaHttp { url =>
    if url.contains("GVP-VOTW") then
      FakeMediaHttp.json(if url.contains("StartDateYear%3E%3D-2000+") then fixture("gvp.json") else emptyGvp)
    else if url.contains("hazard-service") then
      val ds = List("earthquakes", "volcanoes", "tsunamis").find(d => url.contains(s"/$d")).get
      FakeMediaHttp.json(fixture(if ds == "tsunamis" then "noaa_tsunamis_events.json" else s"noaa_$ds.json")
        .replaceAll("\"totalPages\":\\d+", "\"totalPages\":1"))
    else if url.contains("query.wikidata.org") then
      FakeMediaHttp.json(if url.contains("Q188055") then fixture("wikidata_siege.json") else emptySparql)
    else if url.contains("wbgetentities") && url.contains("props=claims") then
      // P31 of the two sites; P279 of the unknown class (a tumulus kind)
      FakeMediaHttp.json(
        if url.contains("Q9100001") || url.contains("Q9100002") then
          """{"entities":{"Q9100001":{"claims":{"P31":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q755017"}}}}]}},
             "Q9100002":{"claims":{"P31":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q9199999"}}}}]}}}}"""
        else """{"entities":{"Q9199999":{"claims":{"P279":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q34023"}}}}]}}}}""")
    else if url.contains("wbgetentities") then FakeMediaHttp.json(names)
    else Left(s"unexpected $url")
  }

  private lazy val base = AppConfig.load()
  private lazy val ds =
    val d = Db.dataSource(base.db.copy(url = testDb.get))
    Migrate.run(d)
    d
  private lazy val cfg = base.copy(
    media = base.media.copy(requestIntervalMs = 0),
    events = base.events.copy(
      gvp = base.events.gvp.copy(requestIntervalMs = 0),
      noaa = base.events.noaa.copy(requestIntervalMs = 0),
      wikidata = base.events.wikidata.copy(requestIntervalMs = 0, apiIntervalMs = 0, classes = "Q188055:siege")))

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE catalogue IS NOT NULL OR id LIKE 'zqei%'".update.run()
    val _ = sql"DELETE FROM event_import_parts".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqei%'".update.run()
    val _ = sql"DELETE FROM places WHERE id IN ('wikidata_q9100001', 'wikidata_q9100002')".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterAll(): Unit = if testDb.isDefined then clean()

  private def run(h: EventCatalogHandlers, provider: String, dry: Boolean): EventImportReport =
    h.runImport(provider, EventImportRequest(provider, dryRun = dry), None, _ => ())

  test("a dry run reads and counts, and stores nothing"):
    val f = http
    val r = run(EventCatalogHandlers(Deps(cfg, ds), f), "gvp", dry = true)
    assertEquals((r.records, r.kept, r.created), (3, 3, 3))
    assertEquals(r.byKind, List(EventCount("eruption", 3)))
    assertEquals(EventCatalogRepo.stored(ds).count(_.catalogue), 0)
    assert(f.requests.forall(_._2.get("User-Agent").exists(ua => !ua.contains("@"))), "no e-mail in the User-Agent")

  test("GVP then NOAA: the same eruption is one event with both catalogue records; a rerun reads nothing again"):
    val h = EventCatalogHandlers(Deps(cfg, ds), http)
    val g = run(h, "gvp", dry = false)
    assertEquals(g.created, 3)
    val n = run(h, "noaa", dry = false)
    assert(n.matchedEvents.exists(_.contains("gvp_13879")), n.matchedEvents.toString)
    assertEquals(EventCatalogRepo.catalogueRefs(ds, "gvp_13879").map(_.provider).toSet, Set("gvp", "noaa"))
    val f = http
    val r = run(EventCatalogHandlers(Deps(cfg, ds), f), "gvp", dry = false)
    assertEquals((r.records, f.requests.size), (0, 0), "every window was read: nothing is asked again")

  test("Wikidata: labels and aliases in other languages become the event's names"):
    val h = EventCatalogHandlers(Deps(cfg, ds), http)
    val r = run(h, "wikidata", dry = false)
    assert(r.created >= 2, r.toString)
    val (tier, names, provider) = EventCatalogRepo.tierAndNames(ds, "wikidata_q131386").get
    assertEquals((tier, provider), (EventTier.Catalogue, Some("wikidata")))
    assert(names.contains("Siège d'Alésia") && names.contains("Siege of Alesia"), names.toString)

  test("links: a source naming the event is proposed; confirmed from its passage; one without a passage refused"):
    val chunk = AppConfig.load().limits.chunkChars
    def src(ck: String, body: String, lon: Double) = Source(ck, s"Zqei $ck", List("A"), None, Some(2020), None,
      SourceStatus.Current, Nil, Nil, Nil, Some(Period(-60, -40)), None, None, None, None, None,
      ocrMarkdown = Some(body),
      findings = List(Finding(s"$ck#0", ck, "a claim", None, Some(GeoPoint(lon, 47.53)), Some(Period(-55, -50)), None, Nil, None)))
    SourceRepo.upsert(ds, src("zqei_named2020", "# Gaul\n\nCaesar's victory at the Siege of Alesia ended the war.", 4.5), chunk)
    SourceRepo.upsert(ds, src("zqei_near2020", "# Gaul\n\nA hillfort was excavated nearby.", 4.6), chunk)
    val h = EventCatalogHandlers(Deps(cfg, ds), http)
    val links = h.links("wikidata_q131386").toOption.get
    assertEquals(links.candidates.map(_.citekey).take(2), List("zqei_named2020", "zqei_near2020"))
    assert(links.candidates.head.passages.exists(_.quote == "Siege of Alesia"))
    val r = h.applyLinks("wikidata_q131386", EventLinksRequest(confirm = List("zqei_named2020", "zqei_near2020"))).toOption.get
    assertEquals(r.attested.map(a => (a.citekey, a.quote)), List(("zqei_named2020", Some("Siege of Alesia"))))
    val a = r.attested.head
    val body = SourceRepo.bodyText(ds, "zqei_named2020")
    assertEquals(body.substring(a.charStart.get, a.charEnd.get), "Siege of Alesia", "the offsets point at the quote")
    assert(r.refused.exists(_.startsWith("zqei_near2020: no passage")), r.refused.toString)
    assertEquals(EventRepo.get(ds, "wikidata_q131386").map(_.event.tier), Some(EventTier.Attested))
    val _ = connect(ds)(sql"DELETE FROM attestations WHERE citekey LIKE 'zqei%'".update.run())

  test("site types: the Wikidata sites on the map are typed from their P31, an unknown class walked up P279"):
    connect(ds):
      for (id, lon) <- List("wikidata_q9100001" -> 10.0, "wikidata_q9100002" -> 10.1) do
        val _ = sql"""INSERT INTO places (id, label, kind, geom, import_source, properties)
                      VALUES ($id, $id, 'site', ST_SetSRID(ST_MakePoint($lon, 45.0), 4326), 'wikidata',
                              '{"class":"Q839954"}'::jsonb)""".update.run()
    val chunk = AppConfig.load().limits.chunkChars
    SourceRepo.upsert(ds, Source("zqei_sites2020", "Zqei sites", List("A"), None, Some(2020), None, SourceStatus.Current,
      Nil, Nil, Nil, None, None, None, None, None, None), chunk)
    connect(ds):
      val _ = sql"""INSERT INTO findings (id, citekey, claim, geom) VALUES
                      ('zqei_sites2020#0', 'zqei_sites2020', 'x', ST_SetSRID(ST_MakePoint(10.0, 45.0), 4326))""".update.run()
      val _ = sql"""INSERT INTO finding_places (finding_id, place_id) VALUES
                      ('zqei_sites2020#0', 'wikidata_q9100001'), ('zqei_sites2020#0', 'wikidata_q9100002')""".update.run()
    val h = EventCatalogHandlers(Deps(cfg, ds), http)
    assertEquals(h.runSiteTypes(SiteTypesRequest(dryRun = true), _ => ()).pending, 2)
    val r = h.runSiteTypes(SiteTypesRequest(), _ => ())
    assertEquals((r.asked, r.pending), (2, 0))
    val types = connect(ds)(sql"""SELECT id, site_type FROM places WHERE id LIKE 'wikidata_q91%' ORDER BY id"""
      .query[(String, Option[String])].run().toList)
    assertEquals(types, List("wikidata_q9100001" -> Some("settlement"), "wikidata_q9100002" -> Some("burial")))
    assertEquals(h.runSiteTypes(SiteTypesRequest(), _ => ()).asked, 0, "asked once")
