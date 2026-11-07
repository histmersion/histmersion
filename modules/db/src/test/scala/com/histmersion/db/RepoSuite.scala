package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.citations.CitationFixtures

import com.histmersion.domain.library.findings.FindingFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.api.{BBox, GeoPoint}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Latitude, Longitude, Period, Year}
import com.histmersion.api.library.{Edge, Filter, Finding, NeighborTarget, Source}
import com.histmersion.util.database.given

/** Round-trips against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise. */
class RepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(testDb.get))
    Migrate.run(d); d

  private val src = Source(
    citekey = com.histmersion.api.library.Citekey("zz_test_source"),
    title = com.histmersion.api.library.Title("Test"),
    authors = (List("A", "B")).map(com.histmersion.api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
    venue = None,
    status = com.histmersion.api.library.Source.Status.Current,
    topics = List(com.histmersion.api.library.Source.Topics("t")),
    regions = List(com.histmersion.api.library.Source.Regions("Anatolia")),
    tags = List(com.histmersion.api.library.Source.Tags("x")),
    period = Some(Period(com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1500))),
    summary = Some(com.histmersion.api.library.Source.Summary("sum")),
    citedByCount = Some(com.histmersion.api.library.Source.CitedByCount(1)),
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      Finding(
        com.histmersion.api.library.Finding.Id("zz_test_source#0"),
        com.histmersion.api.library.Citekey("zz_test_source"),
        com.histmersion.api.library.Finding.Claim("claim"),
        Some(com.histmersion.api.library.Finding.Region("Anatolia")),
        Some(GeoPoint(Longitude(32.0), Latitude(39.0))),
        Some(Period(com.histmersion.api.catalogue.Year(-1800), com.histmersion.api.catalogue.Year(-1700))),
        None,
        List(com.histmersion.api.library.Finding.Tags("f")),
        Some(Confidence.High)
      )
    ),
    edges = List(Edge(com.histmersion.api.library.Citekey("zz_test_source"), com.histmersion.api.library.Citekey("zz_other"), com.histmersion.api.library.Edge.Kind.References))
  )

  test("upsert → get round-trip, int8range bounds inclusive"):
    SourceFixtures.upsert(ds, src, AppConfig.load().limits.chunkChars.value)
    val (d, fs) = SourceFixtures.get(ds, "zz_test_source").get
    assertEquals((d.periodStart, d.periodEnd), (Some(-2000L), Some(-1500L)))
    assertEquals(d.authors, List("A", "B"))
    assertEquals(fs.head.lon, Some(32.0))
    assertEquals((fs.head.periodStart, fs.head.periodEnd), (Some(-1800L), Some(-1700L)))

  test("findingsFor (the map feed): period overlap + bbox"):
    SourceFixtures.upsert(ds, src, AppConfig.load().limits.chunkChars.value)
    val box = Filter(bbox = Some(BBox(Longitude(30.0), Latitude(38.0), Longitude(34.0), Latitude(40.0))), limit = 10)
    val hit = FindingFixtures.findingsFor(ds, box.modify(_.period).setTo(Some(Period(Year(-1750), Year(-1750)))))
    val miss = FindingFixtures.findingsFor(ds, box.modify(_.period).setTo(Some(Period(Year(-1000), Year(-900)))))
    assert(hit.exists(_.citekey.value == "zz_test_source")); assert(!miss.exists(_.citekey.value == "zz_test_source"))

  test("the map feed says what each finding's source is (core.SourceKinds): its DOI, else its provenance"):
    SourceFixtures.upsert(ds, src.modify(_.doi).setTo(Some("10.1007/978-3-030-12345-6_7")), AppConfig.load().limits.chunkChars.value)
    val box = Filter(bbox = Some(BBox(Longitude(30.0), Latitude(38.0), Longitude(34.0), Latitude(40.0))), period = Some(Period(com.histmersion.api.catalogue.Year(-1750), com.histmersion.api.catalogue.Year(-1750))), limit = 10)
    def kind = FindingFixtures.findingsFor(ds, box).find(_.citekey.value == "zz_test_source").flatMap(_.sourceKind)
    assertEquals(kind, Some(Source.Kinds.Chapter))
    SourceFixtures.upsert(ds, src.modify(_.doi).setTo(None).modify(_.provenance).setTo(Some("calibre")), AppConfig.load().limits.chunkChars.value)
    assertEquals(kind, Some(Source.Kinds.Book))
    SourceFixtures.upsert(ds, src, AppConfig.load().limits.chunkChars.value)
    assertEquals(kind, Some(Source.Kinds.Unknown))

  test("neighbors lists an outgoing edge to an unknown target, classified as unresolved"):
    SourceFixtures.upsert(ds, src, AppConfig.load().limits.chunkChars.value)
    assert(
      CitationFixtures
        .neighbors(ds, "zz_test_source")
        .exists(n => n.direction.value == "out" && n.citekey.value == "zz_other" && n.target == NeighborTarget.Unresolved)
    )
