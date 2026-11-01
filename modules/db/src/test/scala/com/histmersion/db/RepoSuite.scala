package com.histmersion.db

import com.histmersion.domain.library.citations.CitationFixtures

import com.histmersion.domain.library.findings.FindingFixtures
import com.histmersion.domain.library.sources.SourceFixtures
import com.histmersion.domain.library.sources.SourceRepo

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.{Confidence, SourceStatus}

/** Round-trips against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise. */
class RepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.copy(url = testDb.get))
    Migrate.run(d); d

  private val src = Source(
    citekey = "zz_test_source",
    title = "Test",
    authors = List("A", "B"),
    doi = None,
    year = Some(2020),
    venue = None,
    status = SourceStatus.Current,
    topics = List("t"),
    regions = List("Anatolia"),
    tags = List("x"),
    period = Some(Period(-2000, -1500)),
    summary = Some("sum"),
    citedByCount = Some(1),
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      Finding(
        "zz_test_source#0",
        "zz_test_source",
        "claim",
        Some("Anatolia"),
        Some(GeoPoint(32.0, 39.0)),
        Some(Period(-1800, -1700)),
        None,
        List("f"),
        Some(Confidence.High)
      )
    ),
    edges = List(Edge("zz_test_source", "zz_other", "references"))
  )

  test("upsert → get round-trip, int8range bounds inclusive"):
    SourceRepo.upsert(ds, src, AppConfig.load().limits.chunkChars)
    val (d, fs) = SourceFixtures.get(ds, "zz_test_source").get
    assertEquals((d.periodStart, d.periodEnd), (Some(-2000L), Some(-1500L)))
    assertEquals(d.authors, List("A", "B"))
    assertEquals(fs.head.lon, Some(32.0))
    assertEquals((fs.head.periodStart, fs.head.periodEnd), (Some(-1800L), Some(-1700L)))

  test("findingsFor (the map feed): period overlap + bbox"):
    SourceRepo.upsert(ds, src, AppConfig.load().limits.chunkChars)
    val box = Filter(bbox = Some(BBox(30.0, 38.0, 34.0, 40.0)), limit = 10)
    val hit = FindingFixtures.findingsFor(ds, box.copy(period = Some(Period(-1750, -1750))))
    val miss = FindingFixtures.findingsFor(ds, box.copy(period = Some(Period(-1000, -900))))
    assert(hit.exists(_.citekey == "zz_test_source")); assert(!miss.exists(_.citekey == "zz_test_source"))

  test("the map feed says what each finding's source is (core.SourceKinds): its DOI, else its provenance"):
    SourceRepo.upsert(ds, src.copy(doi = Some("10.1007/978-3-030-12345-6_7")), AppConfig.load().limits.chunkChars)
    val box = Filter(bbox = Some(BBox(30.0, 38.0, 34.0, 40.0)), period = Some(Period(-1750, -1750)), limit = 10)
    def kind = FindingFixtures.findingsFor(ds, box).find(_.citekey == "zz_test_source").flatMap(_.sourceKind)
    assertEquals(kind, Some(SourceKinds.Chapter))
    SourceRepo.upsert(ds, src.copy(doi = None, provenance = Some("calibre")), AppConfig.load().limits.chunkChars)
    assertEquals(kind, Some(SourceKinds.Book))
    SourceRepo.upsert(ds, src, AppConfig.load().limits.chunkChars)
    assertEquals(kind, Some(SourceKinds.Unknown))

  test("neighbors lists an outgoing edge to an unknown target, classified as unresolved"):
    SourceRepo.upsert(ds, src, AppConfig.load().limits.chunkChars)
    assert(
      CitationFixtures
        .neighbors(ds, "zz_test_source")
        .exists(n => n.direction == "out" && n.citekey == "zz_other" && n.target == NeighborTarget.Unresolved)
    )
