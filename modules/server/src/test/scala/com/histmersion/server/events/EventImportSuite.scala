package com.histmersion
package server.events

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.server.CatalogueWiring

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{GeoPoint, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.TestDb
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.Deps
import com.histmersion.server.media.FakeMediaHttp

import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.{
  CatalogRecord,
  Correction,
  EventCount,
  EventImportReport,
  EventImportRequest,
  EventLinksRequest,
  EventPatch,
  EventPrecision,
  EventRederiveReport,
  EventTier,
  Period
}
import com.histmersion.api.library.{Filter, Finding, Source}
import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.db.DbSuite
import com.histmersion.server.ServerPlugins
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.EventPassage
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.catalogue.events.EventSelection
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** #1449 end to end against FAKE catalogues (recorded answers, no socket) and a real PostGIS (HISTMERSION_TEST_DB_URL;
  * skipped otherwise): a dry run stores nothing; GVP then NOAA merge the same eruption into one event; a second run
  * resumes (no part read twice); Wikidata adds labels and aliases; a source naming an event is proposed, confirmed into
  * an attestation from its passage, and a source no passage of which names it is refused. The P0 fixes: a GVP eruption
  * dated ± 2500 years is held inside the present with its year, ± and method kept; `--refresh` reads again and
  * refreshes the event; `rederive` recomputes a pre-V088 row from what is stored with NO request, a correction by hand
  * kept.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class EventImportSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private def fixture(name: String): String =
    scala.io.Source.fromResource(s"events/$name")(using scala.io.Codec.UTF8).mkString

  private val emptyGvp = """{"type":"FeatureCollection","features":[],"numberMatched":0}"""
  private val emptySparql = """{"head":{"vars":[]},"results":{"bindings":[]}}"""
  private val names =
    """{"entities":{"Q131386":{"labels":{"en":{"value":"Battle of Alesia"},"fr":{"value":"Siège d'Alésia"}},
    "aliases":{"en":[{"value":"Siege of Alesia"}]}}}}"""

  /** The three catalogues, answering from the fixtures; every request recorded. */
  private def http = FakeMediaHttp { url =>
    if url.contains("GVP-VOTW") then
      FakeMediaHttp.json(if url.contains("StartDateYear%3E%3D-2000+") then fixture("gvp.json") else emptyGvp)
    else if url.contains("hazard-service") then
      val ds = List("earthquakes", "volcanoes", "tsunamis").find(d => url.contains(s"/$d")).get
      FakeMediaHttp.json(
        fixture(if ds == "tsunamis" then "noaa_tsunamis_events.json" else s"noaa_$ds.json")
          .replaceAll("\"totalPages\":\\d+", "\"totalPages\":1")
      )
    else if url.contains("query.wikidata.org") then
      FakeMediaHttp.json(if url.contains("Q188055") then fixture("wikidata_siege.json") else emptySparql)
    else if url.contains("wbgetentities") && url.contains("props=claims") then
      // P31 of the two sites; P279 of the unknown class (a tumulus kind)
      FakeMediaHttp.json(
        if url.contains("Q9100001") || url.contains("Q9100002") then
          """{"entities":{"Q9100001":{"claims":{"P31":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q755017"}}}}]}},
             "Q9100002":{"claims":{"P31":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q9199999"}}}}]}}}}"""
        else
          """{"entities":{"Q9199999":{"claims":{"P279":[{"rank":"normal","mainsnak":{"datavalue":{"value":{"id":"Q34023"}}}}]}}}}"""
      )
    else if url.contains("wbgetentities") then FakeMediaHttp.json(names)
    else Left(s"unexpected $url")
  }

  /** The catalogues' paces at zero, one Wikidata class: the host's own `events.*` keys, which the first-party catalogue
    * plugins are supplied from.
    */
  private val text =
    """histmersion.media.request-interval-ms = 0
      |histmersion.events.gvp.request-interval-ms = 0
      |histmersion.events.noaa.request-interval-ms = 0
      |histmersion.events.wikidata { request-interval-ms = 0, api-interval-ms = 0, classes = "Q188055:siege" }
      |""".stripMargin

  private lazy val loaded: LoadedConfig =
    AppConfig
      .loadWithUserText(org.ekrich.config.ConfigFactory.empty, text)
      .fold(e => fail(e.sentences.mkString("; ")), identity)
  private lazy val cfg = loaded.config
  private lazy val plugins =
    ServerPlugins.load(loaded, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  /** The handlers' dependencies: `c` (the host's thresholds), the catalogues of the suite's generation. */
  private def deps(c: AppConfig): Deps = Deps(c, ds, ds, plugins = plugins)

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE catalogue IS NOT NULL OR id LIKE 'zqei%'".update.run()
    val _ = sql"DELETE FROM event_import_parts".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqei%'".update.run()
    val _ = sql"DELETE FROM places WHERE id IN ('wikidata_q9100001', 'wikidata_q9100002')".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val Gvp = CatalogRecord.Provider("gvp")
  private val Noaa = CatalogRecord.Provider("noaa")
  private val Wikidata = CatalogRecord.Provider("wikidata")
  private val Dry = EventImportRequest.DryRun.DryRun
  private val Wet = EventImportRequest.DryRun.Apply
  private val Hekla = BEventRow.Id("gvp_12724")
  private val Thera = BEventRow.Id("gvp_13879")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def run(
      h: CatalogueWiring,
      provider: CatalogRecord.Provider,
      dry: EventImportRequest.DryRun
  ): EventImportReport =
    h.eventImporter.runImport(
      provider,
      EventImportRequest(EventImportRequest.Provider(provider.value), dryRun = dry),
      None,
      _ => ()
    )

  test("a dry run reads and counts, and stores nothing"):
    val f = http
    val r = run(CatalogueWiring(deps(cfg), f), Gvp, dry = Dry)
    assertEquals((r.records, r.kept, r.created), (3, 3, 3))
    assertEquals(r.byKind, List(EventCount(EventCount.Key("eruption"), EventCount.N(3))))
    assertEquals(EventFixtures.stored(ds).count(_.catalogue.value), 0)
    assert(f.requests.forall(_._2.get("User-Agent").exists(ua => !ua.contains("@"))), "no e-mail in the User-Agent")

  test("GVP then NOAA: the same eruption is one event with both catalogue records; a rerun reads nothing again"):
    val h = CatalogueWiring(deps(cfg), http)
    val g = run(h, Gvp, dry = Wet)
    assertEquals(g.created, 3)
    val n = run(h, Noaa, dry = Wet)
    assert(n.matchedEvents.exists(_.value.contains("gvp_13879")), n.matchedEvents.toString)
    assertEquals(EventFixtures.catalogueRefs(ds, Event.Id("gvp_13879")).map(_.provider).toSet, Set("gvp", "noaa"))
    val f = http
    val r = run(CatalogueWiring(deps(cfg), f), Gvp, dry = Wet)
    assertEquals((r.records, f.requests.size), (0, 0), "every window was read: nothing is asked again")
    // #6561: a wider region (here: a lower VEI floor) judged differently — every window is read again
    val wider =
      cfg
        .modify(_.events)
        .setTo(
          cfg.events
            .modify(_.gvp)
            .setTo(
              cfg.events.gvp
                .modify(_.regionMinVei)
                .setTo(EventsGvpConfig.RegionMinVei(cfg.events.gvp.regionMinVei.value + 1))
            )
        )
    val f2 = http
    val r2 = run(CatalogueWiring(deps(wider), f2), Gvp, dry = Wet)
    assert(f2.requests.nonEmpty && r2.records.value > 0, s"thresholds changed: the windows are due again ($r2)")

  test("Wikidata: labels and aliases in other languages become the event's names"):
    val h = CatalogueWiring(deps(cfg), http)
    val r = run(h, Wikidata, dry = Wet)
    assert(r.created.value >= 2, r.toString)
    val (tier, names, provider) = EventFixtures.tierAndNames(ds, BEventRow.Id("wikidata_q131386")).get
    assertEquals((tier, provider), (EventTier.Catalogue, Some("wikidata")))
    assert(names.contains("Siège d'Alésia") && names.contains("Siege of Alesia"), names.toString)

  test("links: a source naming the event is proposed; confirmed from its passage; one without a passage refused"):
    val chunk = AppConfig.load().limits.chunkChars
    def src(ck: Citekey, body: Source.OcrMarkdown, lon: Longitude) = Source(
      ck,
      api.library.Title(s"Zqei $ck"),
      (List("A")).map(api.library.AuthorName.apply),
      None,
      (Some(2020)).map(Year.apply),
      None,
      Source.Status.Current,
      Nil,
      Nil,
      Nil,
      Some(Period(Year(-60), Year(-40))),
      None,
      None,
      None,
      None,
      None,
      ocrMarkdown = Some(body),
      findings = List(
        Finding(
          Finding.Id(s"$ck#0"),
          ck,
          Finding.Claim("a claim"),
          None,
          Some(GeoPoint(lon, Latitude(47.53))),
          Some(Period(Year(-55), Year(-50))),
          None,
          Nil,
          None
        )
      )
    )
    SourceFixtures.upsert(
      ds,
      src(
        Citekey("zqei_named2020"),
        Source.OcrMarkdown("# Gaul\n\nCaesar's victory at the Siege of Alesia ended the war."),
        Longitude(4.5)
      ),
      chunk
    )
    SourceFixtures.upsert(
      ds,
      src(Citekey("zqei_near2020"), Source.OcrMarkdown("# Gaul\n\nA hillfort was excavated nearby."), Longitude(4.6)),
      chunk
    )
    val h = CatalogueWiring(deps(cfg), http)
    val links = h.eventLinks.links(Event.Id("wikidata_q131386")).toOption.get
    assertEquals(links.candidates.map(_.citekey).take(2), List("zqei_named2020", "zqei_near2020"))
    assert(links.candidates.head.passages.exists(_.quote == EventPassage.Quote("Siege of Alesia")))
    val r = h.eventLinks
      .applyLinks(
        Caller.Local,
        Event.Id("wikidata_q131386"),
        EventLinksRequest(confirm =
          List(EventLinksRequest.Confirm("zqei_named2020"), EventLinksRequest.Confirm("zqei_near2020"))
        )
      )
      .toOption
      .get
    assertEquals(r.attested.map(a => (a.citekey, a.quote)), List(("zqei_named2020", Some("Siege of Alesia"))))
    val a = r.attested.head
    val body = SourceFixtures.bodyText(ds, domain.library.Citekey("zqei_named2020"))
    assertEquals(
      body.value.substring(a.charStart.get.value, a.charEnd.get.value),
      "Siege of Alesia",
      "the offsets point at the quote"
    )
    assert(r.refused.exists(_.value.startsWith("zqei_near2020: no passage")), r.refused.toString)
    assertEquals(EventFixtures.get(ds, BEventRow.Id("wikidata_q131386")).map(_.event.tier), Some(EventTier.Attested))
    val _ = connect(ds)(sql"DELETE FROM attestations WHERE citekey LIKE 'zqei%'".update.run())

  test("site types: the Wikidata sites on the map are typed from their P31, an unknown class walked up P279"):
    connect(ds):
      for (id, lon) <- List("wikidata_q9100001" -> 10.0, "wikidata_q9100002" -> 10.1) do
        val _ = sql"""INSERT INTO places (id, label, kind, geom, import_source, properties, creator)
                      VALUES ($id, $id, 'site', ST_SetSRID(ST_MakePoint($lon, 45.0), 4326), 'wikidata',
                              '{"class":"Q839954"}'::jsonb, jsonb_build_object('kind', 'unknown'))""".update.run()
    val chunk = AppConfig.load().limits.chunkChars
    SourceFixtures.upsert(
      ds,
      Source(
        Citekey("zqei_sites2020"),
        api.library.Title("Zqei sites"),
        (List("A")).map(api.library.AuthorName.apply),
        None,
        (Some(2020)).map(Year.apply),
        None,
        Source.Status.Current,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        None,
        None,
        None
      ),
      chunk
    )
    connect(ds):
      val _ = sql"""INSERT INTO findings (id, citekey, claim, geom, creator) VALUES
                      ('zqei_sites2020#0', 'zqei_sites2020', 'x', ST_SetSRID(ST_MakePoint(10.0, 45.0), 4326), jsonb_build_object('kind', 'unknown'))""".update
        .run()
      val _ = sql"""INSERT INTO finding_places (finding_id, place_id) VALUES
                      ('zqei_sites2020#0', 'wikidata_q9100001'), ('zqei_sites2020#0', 'wikidata_q9100002')""".update
        .run()
    val h = CatalogueWiring(deps(cfg), http)
    assertEquals(
      h.siteTypes.runSiteTypes(SiteTypes.Request(dryRun = SiteTypes.Request.DryRun.DryRun), _ => ()).pending,
      2
    )
    val r = h.siteTypes.runSiteTypes(SiteTypes.Request(), _ => ())
    assertEquals((r.asked, r.pending), (2, 0))
    val types = connect(ds)(
      sql"""SELECT id, site_type FROM places WHERE id LIKE 'wikidata_q91%' ORDER BY id"""
        .query[(String, Option[String])]
        .run()
        .toList
    )
    assertEquals(types, List("wikidata_q9100001" -> Some("settlement"), "wikidata_q9100002" -> Some("burial")))
    assertEquals(h.siteTypes.runSiteTypes(SiteTypes.Request(), _ => ()).asked, 0, "asked once")

  // ── the P0 dating fixes ─────────────────────────────────────────────────────────────────────

  private val hekla =
    """{"type":"FeatureCollection","numberMatched":1,"features":[{"type":"Feature","id":"Smithsonian_VOTW_Holocene_Eruptions.fid-x",
      |"geometry":{"type":"Point","coordinates":[-19.7,63.98]},"properties":{"Volcano_Number":372070,"Volcano_Name":"Hekla",
      |"Eruption_Number":12724,"Activity_Type":"Confirmed Eruption","ExplosivityIndexMax":5,"StartEvidenceMethod":"Tephrochronology",
      |"StartDateYear":250,"StartDateYearUncertainty":2500,"EndDateYear":null,"EndDateYearUncertainty":null}}]}""".stripMargin

  /** Only the window 0…499 holds an eruption: Hekla's 250 ± 2500. */
  private def heklaHttp = FakeMediaHttp { url =>
    if url.contains("GVP-VOTW") then
      FakeMediaHttp.json(if url.contains("StartDateYear%3E%3D0+") then hekla else emptyGvp)
    else Left(s"unexpected $url")
  }

  private def event(id: BEventRow.Id) = EventFixtures.get(ds, id).get.event

  test("GVP ± 2500: held inside the present, the year, ± and method kept; --refresh reads again and refreshes"):
    val f = heklaHttp
    val h = CatalogueWiring(deps(cfg), f, () => DeepTime.SignedYear(2026L))
    val r = h.eventImporter.runImport(
      CatalogRecord.Provider("gvp"),
      EventImportRequest(
        EventImportRequest.Provider("gvp"),
        dryRun = EventImportRequest.DryRun.Apply,
        refresh = EventImportRequest.Refresh.Refresh
      ),
      None,
      _ => ()
    )
    assert(f.requests.nonEmpty && r.created.value == 1, r.toString)
    val e = event(Hekla)
    assertEquals(e.timeSpan, TimeSpan(Some(Year(-2251)), None, None, Some(Year(2026))))
    assertEquals(
      (e.central, e.plusMinus, e.datingMethod, e.precision),
      (Some(250L), Some(2500L), Some("Tephrochronology"), EventPrecision.Imprecise)
    )
    assertEquals(e.datingNote, Some("250 CE ± 2500 (tephrochronology)"))
    // a refresh refreshes the event (from its own record), not only the catalogue row
    connect(ds)(sql"UPDATE events SET not_after = 9999 WHERE id = 'gvp_12724'".update.run())
    val again =
      h.eventImporter.runImport(
        CatalogRecord.Provider("gvp"),
        EventImportRequest(
          EventImportRequest.Provider("gvp"),
          dryRun = EventImportRequest.DryRun.Apply,
          refresh = EventImportRequest.Refresh.Refresh
        ),
        None,
        _ => ()
      )
    assertEquals((again.created, again.updated), (0, 1))
    assertEquals(event(Hekla).timeSpan.notAfter, Some(2026L))

  test("rederive: no network — a pre-V088 GVP row computed again from what is stored, a correction by hand kept"):
    // the row as V085 stored it: the whole ± window past today, the method only in the description
    connect(ds):
      val _ = sql"""UPDATE event_catalogue SET record = (record - 'central' - 'plusMinus' - 'datingMethod' - 'raw')
                      || '{"timeSpan":{"notBefore":-2251,"beginBy":null,"endFrom":null,"notAfter":2750},
                           "description":"Confirmed Eruption at Hekla; VEI 5; dated by Tephrochronology"}'::jsonb,
                      not_after = 2750
                    WHERE provider = 'gvp' AND provider_id = '12724'""".update.run()
      val _ = sql"""UPDATE events SET not_after = 2750, central = NULL, plus_minus = NULL, dating_method = NULL
                    WHERE id = 'gvp_12724'""".update.run()
    assert(
      EventFixtures
        .patch(
          ds,
          BEventRow.Id("gvp_12724"),
          EventPatch(label = Some(EventSummary.Label("Hekla, a prehistoric tephra (zqei)")))
        )
        .isRight
    )
    val offline = FakeMediaHttp(url => Left(s"no network in a re-derivation: $url"))
    val h = CatalogueWiring(deps(cfg), offline, () => DeepTime.SignedYear(2026L))
    val dry = h.eventImporter.runRederive(Some(CatalogRecord.Provider("gvp")), dryRun = true, _ => ())
    assert(dry.changed.value >= 1 && dry.updated.value == 0, dry.toString)
    assertEquals(event(Hekla).timeSpan.notAfter, Some(2750L), "a dry run stores nothing")
    val r = h.eventImporter.runRederive(Some(CatalogRecord.Provider("gvp")), dryRun = false, _ => ())
    assertEquals(offline.requests.size, 0, "not one request")
    assert(r.updated.value >= 1 && r.offMap.value >= 1 && r.corrections.kept == Correction.Counts.Kept(1), r.toString)
    assert(EventRederiveReport.summary(r).contains("re-derived"), EventRederiveReport.summary(r))
    val e = event(Hekla)
    assertEquals(e.timeSpan, TimeSpan(Some(Year(-2251)), None, None, Some(Year(2026))))
    assertEquals((e.central, e.plusMinus, e.datingMethod), (Some(250L), Some(2500L), Some("Tephrochronology")))
    assertEquals(e.description, Some("Confirmed Eruption at Hekla; VEI 5"))
    assertEquals(e.label, "Hekla, a prehistoric tephra (zqei)", "the person's label survives")
    // Thera (± 14, from its raw record) is precise and unchanged
    assertEquals(event(Thera).precision, EventPrecision.Precise)
    // the card's text, the list's and the map's feed agree: imprecise, off the map
    val geo = EventFixtures.geo(
      ds,
      Filter(period = Some(Period(Year(-500), Year(500)))),
      PlaceScope.empty,
      Some(EventSelection.Text("Hekla")),
      None,
      None,
      None,
      PageSize(100),
      GeoTolerance(0.01)
    )
    assert(
      !geo.events.exists(_.id == api.catalogue.Event.Id("gvp_12724")) && geo.imprecise.value >= 1,
      geo.toString
    )
