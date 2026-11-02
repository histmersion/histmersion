package com.histmersion.domain

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

  private def art(slug: String, topics: List[String], title: String = "T") =
    ArticleAnswer(slug, title, None, None, topics, Nil, "why")

  test(
    "check: invalid slugs, empty titles, filterless articles go back; a duplicate is dropped; last keeps what passed"
  ):
    val a = ArticlesAnswer(
      "covers it",
      List(
        art("good", List("tin")),
        art("Bad Slug", List("x")),
        art("good", List("tin")),
        art("nofilter", Nil),
        art("untitled", List("x"), title = " ")
      )
    )
    val back = check(a, maxArticles = 6, last = false)
    assert(
      back.left.exists(ps =>
        ps.exists(_.contains("Bad Slug")) && ps.exists(_.contains("no filter")) &&
          ps.exists(_.contains("no title"))
      ),
      back.toString
    )
    check(a, maxArticles = 6, last = true) match
      case Right((cs, dropped)) =>
        assertEquals(cs.map(c => (c.slug, c.kind)), List(("good", "concept")))
        assert(dropped.exists(_.contains("proposed twice")))
      case other => fail(other.toString)
    assert(check(ArticlesAnswer("", Nil), 6, last = false).isLeft)
    assert(
      check(ArticlesAnswer("r", List(art("a", List("x")), art("b", List("y")))), 1, last = false).left
        .exists(_.exists(_.contains("at most 1")))
    )

  test("verify: slug taken, too few sources, too much overlap with an area article or an earlier proposal are dropped"):
    val lim = Limits(minSources = 3, maxOverlap = 0.5, maxArticles = 6)
    val sets = Map(
      "tin" -> Set("a", "b", "c", "d"),
      "copper" -> Set("e", "f", "g"),
      "few" -> Set("a"),
      "old" -> Set("a", "b", "c", "x"),
      "dup" -> Set("e", "f", "g", "h")
    )
    def c(slug: String, topic: String) = Candidate(slug, slug, "concept", Article.Filter(topics = List(topic)), "w")
    val plan = verify(
      "goal",
      " r ",
      List(c("taken", "copper"), c("small", "few"), c("tinny", "tin"), c("cop", "copper"), c("copper-again", "dup")),
      Nil,
      _ != "taken",
      f => { val s = sets(f.topics.head); (s.size, s) },
      List("existing" -> Set("x", "y")),
      lim
    )
    assertEquals(plan.articles.map(_.slug), List("tinny", "cop"))
    assertEquals(plan.rationale, "r")
    val tinny = plan.articles.head
    assertEquals((tinny.hits, tinny.overlapWith), (4, None))
    assert(plan.dropped.exists(_.contains("'taken'")))
    assert(plan.dropped.exists(d => d.contains("'small'") && d.contains("at least 3")), plan.dropped)
    assert(plan.dropped.exists(d => d.contains("'copper-again'") && d.contains("[[cop]]")), plan.dropped)
    // the overlap with an area article: 3 of "old"'s 4 sources are "existing2"'s
    val p2 = verify(
      "goal",
      "r",
      List(c("old", "old")),
      Nil,
      _ => true,
      f => { val s = sets(f.topics.head); (s.size, s) },
      List("existing2" -> Set("a", "b", "c")),
      lim
    )
    assertEquals(p2.articles, Nil)
    assert(p2.dropped.head.contains("75%"), p2.dropped)

  test("overlap: the share of mine the other holds"):
    assertEquals(overlap(Set("a", "b"), Set("b", "c")), 0.5)
    assertEquals(overlap(Set.empty, Set("a")), 0.0)
