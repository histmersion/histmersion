package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*
import com.histmersion.domain.catalogue.places.SiteTypeFixtures

import com.histmersion.api.{BBox, GeoPoint, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.{Migrate, Param, Sql}
import com.histmersion.api.catalogue.{
  CatalogRecord,
  EventLinkCandidate,
  EventPassage,
  EventPut,
  EventTier,
  Period,
  Place
}
import com.histmersion.api.library.{Filter, Finding, Source}
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.database.given

/** #1449 against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): V085's kinds and tables, writing placed
  * catalogue records (a compiled event matched, never overwritten), the tier and the kind-group / tier / area /
  * catalogue-context filters of the events feed, findings near an event, the sources naming it, stored candidates and
  * their statuses, lead facts, an article's catalogue events, and site types in the places feed. Fixtures sit at
  * 150–152°E, 40°S; ids start with `zqec`.
  */
class EventCatalogRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(testDb.get))
    Migrate.run(d)
    d

  private val Lat = -40.0

  private val src = Source(
    citekey = com.histmersion.api.library.Citekey("zqec_src2020"),
    title = com.histmersion.api.library.Title("Zqec volcanology"),
    authors = (List("A")).map(com.histmersion.api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
    venue = None,
    status = com.histmersion.api.library.Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = List(com.histmersion.api.library.Source.Tags("zqec")),
    period = Some(Period(com.histmersion.api.catalogue.Year(-1700), com.histmersion.api.catalogue.Year(-1500))),
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      Finding(
        com.histmersion.api.library.Finding.Id("zqec_src2020#0"),
        com.histmersion.api.library.Citekey("zqec_src2020"),
        com.histmersion.api.library.Finding.Claim("ash layer"),
        None,
        Some(GeoPoint(150.2, Lat)),
        Some(Period(com.histmersion.api.catalogue.Year(-1620), com.histmersion.api.catalogue.Year(-1600))),
        None,
        Nil,
        Some(Confidence.High)
      ),
      Finding(
        com.histmersion.api.library.Finding.Id("zqec_src2020#1"),
        com.histmersion.api.library.Citekey("zqec_src2020"),
        com.histmersion.api.library.Finding.Claim("far away"),
        None,
        Some(GeoPoint(170.0, Lat)),
        Some(Period(com.histmersion.api.catalogue.Year(-1620), com.histmersion.api.catalogue.Year(-1600))),
        None,
        Nil,
        Some(Confidence.High)
      )
    ),
    edges = Nil,
    ocrMarkdown = Some("# Zqec\n\nThe Zqecian eruption buried the town. Later the Zqecian eruption was dated.")
  )

  private def rec(p: String, id: String, kind: String, y: Long, lon: Double, vei: Option[Int] = None) =
    CatalogRecord(
      p,
      id,
      s"https://example.org/$id",
      s"Zqec $kind $id",
      kind,
      TimeSpan(Some(y), Some(y), Some(y), Some(y)),
      Some(Lat),
      Some(lon),
      vei = vei,
      names = List("Zqecian eruption")
    )

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqec%' OR id LIKE 'zztest_%'".update.run()
    val _ = sql"DELETE FROM attestations WHERE citekey LIKE 'zqec%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqec%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqec%'".update.run()
    val _ = sql"DELETE FROM event_import_parts WHERE provider = 'zztest'".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      SourceFixtures.upsert(ds, src, cfg.limits.chunkChars)
      // a compiled event: a source names it
      EventFixtures.put(
        ds,
        "zqec_compiled",
        EventPut(
          com.histmersion.api.catalogue.EventSummary.Label("Zqec compiled eruption"),
          com.histmersion.api.catalogue.EventSummary.Kind("natural_disaster"),
          TimeSpan(Some(com.histmersion.api.catalogue.Year(-1615)), None, None, Some(com.histmersion.api.catalogue.Year(-1605))),
          geometry = Some(s"""{"type":"Point","coordinates":[150.0,$Lat]}"""),
          citekey = Some("zqec_src2020")
        )
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  test("V085: the tables are there and the compiled event's kind is refined only on migration"):
    val tables = connect(ds)(
      sql"""SELECT table_name FROM information_schema.tables
        WHERE table_name IN ('event_catalogue', 'event_import_parts', 'event_link_candidates', 'event_link_checks')"""
        .query[String]
        .run()
        .toSet
    )
    assertEquals(tables.size, 4)
    val cols = connect(ds)(
      sql"""SELECT column_name FROM information_schema.columns
        WHERE table_name = 'places' AND column_name IN ('site_type', 'site_type_checked_at')"""
        .query[String]
        .run()
        .toSet
    )
    assertEquals(cols, Set("site_type", "site_type_checked_at"))

  test("saving placed records: new events created, a compiled one matched and never overwritten"):
    val stored = EventFixtures.stored(ds).filter(_.id.startsWith("zqec"))
    val records = List(
      rec("zztest", "a", "natural_disaster", -1610, 150.01, Some(6)), // the compiled event's kind is not eruption: new
      rec("zztest", "b", "eruption", -1200, 151.0, Some(4))
    )
    val placed = CatalogMerge.place(records, stored, CatalogMerge.Tolerance(com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(60), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(30)))
    // the first is natural_disaster like the compiled one, at its place and time: matched
    assertEquals(placed.map(_._2.eventId), List("zqec_compiled", "zztest_b"))
    val saved = EventFixtures.save(ds, placed)
    assertEquals((saved.created, saved.rows), (1, 2))
    val compiled = EventFixtures.get(ds, "zqec_compiled").get.event
    assertEquals(
      (compiled.label, compiled.kind, compiled.citekey),
      ("Zqec compiled eruption", "natural_disaster", Some("zqec_src2020"))
    )
    assertEquals(EventFixtures.catalogueRefs(ds, "zqec_compiled").map(_.providerId), List("a"))
    assert(EventFixtures.tierAndNames(ds, "zqec_compiled").get._2.contains("Zqecian eruption"), "names added")
    val created = EventFixtures.get(ds, "zztest_b").get.event
    assertEquals((created.kind, created.tier), ("eruption", EventTier.Catalogue))
    // a second save of the same records changes nothing
    assertEquals(
      EventFixtures
        .save(ds, CatalogMerge.place(records, EventFixtures.stored(ds), CatalogMerge.Tolerance(com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(60), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(30))))
        .created,
      0
    )

  test("the events feed: tier, kind groups, the catalogue context and the area"):
    def ids(f: Filter = Filter(), kind: Option[String] = None, more: EventSql.Narrow = EventSql.Narrow()) =
      EventFixtures
        .list(ds, f, PlaceScope.empty, None, kind, None, None, 500, 0, more)
        .events
        .map(_.id)
        .filter(i => i.startsWith("zqec") || i.startsWith("zztest"))
        .toSet
    assertEquals(ids(more = EventSql.Narrow(tier = Some(com.histmersion.domain.catalogue.events.EventSql.Narrow.Tier("catalogue")))), Set("zztest_b"))
    assertEquals(ids(more = EventSql.Narrow(tier = Some(com.histmersion.domain.catalogue.events.EventSql.Narrow.Tier("attested")))), Set("zqec_compiled"))
    assertEquals(ids(kind = Some("natural")), Set("zqec_compiled", "zztest_b"))
    assertEquals(ids(kind = Some("human")), Set.empty[String])
    // a source filter selects the compiled event; with the catalogue context, the catalogue's too
    val bySource = Filter(tags = List(com.histmersion.api.library.Filter.Tags("zqec")), period = Some(Period(com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1000))))
    assertEquals(ids(bySource), Set("zqec_compiled"))
    assertEquals(ids(bySource, more = EventSql.Narrow(context = com.histmersion.domain.catalogue.events.EventSql.Narrow.Context(true))), Set("zqec_compiled", "zztest_b"))
    assertEquals(ids(more = EventSql.Narrow(area = BBox.parse(com.histmersion.api.BBox.Text("150.5,-41,151.5,-39")))), Set("zztest_b"))
    val geo = EventFixtures.geo(
      ds,
      Filter(),
      PlaceScope.empty,
      Some("Zqec"),
      None,
      None,
      None,
      100,
      0.01,
      EventSql.Narrow(tier = Some(com.histmersion.domain.catalogue.events.EventSql.Narrow.Tier("catalogue")))
    )
    assertEquals(geo.events.find(_.id == "zztest_b").map(g => (g.tier, g.size)), Some(("catalogue", Some("VEI 4"))))

  test("findings near an event: within the distance and years, nearest first, the far one left out"):
    val near = EvidenceFixtures.nearFindings(ds, "zqec_compiled", 150, 100, 10)
    assertEquals(near.map(_.id), List("zqec_src2020#0"))
    assertEquals(near.head.yearGap, 0L)
    assert(near.head.km > 10 && near.head.km < 25, near.head.km.toString)
    assertEquals(EvidenceFixtures.nearFindings(ds, "zztest_b", 150, 100, 10), Nil, "1200 BCE is 380 years away")

  test("the full-text index finds the sources naming an event; candidates keep their status"):
    assertEquals(SourceFixtures.naming(ds, "Zqecian eruption", 10), List("zqec_src2020"))
    val c = EventLinkCandidate(
      com.histmersion.api.catalogue.Event.Id("zztest_b"),
      com.histmersion.api.library.Citekey("zqec_src2020"),
      1.1,
      findings = Nil,
      passages = List(EventPassage(com.histmersion.api.catalogue.EventPassage.Start(10), com.histmersion.api.catalogue.EventPassage.End(26), com.histmersion.api.catalogue.EventPassage.Quote("Zqecian eruption"), com.histmersion.api.catalogue.EventPassage.Name("Zqecian eruption"))),
      reasons = List("names it")
    )
    EvidenceFixtures.saveCandidates(ds, "zztest_b", List(c))
    assert(EvidenceFixtures.isChecked(ds, "zztest_b"))
    assertEquals(
      EvidenceFixtures.candidates(ds, "zztest_b").map(x => (x.citekey, x.status, x.passages.head.start)),
      List(("zqec_src2020", "proposed", 10))
    )
    assert(EvidenceFixtures.setStatus(ds, "zztest_b", "zqec_src2020", Event.LinkStatus.Rejected))
    EvidenceFixtures.saveCandidates(ds, "zztest_b", List(c)) // a recompute keeps the rejection
    assertEquals(EvidenceFixtures.candidates(ds, "zztest_b"), Nil)
    assertEquals(EvidenceFixtures.candidates(ds, "zztest_b", withRejected = true).map(_.status), List("rejected"))

  test("an attestation makes a catalogue event attested"):
    val _ = AttestationRows.create(
      ds,
      Attestation.Put(
        com.histmersion.api.library.Citekey("zqec_src2020"),
        AttestationTarget.Event,
        com.histmersion.api.catalogue.Attestation.TargetId("zztest_b"),
        quote = Some(com.histmersion.api.catalogue.Attestation.Quote("Zqecian eruption")),
        charStart = Some(10),
        charEnd = Some(26)
      )
    )
    assertEquals(EventFixtures.tierAndNames(ds, "zztest_b").map(_._1), Some(EventTier.Attested))
    assertEquals(
      EvidenceFixtures.attestingSources(ds, "zztest_b").map(s => (s.citekey, s.via)),
      List(("zqec_src2020", "attestation"))
    )
    val _ = connect(ds)(sql"DELETE FROM attestations WHERE target_id = 'zztest_b'".update.run())
    assertEquals(EventFixtures.tierAndNames(ds, "zztest_b").map(_._1), Some(EventTier.Catalogue))

  test("lead facts count the findings around a catalogue event"):
    val (_, rows) = EvidenceFixtures.leadRows(ds, Sql.of(com.histmersion.util.database.Sql.Text("e.id = ?"), Param.S(com.histmersion.util.database.Param.S.V("zztest_b"))), 300, 500, 5, 0)
    assertEquals(rows.map(r => (r.id, r.tier, r.vei, r.findingsNear)), List(("zztest_b", "catalogue", Some(4), 1)))

  test("an article's catalogue events: in its period, near its sources' findings, large enough"):
    val got = EvidenceFixtures.articleCandidates(ds, List("zqec_src2020"), Some((-1700, -1100)), 300, 4, 7.0, 10)
    assertEquals(got.map(_._1), List("zztest_b"))
    assertEquals(
      EvidenceFixtures.articleCandidates(ds, List("zqec_src2020"), Some((-1700, -1100)), 300, 5, 7.0, 10),
      Nil,
      "VEI 4 is below 5"
    )

  test("parts read are recorded and cleared"):
    EventFixtures.markPart(ds, "zztest", "class:Q1", 3)
    assertEquals(EventFixtures.partsDone(ds, "zztest"), Set("class:Q1"))
    EventFixtures.clearParts(ds, "zztest")
    assertEquals(EventFixtures.partsDone(ds, "zztest"), Set.empty[String])

  test("the places feed carries a site's type: stored, else from its gazetteer's fields"):
    val pt = Some(s"""{"type":"Point","coordinates":[152.0,$Lat]}""")
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(com.histmersion.api.catalogue.Place.Id("zqec_site"), com.histmersion.api.catalogue.Place.Label("Zqec site"), com.histmersion.domain.catalogue.Place.Kind.Site, pt),
          List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zqec site")))
        )
      )
    )
    connect(ds):
      val _ =
        sql"""UPDATE places SET properties = '{"feature_types":"settlement,temple-2"}'::jsonb WHERE id = 'zqec_site'""".update
          .run()
    def siteType: Option[String] =
      val json = io.circe.parser.parse(PlaceFixtures.geoJson(ds, Some("site"), None, 5000)).toOption.get
      json.hcursor
        .downField("features")
        .focus
        .flatMap(_.asArray)
        .toList
        .flatten
        .map(_.hcursor.downField("properties"))
        .find(_.get[String]("id").toOption.contains("zqec_site"))
        .flatMap(_.get[String]("siteType").toOption)
    assertEquals(siteType, Some("sanctuary"))
    // the place's card reads the same type, so it shows the icon the marker has
    assertEquals(PlaceFixtures.get(ds, "zqec_site").flatMap(_.siteType), Some("sanctuary"))
    SiteTypeFixtures.setSiteTypes(ds, List("zqec_site" -> Some("palace")))
    assertEquals(siteType, Some("palace"))
    assertEquals(PlaceFixtures.get(ds, "zqec_site").flatMap(_.siteType), Some("palace"))
