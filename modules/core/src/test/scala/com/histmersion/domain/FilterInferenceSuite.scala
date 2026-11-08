package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.FilterInference.*
import com.histmersion.domain.FilterInference.Limits
import com.histmersion.api.research.Article
import com.histmersion.api.library.Filter
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey

/** infer-filter's code checks: the answer's shape, then what the filter returns against the article's citations and the
  * hit bounds; a failing filter goes back, and on the last attempt nothing is proposed.
  */
class FilterInferenceSuite extends munit.FunSuite:

  private val lim =
    Limits(minHits = Limits.MinHits(3), maxHits = Limits.MaxHits(50), minCitedShare = Limits.MinCitedShare(0.6))
  private val f = Article.Filter(topics = List(Filter.Topics("tin")))

  test("filterOf: an empty filter, a blank rationale and an unknown status are refused; a filter is tidied"):
    assert(filterOf(FilterAnswer(FilterAnswer.Rationale("why"))).left.exists(_.head.value.contains("selects nothing")))
    assert(
      filterOf(FilterAnswer(FilterAnswer.Rationale(" "), topics = List(Filter.Topics("tin")))).left
        .exists(_.head.value.contains("rationale"))
    )
    assertEquals(
      filterOf(
        FilterAnswer(
          FilterAnswer.Rationale("why"),
          query = Some(Filter.Q("  ")),
          topics = List(Filter.Topics("tin"), Filter.Topics("tin"), Filter.Topics(" "))
        )
      ),
      Right(f)
    )

  test("judge: within the bounds and covering the cited sources → a proposal with what code measured"):
    judge(
      Slug("zz"),
      f,
      FilterAnswer.Rationale("because"),
      Article.Plans.FilterProposal.Total(10),
      List(Citekey.assume("a"), Citekey.assume("b"), Citekey.assume("c")),
      Set(Citekey.assume("a"), Citekey.assume("b")),
      List(Citekey.assume("x")),
      lim,
      last = Last(false)
    ) match
      case Right(Inferred(Some(p), Nil)) =>
        assertEquals((p.total, p.cited, p.citedIn, p.missed, p.sample), (10, 3, 2, List("c"), List("x")))
        assertEqualsDouble(p.recall.value, 2.0 / 3, 1e-9)
      case other => fail(other.toString)

  test("judge: too few, too many, or missing what the article cites → back with the numbers; last → nothing"):
    assert(
      judge(
        Slug("zz"),
        f,
        FilterAnswer.Rationale("w"),
        Article.Plans.FilterProposal.Total(2),
        Nil,
        Set.empty,
        Nil,
        lim,
        last = Last(false)
      ).left.exists(_.head.value.contains("at least 3"))
    )
    assert(
      judge(
        Slug("zz"),
        f,
        FilterAnswer.Rationale("w"),
        Article.Plans.FilterProposal.Total(51),
        Nil,
        Set.empty,
        Nil,
        lim,
        last = Last(false)
      ).left.exists(_.head.value.contains("at most 50"))
    )
    val missing = judge(
      Slug("zz"),
      f,
      FilterAnswer.Rationale("w"),
      Article.Plans.FilterProposal.Total(10),
      List(Citekey.assume("a"), Citekey.assume("b"), Citekey.assume("c")),
      Set(Citekey.assume("a")),
      Nil,
      lim,
      last = Last(false)
    )
    assert(
      missing.left.exists(ps => ps.head.value.contains("1 of the 3") && ps.head.value.contains("b, c")),
      missing.toString
    )
    judge(
      Slug("zz"),
      f,
      FilterAnswer.Rationale("w"),
      Article.Plans.FilterProposal.Total(2),
      Nil,
      Set.empty,
      Nil,
      lim,
      last = Last(true)
    ) match
      case Right(Inferred(None, ps)) => assert(ps.nonEmpty)
      case other                     => fail(other.toString)

  test("an article that cites nothing is judged by the bounds alone"):
    assert(
      judge(
        Slug("zz"),
        f,
        FilterAnswer.Rationale("w"),
        Article.Plans.FilterProposal.Total(5),
        Nil,
        Set.empty,
        Nil,
        lim,
        last = Last(false)
      ).exists(_.proposal.isDefined)
    )
