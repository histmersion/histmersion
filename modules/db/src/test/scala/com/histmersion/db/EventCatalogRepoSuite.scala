package com.histmersion
package db
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

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
import com.histmersion.util.database.Migrate
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
import com.histmersion.domain.catalogue.Place.Id as PlaceId
import com.histmersion.domain.catalogue.Place.Kind.Wire as KindWire
import com.histmersion.api.catalogue.Attestation
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.domain.catalogue.events.CatalogMerge
import com.histmersion.domain.catalogue.events.EventSql

/** #1449 against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): V085's kinds and tables, writing placed
  * catalogue records (a compiled event matched, never overwritten), the tier and the kind-group / tier / area /
  * catalogue-context filters of the events feed, findings near an event, the sources naming it, stored candidates and
  * their statuses, lead facts, an article's catalogue events, and site types in the places feed. Fixtures sit at
  * 150–152°E, 40°S; ids start with `zqec`.
  */
class EventCatalogRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val Lat = Latitude(-40.0)

  private val src = Source(
    citekey = Citekey("zqec_src2020"),
    title = api.library.Title("Zqec volcanology"),
    authors = (List("A")).map(api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(Year.apply),
    venue = None,
    status = Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = List(Source.Tags("zqec")),
    period = Some(Period(Year(-1700), Year(-1500))),
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      Finding(
        Finding.Id("zqec_src2020#0"),
        Citekey("zqec_src2020"),
        Finding.Claim("ash layer"),
        None,
        Some(GeoPoint(Longitude(150.2), Lat)),
        Some(Period(Year(-1620), Year(-1600))),
        None,
        Nil,
        Some(Confidence.High)
      ),
      Finding(
        Finding.Id("zqec_src2020#1"),
        Citekey("zqec_src2020"),
        Finding.Claim("far away"),
        None,
        Some(GeoPoint(Longitude(170.0), Lat)),
        Some(Period(Year(-1620), Year(-1600))),
        None,
        Nil,
        Some(Confidence.High)
      )
    ),
    edges = Nil,
    ocrMarkdown = Some(
      Source.OcrMarkdown("# Zqec\n\nThe Zqecian eruption buried the town. Later the Zqecian eruption was dated.")
    )
  )

  private def rec(
      p: CatalogRecord.Provider,
      id: CatalogRecord.ProviderId,
      kind: CatalogRecord.Kind,
      y: Year,
      lon: Longitude,
      vei: Option[CatalogRecord.Vei] = None
  ) =
    CatalogRecord(
      p,
      id,
      CatalogRecord.Url(s"https://example.org/$id"),
      CatalogRecord.Label(s"Zqec $kind $id"),
      kind,
      TimeSpan(Some(y), Some(y), Some(y), Some(y)),
      Some(Lat),
      Some(lon),
      vei = vei,
      names = List(CatalogRecord.Names("Zqecian eruption"))
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
        BEventRow.Id("zqec_compiled"),
        EventPut(
          EventSummary.Label("Zqec compiled eruption"),
          EventSummary.Kind("natural_disaster"),
          TimeSpan(Some(Year(-1615)), None, None, Some(Year(-1605))),
          geometry = Some(EventPut.Geometry(s"""{"type":"Point","coordinates":[150.0,$Lat]}""")),
          citekey = Some(Citekey("zqec_src2020"))
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
    val stored = EventFixtures.stored(ds).filter(_.id.value.startsWith("zqec"))
    val records = List(
      rec(
        CatalogRecord.Provider("zztest"),
        CatalogRecord.ProviderId("a"),
        CatalogRecord.Kind("natural_disaster"),
        Year(-1610),
        Longitude(150.01),
        Some(CatalogRecord.Vei(6))
      ), // the compiled event's kind is not eruption: new
      rec(
        CatalogRecord.Provider("zztest"),
        CatalogRecord.ProviderId("b"),
        CatalogRecord.Kind("eruption"),
        Year(-1200),
        Longitude(151.0),
        Some(CatalogRecord.Vei(4))
      )
    )
    val placed = CatalogMerge.place(
      records,
      stored,
      CatalogMerge.Tolerance(CatalogMerge.Tolerance.Km(60), CatalogMerge.Tolerance.Years(30))
    )
    // the first is natural_disaster like the compiled one, at its place and time: matched
    assertEquals(placed.map(_._2.eventId), List("zqec_compiled", "zztest_b"))
    val saved = EventFixtures.save(ds, placed)
    assertEquals((saved.created, saved.rows), (1, 2))
    val compiled = EventFixtures.get(ds, BEventRow.Id("zqec_compiled")).get.event
    assertEquals(
      (compiled.label, compiled.kind, compiled.citekey),
      ("Zqec compiled eruption", "natural_disaster", Some("zqec_src2020"))
    )
    assertEquals(EventFixtures.catalogueRefs(ds, Event.Id("zqec_compiled")).map(_.providerId), List("a"))
    assert(
      EventFixtures.tierAndNames(ds, BEventRow.Id("zqec_compiled")).get._2.contains("Zqecian eruption"),
      "names added"
    )
    val created = EventFixtures.get(ds, BEventRow.Id("zztest_b")).get.event
    assertEquals((created.kind, created.tier), ("eruption", EventTier.Catalogue))
    // a second save of the same records changes nothing
    assertEquals(
      EventFixtures
        .save(
          ds,
          CatalogMerge.place(
            records,
            EventFixtures.stored(ds),
            CatalogMerge.Tolerance(CatalogMerge.Tolerance.Km(60), CatalogMerge.Tolerance.Years(30))
          )
        )
        .created,
      0
    )

  test("the events feed: tier, kind groups, the catalogue context and the area"):
    def ids(f: Filter = Filter(), kind: Option[String] = None, more: EventSql.Narrow = EventSql.Narrow()) =
      EventFixtures
        .list(
          ds,
          f,
          PlaceScope.empty,
          None,
          kind.map(EventSelection.Kind.apply),
          None,
          None,
          PageSize(500),
          Offset(0),
          more
        )
        .events
        .map(_.id.value)
        .filter(i => i.startsWith("zqec") || i.startsWith("zztest"))
        .toSet
    assertEquals(ids(more = EventSql.Narrow(tier = Some(EventSql.Narrow.Tier("catalogue")))), Set("zztest_b"))
    assertEquals(ids(more = EventSql.Narrow(tier = Some(EventSql.Narrow.Tier("attested")))), Set("zqec_compiled"))
    assertEquals(ids(kind = Some("natural")), Set("zqec_compiled", "zztest_b"))
    assertEquals(ids(kind = Some("human")), Set.empty[String])
    // a source filter selects the compiled event; with the catalogue context, the catalogue's too
    val bySource = Filter(tags = List(Filter.Tags("zqec")), period = Some(Period(Year(-2000), Year(-1000))))
    assertEquals(ids(bySource), Set("zqec_compiled"))
    assertEquals(
      ids(bySource, more = EventSql.Narrow(context = EventSql.Narrow.Context(true))),
      Set("zqec_compiled", "zztest_b")
    )
    assertEquals(ids(more = EventSql.Narrow(area = BBox.parse(BBox.Text("150.5,-41,151.5,-39")))), Set("zztest_b"))
    val geo = EventFixtures.geo(
      ds,
      Filter(),
      PlaceScope.empty,
      Some(EventSelection.Text("Zqec")),
      None,
      None,
      None,
      PageSize(100),
      EventFixtures.GeoTolerance(0.01),
      EventSql.Narrow(tier = Some(EventSql.Narrow.Tier("catalogue")))
    )
    assertEquals(
      geo.events.find(_.id.value == "zztest_b").map(g => (g.tier, g.size)),
      Some(("catalogue", Some("VEI 4")))
    )

  test("findings near an event: within the distance and years, nearest first, the far one left out"):
    val near = EvidenceFixtures.nearFindings(
      ds,
      Event.Id("zqec_compiled"),
      CatalogMerge.Tolerance.Km(150),
      CatalogMerge.Tolerance.Years(100),
      PageSize(10)
    )
    assertEquals(near.map(_.id), List("zqec_src2020#0"))
    assertEquals(near.head.yearGap, 0L)
    assert(near.head.km.value > 10 && near.head.km.value < 25, near.head.km.toString)
    assertEquals(
      EvidenceFixtures.nearFindings(
        ds,
        Event.Id("zztest_b"),
        CatalogMerge.Tolerance.Km(150),
        CatalogMerge.Tolerance.Years(100),
        PageSize(10)
      ),
      Nil,
      "1200 BCE is 380 years away"
    )

  test("the full-text index finds the sources naming an event; candidates keep their status"):
    assertEquals(
      SourceFixtures.naming(ds, SourceFixtures.Name("Zqecian eruption"), SourceFixtures.Limit(10)),
      List("zqec_src2020")
    )
    val c = EventLinkCandidate(
      api.catalogue.Event.Id("zztest_b"),
      Citekey("zqec_src2020"),
      EventLinkCandidate.Score(1.1),
      findings = Nil,
      passages = List(
        EventPassage(
          EventPassage.Start(10),
          EventPassage.End(26),
          EventPassage.Quote("Zqecian eruption"),
          EventPassage.Name("Zqecian eruption")
        )
      ),
      reasons = List(EventLinkCandidate.Reasons("names it"))
    )
    EvidenceFixtures.saveCandidates(ds, BEventRow.Id("zztest_b"), List(c))
    assert(EvidenceFixtures.isChecked(ds, BEventRow.Id("zztest_b")))
    assertEquals(
      EvidenceFixtures.candidates(ds, BEventRow.Id("zztest_b")).map(x => (x.citekey, x.status, x.passages.head.start)),
      List(("zqec_src2020", "proposed", 10))
    )
    assert(
      EvidenceFixtures.setStatus(
        ds,
        BEventRow.Id("zztest_b"),
        domain.library.Citekey("zqec_src2020"),
        BCandidateRow.Status(Event.LinkStatus.Rejected)
      )
    )
    EvidenceFixtures.saveCandidates(ds, BEventRow.Id("zztest_b"), List(c)) // a recompute keeps the rejection
    assertEquals(EvidenceFixtures.candidates(ds, BEventRow.Id("zztest_b")), Nil)
    assertEquals(
      EvidenceFixtures.candidates(ds, BEventRow.Id("zztest_b"), withRejected = true).map(_.status),
      List("rejected")
    )

  test("an attestation makes a catalogue event attested"):
    val _ = AttestationRows.create(
      ds,
      Attestation.Put(
        Citekey("zqec_src2020"),
        Attestation.TargetType(AttestationTarget.Event),
        Attestation.TargetId("zztest_b"),
        quote = Some(Attestation.Quote("Zqecian eruption")),
        charStart = Some(Attestation.CharStart(10)),
        charEnd = Some(Attestation.CharEnd(26))
      )
    )
    assertEquals(EventFixtures.tierAndNames(ds, BEventRow.Id("zztest_b")).map(_._1), Some(EventTier.Attested))
    assertEquals(
      EvidenceFixtures.attestingSources(ds, Event.Id("zztest_b")).map(s => (s.citekey, s.via)),
      List(("zqec_src2020", "attestation"))
    )
    val _ = connect(ds)(sql"DELETE FROM attestations WHERE target_id = 'zztest_b'".update.run())
    assertEquals(EventFixtures.tierAndNames(ds, BEventRow.Id("zztest_b")).map(_._1), Some(EventTier.Catalogue))

  test("lead facts count the findings around a catalogue event"):
    val (_, rows) = EvidenceFixtures.leadRows(
      ds,
      sql"e.id = ${"zztest_b"}",
      CatalogMerge.Tolerance.Km(300),
      CatalogMerge.Tolerance.Years(500),
      PageSize(5),
      Offset(0)
    )
    assertEquals(rows.map(r => (r.id, r.tier, r.vei, r.findingsNear)), List(("zztest_b", "catalogue", Some(4), 1)))

  test("an article's catalogue events: in its period, near its sources' findings, large enough"):
    val got = EvidenceFixtures.articleCandidates(
      ds,
      List(domain.library.Citekey("zqec_src2020")),
      Some((DeepTime.SignedYear(-1700L), DeepTime.SignedYear(-1100L))),
      CatalogMerge.Tolerance.Km(300),
      CatalogRecord.Vei(4),
      CatalogRecord.Magnitude(7.0),
      PageSize(10)
    )
    assertEquals(got.map(_._1), List("zztest_b"))
    assertEquals(
      EvidenceFixtures.articleCandidates(
        ds,
        List(domain.library.Citekey("zqec_src2020")),
        Some((DeepTime.SignedYear(-1700L), DeepTime.SignedYear(-1100L))),
        CatalogMerge.Tolerance.Km(300),
        CatalogRecord.Vei(5),
        CatalogRecord.Magnitude(7.0),
        PageSize(10)
      ),
      Nil,
      "VEI 4 is below 5"
    )

  test("parts read are recorded and cleared"):
    EventFixtures.markPart(ds, CatalogRecord.Provider("zztest"), EventFixtures.PartName("class:Q1"), RowCount(3))
    assertEquals(EventFixtures.partsDone(ds, CatalogRecord.Provider("zztest")), Set("class:Q1"))
    EventFixtures.clearParts(ds, CatalogRecord.Provider("zztest"))
    assertEquals(EventFixtures.partsDone(ds, CatalogRecord.Provider("zztest")), Set.empty[String])

  test("the places feed carries a site's type: stored, else from its gazetteer's fields"):
    val pt = Some(Place.Geometry(s"""{"type":"Point","coordinates":[152.0,$Lat]}"""))
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(Place.Id("zqec_site"), Place.Label("Zqec site"), Place.KindValue(Place.Kind.Site), pt),
          List(Place.Name(Place.Name.Value("Zqec site")))
        )
      )
    )
    connect(ds):
      val _ =
        sql"""UPDATE places SET properties = '{"feature_types":"settlement,temple-2"}'::jsonb WHERE id = 'zqec_site'""".update
          .run()
    def siteType: Option[String] =
      val json = RawJson
        .parse(PlaceFixtures.geoJson(ds, Some(KindWire("site")), None, PlaceFixtures.Limit(5000)).value)
        .toOption
        .get
      json.cursor
        .downField("features")
        .focus
        .flatMap(_.asArray)
        .toList
        .flatten
        .map(_.cursor.downField("properties"))
        .find(_.get[String]("id").toOption.contains("zqec_site"))
        .flatMap(_.get[String]("siteType").toOption)
    assertEquals(siteType, Some("sanctuary"))
    // the place's card reads the same type, so it shows the icon the marker has
    assertEquals(PlaceFixtures.get(ds, PlaceId("zqec_site")).flatMap(_.siteType), Some("sanctuary"))
    SiteTypeFixtures.setSiteTypes(ds, List(PlaceId("zqec_site") -> Some(SiteTypeFixtures.SiteType("palace"))))
    assertEquals(siteType, Some("palace"))
    assertEquals(PlaceFixtures.get(ds, PlaceId("zqec_site")).flatMap(_.siteType), Some("palace"))
