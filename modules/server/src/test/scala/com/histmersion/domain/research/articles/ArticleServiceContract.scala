package com.histmersion
package domain.research.articles

import com.histmersion.api.Problem
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Write
import com.histmersion.domain.Caller
import com.histmersion.domain.research.{ResearchStores, ResearchStoreSuite, Slug}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.{Article, Backlink, CritiqueReason}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Filter
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.Coverage

/** What research's articles answer, the same on the database and in memory: an article written whole and merged from
  * patches with its token and versions, its link graph, a critique reason refused when no source is stored, renamed
  * with its old slug kept and its links followed, deleted with its versions and old slugs.
  */
abstract class ArticleServiceContract extends ResearchStoreSuite:

  private val Me = Caller.Local
  private val TokenRe = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z".r

  private def article(slug: Slug, title: Article.Title, body: Article.Body): Article =
    Article(
      Article.Slug(slug.value),
      title,
      Article.KindValue("concept"),
      body,
      Some(Article.SummaryText(s"On $title.")),
      List(Article.Tags("zr-art")),
      List(Article.Regions("Zrland")),
      Some(Period(Year(-1600), Year(-1400))),
      None,
      Some(Article.Filter(tags = List(Filter.Tags("zr-oracle")))),
      Map(Citekey("zrdelta2010_survey") -> Problem("Region-wide."))
    )

  private val Walls = Slug("zr_walls")
  private val Harbour = Slug("zr_harbour")
  private val Index = Slug("zr_index")

  stest("an article put and read back: its record and token, its uid, both directions of its links") { s =>
    val token = s.articles.put(
      article(Walls, Article.Title("Walls"), Article.Body("## Overview\n\nThe walls [[zralpha1990_walls]].\n")),
      Me
    )
    assert(TokenRe.matches(token.value), token)
    val _ = s.articles.put(
      article(Index, Article.Title("Index"), Article.Body(s"- [[$Walls]]\n- [[$Harbour]]\n- [[$Index]]\n")),
      Me
    )
    val loaded = s.articles.load(Walls).get
    assertEquals(loaded.updatedAt, Some(token))
    assertEquals(loaded.filter, Some(Article.Filter(tags = List(Filter.Tags("zr-oracle")))))
    assertEquals(loaded.reasons, Map("zrdelta2010_survey" -> "Region-wide."))
    assertEquals(loaded.period, Some(Period(Year(-1600), Year(-1400))))
    val view = s.articles.view(Walls).get
    assertEquals(view.article.updatedAt, Some(token))
    assert(view.article.uid.isDefined)
    assertEquals(
      view.backlinks,
      List(Backlink(Backlink.Kind("article"), Backlink.Id("zr_index"), Some(Backlink.Title("Index"))))
    )
    assertEquals(view.sourcesCited, Nil)
    assertEquals(s.articles.view(Index).get.backlinks, Nil)
    assertEquals(s.articles.slugs, List(Walls, Index).sortBy[String](_.value))
    assert(s.articles.exists(Walls))
    assert(!s.articles.exists(Slug("zr_nothing")))
    assertEquals(
      s.articles.refs(List(Index, Slug.parse("ZR_WALLS").toOption.get, Slug("zr_nothing"))).map(_.slug.value: String),
      List(Index, Walls)
    )
    assertEquals(s.articles.updatedAtMillis.keySet, Set(Walls, Index))
    assertEquals(s.articles.backlinksForSource(com.histmersion.domain.library.Citekey("zralpha1990_walls")), Nil)
    assertEquals(s.articles.citing(List(com.histmersion.domain.library.Citekey("zralpha1990_walls")), PageSize(5)), Nil)
    assertEquals(s.articles.articlesByGoal, Map.empty)
    assertEquals(s.articles.canonical(Walls), Walls)
    assertEquals(s.articles.load(Slug("zr_nothing")), None)
    assertEquals(s.articles.view(Slug("zr_nothing")), None)
  }

  stest("a patch merges what it names and records the version it replaces; a stale token, a bad kind and no article") {
    s =>
      val first =
        s.articles.put(article(Walls, Article.Title("Walls"), Article.Body("## Overview\n\nThe walls.\n")), Me)
      val second = s.articles.patch(
        Walls,
        Article
          .Patch(append = Some(Article.Patch.Append("A gate.")), section = Some(Article.Patch.Section("Overview"))),
        Me
      )
      assert(second.isRight, second)
      assertNotEquals(second.toOption, Some(first))
      assertEquals(s.articles.load(Walls).map(_.body), Some("## Overview\n\nThe walls.\n\nA gate."))
      val third = s.articles.patch(
        Walls,
        Article.Patch(title = Some(Article.Title("City walls")), ifUpdatedAt = second.toOption),
        Me
      )
      assert(third.isRight, third)
      assertEquals(s.articles.load(Walls).map(_.title), Some("City walls"))
      assertEquals(
        s.articles
          .patch(Walls, Article.Patch(title = Some(Article.Title("x")), ifUpdatedAt = Some(first)), Me)
          .left
          .map(_.code),
        Left(Some(Write.CodePrecondition))
      )
      assertEquals(
        s.articles.patch(Walls, Article.Patch(kind = Some(Article.Kind.Wire("Not A Kind!"))), Me).left.map(_.code),
        Left(Some(Write.CodeInvalid))
      )
      assertEquals(
        s.articles.patch(Slug("zr_nothing"), Article.Patch(title = Some(Article.Title("x"))), Me).left.map(_.code),
        Left(Some(Write.CodeNotFound))
      )
      val versions = s.articles.versions(Walls)
      assertEquals(versions.size, 2)
      assertEquals(versions.map(_.wordCount), List(6, 5))
      assertEquals(versions.map(_.updatedAt.value), List(second.toOption.get.value, first.value))
      val oldest = s.articles.version(Walls, ArticleRepo.VersionId(versions.last.id.value)).get
      assertEquals(oldest.body, "## Overview\n\nThe walls.\n")
      assertEquals(s.articles.version(Walls, ArticleRepo.VersionId(versions.last.id.value + 1000)), None)
      assertEquals(s.articles.version(Harbour, ArticleRepo.VersionId(versions.last.id.value)), None)
      assertEquals(s.articles.latestVersion(Walls).map(_.value: Long), Some(versions.map(_.id.value).max))
      assertEquals(s.articles.latestVersion(Harbour), None)
  }

  stest("a critique reason names two sources by citekey: with none stored it is refused and nothing is written") { s =>
    val token = s.articles.put(article(Walls, Article.Title("Walls"), Article.Body("## Overview\n\nThe walls.\n")), Me)
    val refused = s.articles.patch(
      Walls,
      Article.Patch(
        title = Some(Article.Title("x")),
        critiqueReasons =
          List(CritiqueReason(Citekey("zra"), Citekey("zrb"), Problem("Not about the walls.")))
      ),
      Me
    )
    assertEquals(refused.left.map(_.message), Left("critique reason zra>zrb: 'zra' is not a citekey"))
    val long = s.articles.patch(
      Walls,
      Article.Patch(critiqueReasons =
        List(CritiqueReason(Citekey("zra"), Citekey("zrb"), Problem("w" * 501)))
      ),
      Me
    )
    assertEquals(long.left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.articles.load(Walls).flatMap(_.updatedAt), Some(token))
    assertEquals(s.articles.load(Walls).map(_.title), Some("Walls"))
    assertEquals(s.articles.critiqueReasons(Walls), Nil)
    assertEquals(s.articles.versions(Walls), Nil)
  }

  stest("a rename keeps the old slug, moves the versions and rewrites the links of the others") { s =>
    val _ = s.articles.put(article(Walls, Article.Title("Walls"), Article.Body("## Overview\n\nThe walls.\n")), Me)
    val _ = s.articles.patch(Walls, Article.Patch(append = Some(Article.Patch.Append("A gate."))), Me)
    val _ = s.articles.put(
      article(
        Harbour,
        Article.Title("Harbour"),
        Article.Body(s"## Story\n\nSee [[$Walls]] and [[$Walls|the walls]].\n")
      ),
      Me
    )
    val _ = s.articles.put(article(Index, Article.Title("Index"), Article.Body("- nothing about it\n")), Me)
    assertEquals(s.articles.rename(Walls, Slug("zr_city_walls"), Me), Right(List(Harbour)))
    assertEquals(s.articles.canonical(Walls), "zr_city_walls")
    assertEquals(s.articles.view(Walls).map(_.article.slug), Some("zr_city_walls"))
    assertEquals(s.articles.load(Walls), None)
    assertEquals(s.articles.versions(Slug("zr_city_walls")).size, 1)
    assertEquals(
      s.articles.load(Harbour).map(_.body),
      Some("## Story\n\nSee [[zr_city_walls]] and [[zr_city_walls|the walls]].\n")
    )
    assertEquals(s.articles.versions(Harbour).size, 1)
    assertEquals(s.articles.view(Slug("zr_city_walls")).get.backlinks.map(_.id.value: String), List(Harbour))
    assertEquals(s.articles.rename(Slug("zr_city_walls"), Harbour, Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(
      s.articles.rename(Slug("zr_nothing"), Slug("zr_other"), Me).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(s.articles.rename(Index, Walls, Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.articles.rename(Slug("zr_city_walls"), Walls, Me), Right(List(Harbour)))
    assertEquals(s.articles.canonical(Slug("zr_city_walls")), Walls)
  }

  stest("a delete takes the article's versions and old slugs with it") { s =>
    val _ = s.articles.put(article(Walls, Article.Title("Walls"), Article.Body("## Overview\n\nThe walls.\n")), Me)
    val _ = s.articles.patch(Walls, Article.Patch(title = Some(Article.Title("City walls"))), Me)
    val _ = s.articles.rename(Walls, Slug("zr_city_walls"), Me)
    assert(s.articles.delete(Slug("zr_city_walls"), Me))
    assert(!s.articles.delete(Slug("zr_city_walls"), Me))
    assertEquals(s.articles.canonical(Walls), Walls)
    assertEquals(s.articles.versions(Slug("zr_city_walls")), Nil)
    assertEquals(s.articles.slugs, Nil)
  }

class ArticleServiceInMemorySuite extends ArticleServiceContract with ResearchStoreSuite.InMemory
class ArticleServiceDbSuite extends ArticleServiceContract with ResearchStoreSuite.OnDatabase
