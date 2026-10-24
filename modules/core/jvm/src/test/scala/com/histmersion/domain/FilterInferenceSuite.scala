package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.FilterInference.*
import com.histmersion.domain.FilterInference.Limits

/** infer-filter's code checks: the answer's shape, then what the filter returns against the article's
  * citations and the hit bounds; a failing filter goes back, and on the last attempt nothing is proposed. */
class FilterInferenceSuite extends munit.FunSuite:

  private val lim = Limits(minHits = 3, maxHits = 50, minCitedShare = 0.6)
  private val f   = ArticleFilter(topics = List("tin"))

  test("filterOf: an empty filter, a blank rationale and an unknown status are refused; a filter is tidied"):
    assert(filterOf(FilterAnswer("why")).left.exists(_.head.contains("selects nothing")))
    assert(filterOf(FilterAnswer(" ", topics = List("tin"))).left.exists(_.head.contains("rationale")))
    assertEquals(filterOf(FilterAnswer("why", query = Some("  "), topics = List("tin", "tin", " "))), Right(f))

  test("judge: within the bounds and covering the cited sources → a proposal with what code measured"):
    judge("zz", f, "because", 10, List("a", "b", "c"), Set("a", "b"), List("x"), lim, last = false) match
      case Right(Inferred(Some(p), Nil)) =>
        assertEquals((p.total, p.cited, p.citedIn, p.missed, p.sample), (10, 3, 2, List("c"), List("x")))
        assertEqualsDouble(p.recall, 2.0 / 3, 1e-9)
      case other => fail(other.toString)

  test("judge: too few, too many, or missing what the article cites → back with the numbers; last → nothing"):
    assert(judge("zz", f, "w", 2, Nil, Set.empty, Nil, lim, last = false).left.exists(_.head.contains("at least 3")))
    assert(judge("zz", f, "w", 51, Nil, Set.empty, Nil, lim, last = false).left.exists(_.head.contains("at most 50")))
    val missing = judge("zz", f, "w", 10, List("a", "b", "c"), Set("a"), Nil, lim, last = false)
    assert(missing.left.exists(ps => ps.head.contains("1 of the 3") && ps.head.contains("b, c")), missing.toString)
    judge("zz", f, "w", 2, Nil, Set.empty, Nil, lim, last = true) match
      case Right(Inferred(None, ps)) => assert(ps.nonEmpty)
      case other => fail(other.toString)

  test("an article that cites nothing is judged by the bounds alone"):
    assert(judge("zz", f, "w", 5, Nil, Set.empty, Nil, lim, last = false).exists(_.proposal.isDefined))
