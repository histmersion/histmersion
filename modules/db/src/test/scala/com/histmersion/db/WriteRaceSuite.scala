package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

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
import com.histmersion.config.DbConfig
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.lint.{tolerated, LintRule}

/** `ifUpdatedAt` is a compare-and-swap, and it was not one: `patch` read the row, compared the token, then wrote — with
  * nothing stopping a second patch from doing all three in between. Both saw the token they were given, both
  * "succeeded", and the first writer's edit was gone.
  *
  * These tests race two real threads against one row; exactly one must win.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class WriteRaceSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
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

  private val ck = Citekey("zzrace_src")

  private def seedSource(): Source.UpdatedAt =
    SourceFixtures.put(
      ds,
      Source(
        citekey = api.library.Citekey("zzrace_src"),
        title = api.library.Title("Race"),
        authors = (Nil).map(api.library.AuthorName.apply),
        doi = None,
        year = None,
        venue = None,
        status = Source.Status.Unassessed,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = None,
        summary = Some(Source.Summary("before")),
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
          Source.Patch(summary = Some(Source.Summary("A")), ifUpdatedAt = Some(token)),
          cfg.limits.chunkChars,
          cfg.places.pointToleranceKm
        ),
        SourceFixtures.patch(
          ds,
          ck,
          Source.Patch(summary = Some(Source.Summary("B")), ifUpdatedAt = Some(token)),
          cfg.limits.chunkChars,
          cfg.places.pointToleranceKm
        )
      )
      assertEquals(List(a, b).count(_.isRight), 1, s"round $round: a=$a b=$b — a lost update slipped through")
      assertEquals(List(a, b).collectFirst { case Left(e) => e }.get.code, Some(Write.CodePrecondition))
      // …and the stored row is whichever one won, never a mixture
      assert(Set(Some("A"), Some("B")).contains(SourceFixtures.load(ds, ck).get.summary.map(_.value)))
    }

  private val slug = Slug("zzrace_article")

  private def seedArticle(): Article.UpdatedAt =
    ArticleFixtures.put(
      ds,
      Article(
        slug = Article.Slug("zzrace_article"),
        title = Article.Title("Race"),
        kind = Article.KindValue("concept"),
        body = Article.Body("before"),
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
        ArticleFixtures.patch(ds, slug, Article.Patch(body = Some(Article.Body("A")), ifUpdatedAt = Some(token))),
        ArticleFixtures.patch(ds, slug, Article.Patch(body = Some(Article.Body("B")), ifUpdatedAt = Some(token)))
      )
      assertEquals(List(a, b).count(_.isRight), 1, s"round $round: a=$a b=$b — a lost update slipped through")
      assertEquals(List(a, b).collectFirst { case Left(e) => e }.get.code, Some(Write.CodePrecondition))
      assert(Set("A", "B").contains(ArticleFixtures.load(ds, slug).get.body.value))
    }
