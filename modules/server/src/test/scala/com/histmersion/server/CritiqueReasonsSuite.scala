package com.histmersion.server

import com.histmersion.domain.research.articles.ArticleFixtures

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.values.EdgeKind
import com.histmersion.db.*

/** V091 — the critique checklist's stored reasons (docs/research-review-convergence.md §6.8) against a `*_test`
  * database: written and read through `patch_article`'s one path, refused without a disagreement edge, kept under the
  * edge's direction, removed by an empty reason, and following the article (rename, delete) and both sources (a citekey
  * rename, a delete); the checklist itself with aliases resolved. Skipped without HISTMERSION_TEST_DB_URL.
  */
class CritiqueReasonsSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get))
  private lazy val ds = testPool(cfg.db)
  private lazy val h = ServerWiring(Deps(cfg, ds))

  private val Slug = "zzcrit_bough"
  private val Frazer = "zzcrit1922_golden"
  private val Smith = "zzcrit1987_dying"
  private val Brown = "zzcrit1999_rites"
  private val Lone = "zzcrit2005_lone"

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM article_aliases WHERE alias LIKE 'zzcrit%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzcrit%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzcrit%'".update.run()
      sql"DELETE FROM source_aliases WHERE alias LIKE 'zzcrit%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzcrit%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(): Unit =
    List(Frazer -> 1922, Smith -> 1987, Brown -> 1999, Lone -> 2005).foreach { (ck, y) =>
      assert(h.library.sources.put(ck, SourcePut(title = s"Title of $ck", year = Some(y))).isRight)
    }
    assert(
      h.library.sources
        .patch(Smith, SourcePatch(edges = Some(EdgesPatch(add = List(EdgeInput(Frazer, EdgeKind.Refutes))))))
        .isRight
    )
    assert(
      h.library.sources
        .patch(
          Brown,
          SourcePatch(edges =
            Some(EdgesPatch(add = List(EdgeInput(Frazer, EdgeKind.Challenges), EdgeInput(Frazer, EdgeKind.Updates))))
          )
        )
        .isRight
    )
    val body =
      s"The dying god [[$Frazer]] is a type; [[$Smith]] refutes it.\n\n## Disagreements\n\nRites differ [[$Frazer]]."
    assert(h.research.articles.put(Slug, ArticlePut("The golden bough", "concept", body)).isRight)

  private def patch(rs: CritiqueReason*) = h.research.articles.patch(Slug, ArticlePatch(critiqueReasons = rs.toList))
  private def stored = ArticleFixtures.critiques(ds, Slug).map(r => (r.src, r.dst, r.reason, r.by))

  test("set, read, refuse, normalise the direction, remove; the article's row is not touched"):
    seed()
    assertEquals(
      h.research.articles
        .critiques(Slug, h.research.articles.get(Slug).toOption.get.article.body)
        .map(p => (p.ref.wire, p.kinds, p.state.wire)),
      List(
        (s"$Smith>$Frazer", List("refutes"), "addressed"),
        (s"$Brown>$Frazer", List("challenges", "updates"), "open")
      )
    )
    val token = h.research.articles.get(Slug).toOption.get.article.updatedAt
    // the pair named the other way round is kept under the edge's direction; an explicit setter is kept
    assert(patch(CritiqueReason(Frazer, Brown, " Cited for the\nrites only. ")).isRight)
    assert(patch(CritiqueReason(Smith, Frazer, "x", Some("skeptic (agent run abcd1234)"))).isRight)
    assertEquals(
      stored,
      List(
        (Smith, Frazer, "x", Some("skeptic (agent run abcd1234)")),
        (Brown, Frazer, "Cited for the rites only.", Some("person"))
      )
    )
    assertEquals(h.research.articles.get(Slug).toOption.get.article.updatedAt, token)
    assertEquals(h.research.articles.versions(Slug).toOption.get, Nil, "no version for a reason alone")
    assertEquals(h.research.articles.openCritiques(Slug), Some(0))
    // refused, nothing written: no edge, an unknown citekey, a reason too long — and the whole patch with them
    assert(
      patch(CritiqueReason(Lone, Frazer, "y")).left
        .exists(_.message.contains("no challenges/refutes/updates/superseded_by edge"))
    )
    assert(patch(CritiqueReason("zzcrit0000_ghost", Frazer, "y")).left.exists(_.message.contains("is not a citekey")))
    assert(patch(CritiqueReason(Brown, Frazer, "z" * 501)).isLeft)
    assert(
      h.research.articles
        .patch(
          Slug,
          ArticlePatch(title = Some("Changed"), critiqueReasons = List(CritiqueReason(Lone, Frazer, "y")))
        )
        .isLeft
    )
    assertEquals(h.research.articles.get(Slug).toOption.get.article.title, "The golden bough")
    // a reason with a body edit: both in one transaction (the body is a new version)
    assert(
      h.research.articles
        .patch(
          Slug,
          ArticlePatch(append = Some(s"More [[$Frazer]]."), critiqueReasons = List(CritiqueReason(Smith, Frazer, "")))
        )
        .isRight
    )
    assertEquals(stored.map(_._1), List(Brown))
    assertEquals(h.research.articles.versions(Slug).toOption.get.size, 1)

  test("the reasons follow the article (rename, delete) and both sources (citekey rename, delete)"):
    seed()
    assert(patch(CritiqueReason(Brown, Frazer, "Cited for the rites only.")).isRight)
    // a citekey rename carries it; the old citekey still names the pair
    assert(h.library.sources.rename(Brown, RenameRequest("zzcrit1999_ritesb")).isRight)
    assertEquals(stored.map(_._1), List("zzcrit1999_ritesb"))
    assert(patch(CritiqueReason(Brown, Frazer, "Renamed, still the same.")).isRight)
    assertEquals(stored.map(r => (r._1, r._3)), List(("zzcrit1999_ritesb", "Renamed, still the same.")))
    // the article's rename carries it
    assert(h.research.articles.rename(Slug, LabelRename("zzcrit_bough2")).isRight)
    assertEquals(ArticleFixtures.critiques(ds, "zzcrit_bough2").size, 1)
    assertEquals(h.research.articles.openCritiques("zzcrit_bough2"), Some(0))
    // the source's delete drops it
    assert(h.library.sources.delete("zzcrit1999_ritesb", SourceDeleteRequest(apply = true)).isRight)
    assertEquals(ArticleFixtures.critiques(ds, "zzcrit_bough2"), Nil)
    // and the article's delete drops the rest
    assert(
      h.research.articles
        .patch("zzcrit_bough2", ArticlePatch(critiqueReasons = List(CritiqueReason(Smith, Frazer, "x"))))
        .isRight
    )
    assert(h.research.articles.delete("zzcrit_bough2").isRight)
    assertEquals(
      connect(ds)(
        sql"SELECT count(*)::int FROM article_critique_reasons WHERE slug LIKE 'zzcrit%'".query[Int].run().head
      ),
      0
    )

  test("the checklist resolves an old citekey and a short form in the body as the page does"):
    seed()
    assert(h.library.sources.rename(Smith, RenameRequest("zzcrit1987_dyingb")).isRight)
    // the body still says [[zzcrit1987_dying]] (an alias now) and the short form [[zzcrit1922]]
    val body = s"The type [[zzcrit1922]] and its critic [[$Smith]].\n\nRites [[$Brown]]."
    assertEquals(
      h.research.articles.critiques(Slug, body).map(p => (p.ref.wire, p.state.wire, p.src.cited, p.dst.cited)),
      List((s"zzcrit1987_dyingb>$Frazer", "addressed", true, true), (s"$Brown>$Frazer", "open", true, true))
    )
