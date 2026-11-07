package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.search.Search

import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.assets.AssetInfoRow
import com.histmersion.domain.media.assets.NewFigure

import com.histmersion.api.GeoPoint
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.{Migrate, Param}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, Finding, Source}
import com.histmersion.util.database.given

/** The small, sharp-edged repo fixes: the `ts_headline` word bounds, the assets contract, and the `findings_flat` upper
  * bound.
  */
class RepoDetailsSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val ck = com.histmersion.domain.library.Citekey("zzdetail_src")

  private def seed(
      body: Option[Source.OcrMarkdown] = Some(Source.OcrMarkdown("The sea peoples reached the Nile delta."))
  ): Source.UpdatedAt =
    SourceFixtures.put(
      ds,
      Source(
        citekey = com.histmersion.api.library.Citekey(ck.value),
        title = com.histmersion.api.library.Title("Details"),
        authors = (Nil).map(com.histmersion.api.library.AuthorName.apply),
        doi = None,
        year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
        venue = None,
        status = com.histmersion.api.library.Source.Status.Current,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = Some(Period(com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.catalogue.Year(-1100))),
        summary = Some(com.histmersion.api.library.Source.Summary("sea peoples")),
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None,
        ocrMarkdown = body,
        findings = List(
          Finding(
            Finding.Id(s"$ck#0"),
            com.histmersion.api.library.Citekey(ck.value),
            com.histmersion.api.library.Finding.Claim("A claim"),
            Some(com.histmersion.api.library.Finding.Region("Nile")),
            Some(GeoPoint(com.histmersion.api.catalogue.Longitude(31.0), com.histmersion.api.catalogue.Latitude(30.0))),
            Some(Period(com.histmersion.api.catalogue.Year(-1150), com.histmersion.api.catalogue.Year(-1120))),
            None,
            Nil,
            None
          )
        )
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )

  // ── 18: ts_headline needs MinWords < MaxWords ────────────────────────────────────────────────

  test("a tiny snippet budget does not blow up ts_headline (MinWords was a fixed 8)"):
    val _ = seed()
    // limits.snippetWords is snippetChars/6, so a small `snippet-chars` puts MaxWords under 8.
    List(1, 2, 5, 7, 8, 40).map(Search.SnippetWords(_)).foreach { maxWords =>
      val r = SearchFixtures.sources(ds, Filter(q = Some(com.histmersion.api.library.Filter.Q("sea peoples")), limit = com.histmersion.api.library.Filter.Limit(5)), maxWords)
      assert(r.hits.nonEmpty, s"maxWords=$maxWords returned nothing")
    }

  test("the fragment keeps MinWords strictly below MaxWords at every budget"):
    List(1, 2, 8, 100).map(Search.SnippetWords(_)).foreach { max =>
      val text = Search
        .bestChunkJoin(com.histmersion.domain.library.search.Search.BestChunkJoinQ("x"), max)
        .params
        .collect { case Param.S(v) => v.value }
        .find(_.contains("MaxWords"))
        .get
      val Some(mn) = """MinWords=(\d+)""".r.findFirstMatchIn(text).map(_.group(1).toInt): @unchecked
      val Some(mx) = """MaxWords=(\d+)""".r.findFirstMatchIn(text).map(_.group(1).toInt): @unchecked
      assert(mn >= 1 && mn < mx, s"MaxWords=$mx MinWords=$mn")
    }

  // ── 21 + 22: the assets contract ─────────────────────────────────────────────────────────────

  private def png(byte: Byte) = NewFigure(com.histmersion.domain.media.assets.NewFigure.Name("fig1.png"), com.histmersion.domain.media.assets.NewFigure.Mime("image/png"), NewFigure.Bytes(Array[Byte](1, 2, 3, byte)), None, None)

  private def updatedAt: Source.UpdatedAt = SourceFixtures.load(ds, ck).get.updatedAt.get

  test("add() leaves an asset already stored under that name alone, as its doc says"):
    val _ = seed()
    assertEquals(AssetFixtures.replace(ds, ck, List(png(10))), 1)
    val _ = AssetFixtures.add(ds, ck, List(png(99)))
    assertEquals(
      AssetFixtures.read(ds, ck, AssetInfoRow.Name("fig1.png")).get._2.value.toList,
      List[Byte](1, 2, 3, 10),
      "add() must not overwrite — replace() is the operation that does"
    )

  test("replace() DOES overwrite, which is what a re-conversion needs"):
    val _ = seed()
    val _ = AssetFixtures.replace(ds, ck, List(png(10)))
    val _ = AssetFixtures.replace(ds, ck, List(png(99)))
    assertEquals(AssetFixtures.read(ds, ck, AssetInfoRow.Name("fig1.png")).get._2.value.toList, List[Byte](1, 2, 3, 99))

  test("changing a source's assets moves its updated_at token"):
    val _ = seed()
    val before = updatedAt
    Thread.sleep(5) // the token has microsecond resolution
    val _ = AssetFixtures.replace(ds, ck, List(png(10)))
    val afterReplace = updatedAt
    assertNotEquals(afterReplace, before, "replace() must invalidate the concurrency token")
    Thread.sleep(5)
    val _ = AssetFixtures.add(ds, ck, List(NewFigure(com.histmersion.domain.media.assets.NewFigure.Name("fig2.png"), com.histmersion.domain.media.assets.NewFigure.Mime("image/png"), NewFigure.Bytes(Array[Byte](9, 9)), None, None)))
    assertNotEquals(updatedAt, afterReplace, "add() must too")

  // ── 23: findings_flat's inclusive upper bound ────────────────────────────────────────────────

  test("findings_flat.period_end is the INCLUSIVE bound, like every other projection"):
    val _ = seed()
    val row = connect(ds):
      sql"SELECT period_start, period_end FROM findings_flat WHERE id = ${ck.value + "#0"}"
        .query[(Long, Long)]
        .run()
        .head
    assertEquals(row, (-1150L, -1120L), "upper(period) - 1, not upper(period)")
