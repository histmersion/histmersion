package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*

/** The small, sharp-edged repo fixes: the `ts_headline` word bounds, the assets contract, and the
  * `findings_flat` upper bound. */
class RepoDetailsSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private val ck = "zzdetail_src"

  private def seed(body: Option[String] = Some("The sea peoples reached the Nile delta.")): String =
    SourceRepo.put(ds, Source(
      citekey = ck, title = "Details", authors = Nil, doi = None, year = Some(2020), venue = None,
      status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil,
      period = Some(Period(-1200, -1100)), summary = Some("sea peoples"), citedByCount = None,
      influentialCitations = None, provenance = None, calibreId = None, ocrMarkdown = body,
      findings = List(Finding(s"$ck#0", ck, "A claim", Some("Nile"), Some(GeoPoint(31.0, 30.0)),
        Some(Period(-1150, -1120)), None, Nil, None))),
      cfg.limits.chunkChars, cfg.places.pointToleranceKm)

  // ── 18: ts_headline needs MinWords < MaxWords ────────────────────────────────────────────────

  test("a tiny snippet budget does not blow up ts_headline (MinWords was a fixed 8)"):
    val _ = seed()
    // limits.snippetWords is snippetChars/6, so a small `snippet-chars` puts MaxWords under 8.
    List(1, 2, 5, 7, 8, 40).foreach { maxWords =>
      val r = SourceRepo.sources(ds, Filter(q = Some("sea peoples"), limit = 5), maxWords)
      assert(r.hits.nonEmpty, s"maxWords=$maxWords returned nothing")
    }

  test("the fragment keeps MinWords strictly below MaxWords at every budget"):
    List(1, 2, 8, 100).foreach { max =>
      val text = Search.bestChunkJoin("x", max).params.collect { case Param.S(v) => v }
        .find(_.contains("MaxWords")).get
      val Some(mn) = """MinWords=(\d+)""".r.findFirstMatchIn(text).map(_.group(1).toInt): @unchecked
      val Some(mx) = """MaxWords=(\d+)""".r.findFirstMatchIn(text).map(_.group(1).toInt): @unchecked
      assert(mn >= 1 && mn < mx, s"MaxWords=$mx MinWords=$mn")
    }

  // ── 21 + 22: the assets contract ─────────────────────────────────────────────────────────────

  private def png(byte: Byte) = AssetRepo.Stored("fig1.png", "image/png", Array[Byte](1, 2, 3, byte), None, None)

  private def updatedAt: String = SourceRepo.load(ds, ck).get.updatedAt.get

  test("add() leaves an asset already stored under that name alone, as its doc says"):
    val _ = seed()
    assertEquals(AssetRepo.replace(ds, ck, List(png(10))), 1)
    val _ = AssetRepo.add(ds, ck, List(png(99)))
    assertEquals(AssetRepo.read(ds, ck, "fig1.png").get._2.toList, List[Byte](1, 2, 3, 10),
      "add() must not overwrite — replace() is the operation that does")

  test("replace() DOES overwrite, which is what a re-conversion needs"):
    val _ = seed()
    val _ = AssetRepo.replace(ds, ck, List(png(10)))
    val _ = AssetRepo.replace(ds, ck, List(png(99)))
    assertEquals(AssetRepo.read(ds, ck, "fig1.png").get._2.toList, List[Byte](1, 2, 3, 99))

  test("changing a source's assets moves its updated_at token"):
    val _ = seed()
    val before = updatedAt
    Thread.sleep(5)                                  // the token has microsecond resolution
    val _ = AssetRepo.replace(ds, ck, List(png(10)))
    val afterReplace = updatedAt
    assertNotEquals(afterReplace, before, "replace() must invalidate the concurrency token")
    Thread.sleep(5)
    val _ = AssetRepo.add(ds, ck, List(AssetRepo.Stored("fig2.png", "image/png",
      Array[Byte](9, 9), None, None)))
    assertNotEquals(updatedAt, afterReplace, "add() must too")

  // ── 23: findings_flat's inclusive upper bound ────────────────────────────────────────────────

  test("findings_flat.period_end is the INCLUSIVE bound, like every other projection"):
    val _ = seed()
    val row = connect(ds):
      sql"SELECT period_start, period_end FROM findings_flat WHERE id = ${ck + "#0"}"
        .query[(Long, Long)].run().head
    assertEquals(row, (-1150L, -1120L), "upper(period) - 1, not upper(period)")
