package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.PlanArticles.*
import com.histmersion.domain.PlanArticles.Limits
import com.histmersion.api.research.Article

/** propose-articles' code checks: the shape of each proposed article, then the seeding rules — a free slug, a filter
  * returning at least minSources, less than maxOverlap shared with any article of the area or with a proposal kept
  * before it.
  */
class PlanArticlesSuite extends munit.FunSuite:

  private def art(
      slug: ArticleAnswer.Slug,
      topics: List[ArticleAnswer.Topics],
      title: ArticleAnswer.Title = ArticleAnswer.Title("T")
  ) =
    ArticleAnswer(slug, title, None, None, topics, Nil, com.histmersion.domain.PlanArticles.ArticleAnswer.Why("why"))

  private def slug(s: String) = ArticleAnswer.Slug(s)
  private def topics(ts: String*) = ts.toList.map(ArticleAnswer.Topics(_))
  private def citekeys(ks: String*) = ks.map(com.histmersion.domain.library.Citekey(_)).toSet

  test(
    "check: invalid slugs, empty titles, filterless articles go back; a duplicate is dropped; last keeps what passed"
  ):
    val a = ArticlesAnswer(
      com.histmersion.domain.PlanArticles.ArticlesAnswer.Rationale("covers it"),
      List(
        art(slug("good"), topics("tin")),
        art(slug("Bad Slug"), topics("x")),
        art(slug("good"), topics("tin")),
        art(slug("nofilter"), Nil),
        art(slug("untitled"), topics("x"), title = ArticleAnswer.Title(" "))
      )
    )
    val back = check(a, maxArticles = com.histmersion.domain.PlanArticles.Limits.MaxArticles(6), last = com.histmersion.domain.PlanArticles.LastRound(false))
    assert(
      back.left.exists(ps =>
        ps.exists(_.value.contains("Bad Slug")) && ps.exists(_.value.contains("no filter")) &&
          ps.exists(_.value.contains("no title"))
      ),
      back.toString
    )
    check(a, maxArticles = com.histmersion.domain.PlanArticles.Limits.MaxArticles(6), last = com.histmersion.domain.PlanArticles.LastRound(true)) match
      case Right((cs, dropped)) =>
        assertEquals(cs.map(c => (c.slug, c.kind)), List(("good", "concept")))
        assert(dropped.exists(_.value.contains("proposed twice")))
      case other => fail(other.toString)
    assert(check(ArticlesAnswer(com.histmersion.domain.PlanArticles.ArticlesAnswer.Rationale(""), Nil), com.histmersion.domain.PlanArticles.Limits.MaxArticles(6), last = com.histmersion.domain.PlanArticles.LastRound(false)).isLeft)
    assert(
      check(ArticlesAnswer(com.histmersion.domain.PlanArticles.ArticlesAnswer.Rationale("r"), List(art(slug("a"), topics("x")), art(slug("b"), topics("y")))), com.histmersion.domain.PlanArticles.Limits.MaxArticles(1), last = com.histmersion.domain.PlanArticles.LastRound(false)).left
        .exists(_.exists(_.value.contains("at most 1")))
    )

  test("verify: slug taken, too few sources, too much overlap with an area article or an earlier proposal are dropped"):
    val lim = Limits(minSources = com.histmersion.domain.PlanArticles.Limits.MinSources(3), maxOverlap = com.histmersion.domain.PlanArticles.Limits.MaxOverlap(0.5), maxArticles = com.histmersion.domain.PlanArticles.Limits.MaxArticles(6))
    val sets = Map(
      Article.Filter.Topics("tin") -> citekeys("a", "b", "c", "d"),
      Article.Filter.Topics("copper") -> citekeys("e", "f", "g"),
      Article.Filter.Topics("few") -> citekeys("a"),
      Article.Filter.Topics("old") -> citekeys("a", "b", "c", "x"),
      Article.Filter.Topics("dup") -> citekeys("e", "f", "g", "h")
    )
    def c(slug: Article.Slug, topic: Article.Filter.Topics) = Candidate(slug, Article.Title(slug.value), com.histmersion.api.research.Article.Kind.Wire("concept"), Article.Filter(topics = List(topic)), com.histmersion.api.research.Article.Plans.ProposedArticle.Why("w"))
    val plan = verify(
      com.histmersion.domain.automation.Goal.Slug("goal"),
      com.histmersion.domain.PlanArticles.ArticlesAnswer.Rationale(" r "),
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
    assert(plan.dropped.exists(_.value.contains("'taken'")))
    assert(plan.dropped.exists(d => d.value.contains("'small'") && d.value.contains("at least 3")), plan.dropped)
    assert(plan.dropped.exists(d => d.value.contains("'copper-again'") && d.value.contains("[[cop]]")), plan.dropped)
    // the overlap with an area article: 3 of "old"'s 4 sources are "existing2"'s
    val p2 = verify(
      com.histmersion.domain.automation.Goal.Slug("goal"),
      com.histmersion.domain.PlanArticles.ArticlesAnswer.Rationale("r"),
      List(c(Article.Slug("old"), Article.Filter.Topics("old"))),
      Nil,
      _ => true,
      f => { val s = sets(f.topics.head); (Article.Plans.ProposedArticle.Hits(s.size), s) },
      List(Article.Slug("existing2") -> citekeys("a", "b", "c")),
      lim
    )
    assertEquals(p2.articles, Nil)
    assert(p2.dropped.head.value.contains("75%"), p2.dropped)

  test("overlap: the share of mine the other holds"):
    assertEquals(overlap(Set(com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.library.Citekey("b")), Set(com.histmersion.domain.library.Citekey("b"), com.histmersion.domain.library.Citekey("c"))), 0.5)
    assertEquals(overlap(Set.empty, Set(com.histmersion.domain.library.Citekey("a"))), 0.0)
