package com.histmersion.domain.research.articles

import com.histmersion.api.Write
import com.histmersion.domain.Caller
import com.histmersion.domain.research.{ResearchStores, ResearchStoreSuite}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.research.{Article, Backlink, CritiqueReason}

/** What research's articles answer, the same on the database and in memory: an article written whole and merged from
  * patches with its token and versions, its link graph, a critique reason refused when no source is stored, renamed
  * with its old slug kept and its links followed, deleted with its versions and old slugs.
  */
abstract class ArticleServiceContract extends ResearchStoreSuite:

  private val Me = Caller.Local
  private val TokenRe = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z".r

  private def article(slug: String, title: String, body: String): Article =
    Article(
      slug,
      title,
      "concept",
      body,
      Some(s"On $title."),
      List("zr-art"),
      List("Zrland"),
      Some(Period(-1600, -1400)),
      None,
      Some(Article.Filter(tags = List("zr-oracle"))),
      Map("zrdelta2010_survey" -> "Region-wide.")
    )

  private val Walls = "zr_walls"
  private val Harbour = "zr_harbour"
  private val Index = "zr_index"

  stest("an article put and read back: its record and token, its uid, both directions of its links") { s =>
    val token = s.articles.put(article(Walls, "Walls", "## Overview\n\nThe walls [[zralpha1990_walls]].\n"), Me)
    assert(TokenRe.matches(token), token)
    val _ = s.articles.put(article(Index, "Index", s"- [[$Walls]]\n- [[$Harbour]]\n- [[$Index]]\n"), Me)
    val loaded = s.articles.load(Walls).get
    assertEquals(loaded.updatedAt, Some(token))
    assertEquals(loaded.filter, Some(Article.Filter(tags = List("zr-oracle"))))
    assertEquals(loaded.reasons, Map("zrdelta2010_survey" -> "Region-wide."))
    assertEquals(loaded.period, Some(Period(-1600, -1400)))
    val view = s.articles.view(Walls).get
    assertEquals(view.article.updatedAt, Some(token))
    assert(view.article.uid.isDefined)
    assertEquals(view.backlinks, List(Backlink("article", Index, Some("Index"))))
    assertEquals(view.sourcesCited, Nil)
    assertEquals(s.articles.view(Index).get.backlinks, Nil)
    assertEquals(s.articles.slugs, List(Walls, Index).sorted)
    assert(s.articles.exists(Walls))
    assert(!s.articles.exists("zr_nothing"))
    assertEquals(s.articles.refs(List(Index, "ZR_WALLS", "zr_nothing")).map(_.slug), List(Index, Walls))
    assertEquals(s.articles.updatedAtMillis.keySet, Set(Walls, Index))
    assertEquals(s.articles.backlinksForSource("zralpha1990_walls"), Nil)
    assertEquals(s.articles.citing(List("zralpha1990_walls"), 5), Nil)
    assertEquals(s.articles.articlesByGoal, Map.empty)
    assertEquals(s.articles.canonical(Walls), Walls)
    assertEquals(s.articles.load("zr_nothing"), None)
    assertEquals(s.articles.view("zr_nothing"), None)
  }

  stest("a patch merges what it names and records the version it replaces; a stale token, a bad kind and no article") {
    s =>
      val first = s.articles.put(article(Walls, "Walls", "## Overview\n\nThe walls.\n"), Me)
      val second = s.articles.patch(Walls, Article.Patch(append = Some("A gate."), section = Some("Overview")), Me)
      assert(second.isRight, second)
      assertNotEquals(second.toOption, Some(first))
      assertEquals(s.articles.load(Walls).map(_.body), Some("## Overview\n\nThe walls.\n\nA gate."))
      val third = s.articles.patch(Walls, Article.Patch(title = Some("City walls"), ifUpdatedAt = second.toOption), Me)
      assert(third.isRight, third)
      assertEquals(s.articles.load(Walls).map(_.title), Some("City walls"))
      assertEquals(
        s.articles.patch(Walls, Article.Patch(title = Some("x"), ifUpdatedAt = Some(first)), Me).left.map(_.code),
        Left(Some(Write.CodePrecondition))
      )
      assertEquals(
        s.articles.patch(Walls, Article.Patch(kind = Some("Not A Kind!")), Me).left.map(_.code),
        Left(Some(Write.CodeInvalid))
      )
      assertEquals(
        s.articles.patch("zr_nothing", Article.Patch(title = Some("x")), Me).left.map(_.code),
        Left(Some(Write.CodeNotFound))
      )
      val versions = s.articles.versions(Walls)
      assertEquals(versions.size, 2)
      assertEquals(versions.map(_.wordCount), List(6, 5))
      assertEquals(versions.map(_.updatedAt), List(second.toOption.get, first))
      val oldest = s.articles.version(Walls, versions.last.id).get
      assertEquals(oldest.body, "## Overview\n\nThe walls.\n")
      assertEquals(s.articles.version(Walls, versions.last.id + 1000), None)
      assertEquals(s.articles.version(Harbour, versions.last.id), None)
      assertEquals(s.articles.latestVersion(Walls), Some(versions.map(_.id).max))
      assertEquals(s.articles.latestVersion(Harbour), None)
  }

  stest("a critique reason names two sources by citekey: with none stored it is refused and nothing is written") { s =>
    val token = s.articles.put(article(Walls, "Walls", "## Overview\n\nThe walls.\n"), Me)
    val refused = s.articles.patch(
      Walls,
      Article.Patch(title = Some("x"), critiqueReasons = List(CritiqueReason("zra", "zrb", "Not about the walls."))),
      Me
    )
    assertEquals(refused.left.map(_.message), Left("critique reason zra>zrb: 'zra' is not a citekey"))
    val long = s.articles.patch(
      Walls,
      Article.Patch(critiqueReasons = List(CritiqueReason("zra", "zrb", "w" * 501))),
      Me
    )
    assertEquals(long.left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.articles.load(Walls).flatMap(_.updatedAt), Some(token))
    assertEquals(s.articles.load(Walls).map(_.title), Some("Walls"))
    assertEquals(s.articles.critiqueReasons(Walls), Nil)
    assertEquals(s.articles.versions(Walls), Nil)
  }

  stest("a rename keeps the old slug, moves the versions and rewrites the links of the others") { s =>
    val _ = s.articles.put(article(Walls, "Walls", "## Overview\n\nThe walls.\n"), Me)
    val _ = s.articles.patch(Walls, Article.Patch(append = Some("A gate.")), Me)
    val _ = s.articles.put(article(Harbour, "Harbour", s"## Story\n\nSee [[$Walls]] and [[$Walls|the walls]].\n"), Me)
    val _ = s.articles.put(article(Index, "Index", "- nothing about it\n"), Me)
    assertEquals(s.articles.rename(Walls, "zr_city_walls", Me), Right(List(Harbour)))
    assertEquals(s.articles.canonical(Walls), "zr_city_walls")
    assertEquals(s.articles.view(Walls).map(_.article.slug), Some("zr_city_walls"))
    assertEquals(s.articles.load(Walls), None)
    assertEquals(s.articles.versions("zr_city_walls").size, 1)
    assertEquals(
      s.articles.load(Harbour).map(_.body),
      Some("## Story\n\nSee [[zr_city_walls]] and [[zr_city_walls|the walls]].\n")
    )
    assertEquals(s.articles.versions(Harbour).size, 1)
    assertEquals(s.articles.view("zr_city_walls").get.backlinks.map(_.id), List(Harbour))
    assertEquals(s.articles.rename("zr_city_walls", Harbour, Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.articles.rename("zr_nothing", "zr_other", Me).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(s.articles.rename(Index, Walls, Me).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(s.articles.rename("zr_city_walls", Walls, Me), Right(List(Harbour)))
    assertEquals(s.articles.canonical("zr_city_walls"), Walls)
  }

  stest("a delete takes the article's versions and old slugs with it") { s =>
    val _ = s.articles.put(article(Walls, "Walls", "## Overview\n\nThe walls.\n"), Me)
    val _ = s.articles.patch(Walls, Article.Patch(title = Some("City walls")), Me)
    val _ = s.articles.rename(Walls, "zr_city_walls", Me)
    assert(s.articles.delete("zr_city_walls", Me))
    assert(!s.articles.delete("zr_city_walls", Me))
    assertEquals(s.articles.canonical(Walls), Walls)
    assertEquals(s.articles.versions("zr_city_walls"), Nil)
    assertEquals(s.articles.slugs, Nil)
  }

class ArticleServiceInMemorySuite extends ArticleServiceContract with ResearchStoreSuite.InMemory
class ArticleServiceDbSuite extends ArticleServiceContract with ResearchStoreSuite.OnDatabase
