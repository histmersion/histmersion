package com.histmersion
package domain

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.research.Article
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Goal
import com.histmersion.api.research.Article.Plans
import com.histmersion.lint.{tolerated, LintRule}

/** propose-articles' code checks: the shape of each proposed article, then the seeding rules — a free slug, a filter
  * returning at least minSources, less than maxOverlap shared with any article of the area or with a proposal kept
  * before it.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PlanArticlesSuite extends munit.FunSuite:

  private def art(
      slug: ArticlesAnswer.ArticleAnswer.Slug,
      topics: List[ArticlesAnswer.ArticleAnswer.Topics],
      title: ArticlesAnswer.ArticleAnswer.Title = ArticlesAnswer.ArticleAnswer.Title("T")
  ) =
    ArticlesAnswer.ArticleAnswer(slug, title, None, None, topics, Nil, Plans.ArticlePlan.Rationale("why"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def slug(s: String) = ArticlesAnswer.ArticleAnswer.Slug(s)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def topics(ts: String*) = ts.toList.map(ArticlesAnswer.ArticleAnswer.Topics(_))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def citekeys(ks: String*) = ks.map(Citekey.assume(_)).toSet

  test(
    "check: invalid slugs, empty titles, filterless articles go back; a duplicate is dropped; last keeps what passed"
  ):
    val a = ArticlesAnswer(
      ArticlesAnswer.Rationale("covers it"),
      List(
        art(slug("good"), topics("tin")),
        art(slug("Bad Slug"), topics("x")),
        art(slug("good"), topics("tin")),
        art(slug("nofilter"), Nil),
        art(slug("untitled"), topics("x"), title = ArticlesAnswer.ArticleAnswer.Title(" "))
      )
    )
    val back = a.check(maxArticles = ArticlesAnswer.Limits.MaxArticles(6), last = ArticlesAnswer.LastRound(false))
    assert(
      back.left.exists(ps =>
        ps.exists(_.sentence.contains("Bad Slug")) && ps.exists(_.sentence.contains("no filter")) &&
          ps.exists(_.sentence.contains("no title"))
      ),
      back.toString
    )
    a.check(maxArticles = ArticlesAnswer.Limits.MaxArticles(6), last = ArticlesAnswer.LastRound(true)) match
      case Right((cs, dropped)) =>
        assertEquals(cs.map(c => (c.slug, c.kind)), List(("good", "concept")))
        // the sentences the dropped entries had as text before they were typed (G60 step 5)
        val said = dropped.map(_.sentence)
        assert(said.contains("'good' proposed twice"), said)
        assert(
          said.contains("not proposed: article 'nofilter': it has no filter: give a query, topics or tags"),
          said
        )
        assert(said.contains("not proposed: article 'untitled': it has no title"), said)
      case other => fail(other.toString)
    assert(
      ArticlesAnswer(ArticlesAnswer.Rationale(""), Nil)
        .check(ArticlesAnswer.Limits.MaxArticles(6), last = ArticlesAnswer.LastRound(false))
        .isLeft
    )
    assert(
      ArticlesAnswer(ArticlesAnswer.Rationale("r"), List(art(slug("a"), topics("x")), art(slug("b"), topics("y"))))
        .check(ArticlesAnswer.Limits.MaxArticles(1), last = ArticlesAnswer.LastRound(false))
        .left
        .exists(_.exists(_.sentence.contains("at most 1")))
    )

  test("verify: slug taken, too few sources, too much overlap with an area article or an earlier proposal are dropped"):
    val lim = ArticlesAnswer.Limits(
      minSources = ArticlesAnswer.Limits.MinSources(3),
      maxOverlap = ArticlesAnswer.Limits.MaxOverlap(0.5),
      maxArticles = ArticlesAnswer.Limits.MaxArticles(6)
    )
    val sets = Map(
      Article.Filter.Topics("tin") -> citekeys("a", "b", "c", "d"),
      Article.Filter.Topics("copper") -> citekeys("e", "f", "g"),
      Article.Filter.Topics("few") -> citekeys("a"),
      Article.Filter.Topics("old") -> citekeys("a", "b", "c", "x"),
      Article.Filter.Topics("dup") -> citekeys("e", "f", "g", "h")
    )
    def c(slug: Article.Slug, topic: Article.Filter.Topics) = ArticlesAnswer.Candidate(
      slug,
      Article.Title(slug.value),
      Article.Kind.Wire("concept"),
      Article.Filter(topics = List(topic)),
      Plans.ArticlePlan.Rationale("w")
    )
    val plan = ArticlesAnswer.verify(
      Goal.Slug("goal"),
      ArticlesAnswer.Rationale(" r "),
      List(
        c(Article.Slug("taken"), Article.Filter.Topics("copper")),
        c(Article.Slug("small"), Article.Filter.Topics("few")),
        c(Article.Slug("tinny"), Article.Filter.Topics("tin")),
        c(Article.Slug("cop"), Article.Filter.Topics("copper")),
        c(Article.Slug("copper-again"), Article.Filter.Topics("dup"))
      ),
      Nil,
      _.value != "taken",
      f => { val s = sets(f.topics.head); (Article.Plans.ProposedArticle.Hits(s.size), s) },
      List(Article.Slug("existing") -> citekeys("x", "y")),
      lim
    )
    assertEquals(plan.articles.map(_.slug), List("tinny", "cop"))
    assertEquals(plan.rationale, "r")
    val tinny = plan.articles.head
    assertEquals((tinny.hits, tinny.overlapWith), (4, None))
    val said = plan.dropped.map(_.sentence)
    assert(said.contains("'taken': an article with this slug exists (or did)"), said)
    assert(said.exists(d => d.startsWith("'small': ") && d.endsWith(" returns 1 source; at least 3")), said)
    assert(said.contains("'copper-again': 75% of its sources are [[cop]]'s already (less than 50%)"), said)
    // the overlap with an area article: 3 of "old"'s 4 sources are "existing2"'s
    val p2 = ArticlesAnswer.verify(
      Goal.Slug("goal"),
      ArticlesAnswer.Rationale("r"),
      List(c(Article.Slug("old"), Article.Filter.Topics("old"))),
      Nil,
      _ => true,
      f => { val s = sets(f.topics.head); (Article.Plans.ProposedArticle.Hits(s.size), s) },
      List(Article.Slug("existing2") -> citekeys("a", "b", "c")),
      lim
    )
    assertEquals(p2.articles, Nil)
    assertEquals(
      p2.dropped.map(_.sentence),
      List("'old': 75% of its sources are [[existing2]]'s already (less than 50%)")
    )

  test("overlap: the share of mine the other holds"):
    assertEquals(
      ArticlesAnswer
        .overlap(Set(Citekey.assume("a"), Citekey.assume("b")), Set(Citekey.assume("b"), Citekey.assume("c"))),
      0.5
    )
    assertEquals(ArticlesAnswer.overlap(Set.empty, Set(Citekey.assume("a"))), 0.0)
