package com.histmersion.db

import com.histmersion.core.*

/** Round-trips against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise. */
class RepoSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d    = Db.dataSource(base.copy(url = testDb.get))
    Migrate.run(d); d

  private val src = Source(
    citekey = "zz_test_source", title = "Test", authors = List("A", "B"), doi = None, year = Some(2020),
    venue = None, status = SourceStatus.Current, topics = List("t"), regions = List("Anatolia"),
    tags = List("x"), period = Some(Period(-2000, -1500)), summary = Some("sum"), citedByCount = Some(1),
    influentialCitations = None, provenance = None, calibreId = None,
    findings = List(Finding("zz_test_source#0", "zz_test_source", "claim", Some("Anatolia"),
      Some(GeoPoint(32.0, 39.0)), Some(Period(-1800, -1700)), None, List("f"), Some("high"))),
    edges = List(Edge("zz_test_source", "zz_other", "references")))

  test("upsert → get round-trip, int8range bounds inclusive"):
    SourceRepo.upsert(ds, src, AppConfig.load().limits.chunkChars)
    val (d, fs) = SourceRepo.get(ds, "zz_test_source").get
    assertEquals((d.periodStart, d.periodEnd), (Some(-2000L), Some(-1500L)))
    assertEquals(d.authors, "A, B")
    assertEquals(fs.head.lon, Some(32.0))
    assertEquals((fs.head.periodStart, fs.head.periodEnd), (Some(-1800L), Some(-1700L)))

  test("findingsFor (the map feed): period overlap + bbox"):
    SourceRepo.upsert(ds, src, AppConfig.load().limits.chunkChars)
    val box  = Filter(bbox = Some(BBox(30.0, 38.0, 34.0, 40.0)), limit = 10)
    val hit  = SourceRepo.findingsFor(ds, box.copy(period = Some(Period(-1750, -1750))))
    val miss = SourceRepo.findingsFor(ds, box.copy(period = Some(Period(-1000, -900))))
    assert(hit.exists(_.citekey == "zz_test_source")); assert(!miss.exists(_.citekey == "zz_test_source"))

  test("neighbors lists outgoing edge even to unknown target"):
    SourceRepo.upsert(ds, src, AppConfig.load().limits.chunkChars)
    assert(SourceRepo.neighbors(ds, "zz_test_source").exists(n => n.direction == "out" && n.citekey == "zz_other"))
