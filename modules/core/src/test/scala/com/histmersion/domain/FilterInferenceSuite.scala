package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.FilterInference.*
import com.histmersion.domain.FilterInference.Limits
import com.histmersion.api.research.Article

/** infer-filter's code checks: the answer's shape, then what the filter returns against the article's citations and the
  * hit bounds; a failing filter goes back, and on the last attempt nothing is proposed.
  */
class FilterInferenceSuite extends munit.FunSuite:

  private val lim = Limits(minHits = com.histmersion.domain.FilterInference.Limits.MinHits(3), maxHits = com.histmersion.domain.FilterInference.Limits.MaxHits(50), minCitedShare = 0.6)
  private val f = Article.Filter(topics = List(com.histmersion.api.library.Filter.Topics("tin")))

  test("filterOf: an empty filter, a blank rationale and an unknown status are refused; a filter is tidied"):
    assert(filterOf(FilterAnswer(com.histmersion.domain.FilterInference.FilterAnswer.Rationale("why"))).left.exists(_.head.contains("selects nothing")))
    assert(filterOf(FilterAnswer(com.histmersion.domain.FilterInference.FilterAnswer.Rationale(" "), topics = List(com.histmersion.api.library.Filter.Topics("tin")))).left.exists(_.head.contains("rationale")))
    assertEquals(filterOf(FilterAnswer(com.histmersion.domain.FilterInference.FilterAnswer.Rationale("why"), query = Some(com.histmersion.api.library.Filter.Q("  ")), topics = List(com.histmersion.api.library.Filter.Topics("tin"), com.histmersion.api.library.Filter.Topics("tin"), com.histmersion.api.library.Filter.Topics(" ")))), Right(f))

  test("judge: within the bounds and covering the cited sources → a proposal with what code measured"):
    judge(com.histmersion.domain.research.Slug("zz"), f, com.histmersion.domain.FilterInference.FilterAnswer.Rationale("because"), com.histmersion.api.research.Article.Plans.FilterProposal.Total(10), List(com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.library.Citekey("b"), com.histmersion.domain.library.Citekey("c")), Set(com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.library.Citekey("b")), List(com.histmersion.domain.library.Citekey("x")), lim, last = com.histmersion.domain.FilterInference.Last(false)) match
      case Right(Inferred(Some(p), Nil)) =>
        assertEquals((p.total, p.cited, p.citedIn, p.missed, p.sample), (10, 3, 2, List("c"), List("x")))
        assertEqualsDouble(p.recall, 2.0 / 3, 1e-9)
      case other => fail(other.toString)

  test("judge: too few, too many, or missing what the article cites → back with the numbers; last → nothing"):
    assert(judge(com.histmersion.domain.research.Slug("zz"), f, com.histmersion.domain.FilterInference.FilterAnswer.Rationale("w"), com.histmersion.api.research.Article.Plans.FilterProposal.Total(2), Nil, Set.empty, Nil, lim, last = com.histmersion.domain.FilterInference.Last(false)).left.exists(_.head.contains("at least 3")))
    assert(judge(com.histmersion.domain.research.Slug("zz"), f, com.histmersion.domain.FilterInference.FilterAnswer.Rationale("w"), com.histmersion.api.research.Article.Plans.FilterProposal.Total(51), Nil, Set.empty, Nil, lim, last = com.histmersion.domain.FilterInference.Last(false)).left.exists(_.head.contains("at most 50")))
    val missing = judge(com.histmersion.domain.research.Slug("zz"), f, com.histmersion.domain.FilterInference.FilterAnswer.Rationale("w"), com.histmersion.api.research.Article.Plans.FilterProposal.Total(10), List(com.histmersion.domain.library.Citekey("a"), com.histmersion.domain.library.Citekey("b"), com.histmersion.domain.library.Citekey("c")), Set(com.histmersion.domain.library.Citekey("a")), Nil, lim, last = com.histmersion.domain.FilterInference.Last(false))
    assert(missing.left.exists(ps => ps.head.contains("1 of the 3") && ps.head.contains("b, c")), missing.toString)
    judge(com.histmersion.domain.research.Slug("zz"), f, com.histmersion.domain.FilterInference.FilterAnswer.Rationale("w"), com.histmersion.api.research.Article.Plans.FilterProposal.Total(2), Nil, Set.empty, Nil, lim, last = com.histmersion.domain.FilterInference.Last(true)) match
      case Right(Inferred(None, ps)) => assert(ps.nonEmpty)
      case other                     => fail(other.toString)

  test("an article that cites nothing is judged by the bounds alone"):
    assert(judge(com.histmersion.domain.research.Slug("zz"), f, com.histmersion.domain.FilterInference.FilterAnswer.Rationale("w"), com.histmersion.api.research.Article.Plans.FilterProposal.Total(5), Nil, Set.empty, Nil, lim, last = com.histmersion.domain.FilterInference.Last(false)).exists(_.proposal.isDefined))
