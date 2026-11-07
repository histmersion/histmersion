package com.histmersion.db

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.util.database.given

/** `ifUpdatedAt` is a compare-and-swap, and it was not one: `patch` read the row, compared the token, then wrote — with
  * nothing stopping a second patch from doing all three in between. Both saw the token they were given, both
  * "succeeded", and the first writer's edit was gone.
  *
  * These tests race two real threads against one row; exactly one must win.
  */
class WriteRaceSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(testDb.get))
    Migrate.run(d); d

  /** Run `a` and `b` at the same moment (a latch, not a hope) and collect both outcomes. */
  private def race[A](a: => A, b: => A): (A, A) =
    val start = CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(2)
    try
      val fa = pool.submit { () => start.await(5, TimeUnit.SECONDS); a }
      val fb = pool.submit { () => start.await(5, TimeUnit.SECONDS); b }
      start.countDown()
      (fa.get(60, TimeUnit.SECONDS), fb.get(60, TimeUnit.SECONDS))
    finally
      pool.shutdownNow()
      val _ = pool.awaitTermination(5, TimeUnit.SECONDS)

  private val ck = "zzrace_src"

  private def seedSource(): String =
    SourceFixtures.put(
      ds,
      Source(
        citekey = com.histmersion.api.library.Citekey(ck),
        title = com.histmersion.api.library.Title("Race"),
        authors = (Nil).map(com.histmersion.api.library.AuthorName.apply),
        doi = None,
        year = None,
        venue = None,
        status = com.histmersion.api.library.Source.Status.Unassessed,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = None,
        summary = Some(com.histmersion.api.library.Source.Summary("before")),
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )

  // Repeated, because ONE round can pass for the wrong reason (the threads simply not overlapping).
  // Without the row lock several of these rounds see two winners.
  private val Rounds = 20

  test("two concurrent patch_source calls against the same token: exactly one wins"):
    (1 to Rounds).foreach { round =>
      val token = seedSource()
      val (a, b) = race(
        SourceFixtures.patch(
          ds,
          ck,
          Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("A")), ifUpdatedAt = Some(token)),
          cfg.limits.chunkChars,
          cfg.places.pointToleranceKm
        ),
        SourceFixtures.patch(
          ds,
          ck,
          Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("B")), ifUpdatedAt = Some(token)),
          cfg.limits.chunkChars,
          cfg.places.pointToleranceKm
        )
      )
      assertEquals(List(a, b).count(_.isRight), 1, s"round $round: a=$a b=$b — a lost update slipped through")
      assertEquals(List(a, b).collectFirst { case Left(e) => e }.get.code, Some(Write.CodePrecondition))
      // …and the stored row is whichever one won, never a mixture
      assert(Set(Some("A"), Some("B")).contains(SourceFixtures.load(ds, ck).get.summary))
    }

  private val slug = "zzrace_article"

  private def seedArticle(): String =
    ArticleFixtures.put(
      ds,
      Article(
        slug = slug,
        title = com.histmersion.api.research.Article.Title("Race"),
        kind = com.histmersion.api.research.Article.KindValue("concept"),
        body = com.histmersion.api.research.Article.Body("before"),
        summary = None,
        tags = Nil,
        regions = Nil,
        period = None
      )
    )

  test("two concurrent patch_article calls against the same token: exactly one wins"):
    (1 to Rounds).foreach { round =>
      val token = seedArticle()
      val (a, b) = race(
        ArticleFixtures.patch(ds, slug, Article.Patch(body = Some(com.histmersion.api.research.Article.Body("A")), ifUpdatedAt = Some(token))),
        ArticleFixtures.patch(ds, slug, Article.Patch(body = Some(com.histmersion.api.research.Article.Body("B")), ifUpdatedAt = Some(token)))
      )
      assertEquals(List(a, b).count(_.isRight), 1, s"round $round: a=$a b=$b — a lost update slipped through")
      assertEquals(List(a, b).collectFirst { case Left(e) => e }.get.code, Some(Write.CodePrecondition))
      assert(Set("A", "B").contains(ArticleFixtures.load(ds, slug).get.body))
    }
