package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.Text.sentences
import com.histmersion.util.problem.Problem

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import AcquisitionSweepRules.*
import com.histmersion.api.library.WorkRef
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.automation.Goal
import com.histmersion.lint.{tolerated, LintRule}

/** An acquisition sweep's pure rules: what it may offer the ranking, how a ranking is checked, which picks are
  * ingested.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AcquisitionSweepRulesSuite extends munit.FunSuite:

  test("a sweep never offers what the corpus or the queue has; each work once; capped"):
    val w = (doi: String, inDb: WorkRef.InDb) =>
      WorkRef(doi = (Some(doi)).map(Doi.apply), title = (Some(doi)).map(Title.apply), inDb = inDb)
    val found = List(
      Candidate.Query("q1") -> w("10.1000/a", WorkRef.InDb(false)),
      Candidate.Query("q1") -> w("10.1000/b", WorkRef.InDb(true)),
      Candidate.Query("q2") -> w("10.1000/a", WorkRef.InDb(false)),
      Candidate.Query("q2") -> w("10.1000/c", WorkRef.InDb(false)),
      Candidate.Query("q2") -> WorkRef(title = (Some("no doi")).map(Title.apply)),
      Candidate.Query("q2") -> w("10.1000/d", WorkRef.InDb(false)),
      Candidate.Query("q2") -> WorkRef(
        doi = (Some("10.1000/e")).map(Doi.apply),
        isRetracted = WorkRef.IsRetracted(true)
      )
    )
    val (cs, skipped) = candidates(found, queued = Set(domain.library.Doi("10.1000/c")), max = Limit(1))
    assertEquals(
      cs.map(c => (c.doi, c.query)),
      List((domain.library.Doi("10.1000/a"), Candidate.Query("q1")))
    )
    assertEquals(
      skipped.map((k, v) => k.said -> v),
      Map("in the corpus" -> 1, "already queued" -> 1, "no DOI" -> 1, "retracted" -> 1, "over the sweep limit" -> 1)
    )

  test("a ranking may only pick listed candidates"):
    val c = Candidate(
      domain.library.Doi("10.1000/a"),
      Title("A"),
      Some(Year(2020)),
      Nil,
      None,
      isOa = WorkRef.IsOa(true),
      Candidate.Query("q"),
      None
    )
    val ok = checkRanking(
      Ranking(
        List(
          Pick(Pick.DoiText("https://doi.org/10.1000/A"), AcquisitionSweepRules.Rationale("fits")),
          Pick(Pick.DoiText("10.1000/a"), AcquisitionSweepRules.Rationale("twice"))
        )
      ),
      List(c),
      last = LastAttempt(false)
    )
    assertEquals(ok.map(_._1.map((c, p) => (c, s"$p"))), Right(List((c, "fits"))))
    assert(
      checkRanking(
        Ranking(List(Pick(Pick.DoiText("10.9999/zzz"), AcquisitionSweepRules.Rationale("invented")))),
        List(c),
        last = LastAttempt(false)
      ).isLeft
    )
    assertEquals(
      checkRanking(
        Ranking(List(Pick(Pick.DoiText("10.9999/zzz"), AcquisitionSweepRules.Rationale("invented")))),
        List(c),
        last = LastAttempt(true)
      ).map(_._1),
      Right(Nil)
    )
    assertEquals(
      acquireText(c, Goal.Slug("g"), AcquisitionSweepRules.Rationale("fits")),
      "(2020). A. https://doi.org/10.1000/a (open access) — for goal g: fits"
    )

  test("ingestPicks: open-access picks in order until the cap of SUCCESSFUL ingests; a failure tries the next"):
    val picks = List("a" -> true, "b" -> false, "c" -> true, "d" -> true, "e" -> true)
    val tried = scala.collection.mutable.ListBuffer.empty[String]
    val got = AcquisitionSweepRules.ingestPicks(picks, (_: (String, Boolean))._2, Allowance(2)) { (n, _) =>
      tried += n
      Option.when(n != "c")(n.toUpperCase)
    }
    assertEquals(got.map(_._2), List("A", "D"))
    assertEquals(tried.toList, List("a", "c", "d"), "closed ones are never offered, nothing past the cap")
    assertEquals(
      AcquisitionSweepRules.ingestPicks(picks, (_: (String, Boolean))._2, Allowance(0))(p => Some(p._1)),
      Nil
    )

  test(
    "ingestOa: the same order and cap as ingestPicks, and what failed with why (the unoffered rest is the caller's)"
  ):
    val picks = List("a" -> true, "b" -> false, "c" -> true, "d" -> true, "e" -> true)
    val (ok, ko) = AcquisitionSweepRules.ingestOa(picks, (_: (String, Boolean))._2, Allowance(2)) { (n, _) =>
      if n == "c" then Left(Problem.NotFound.Copy(Problem.Raw("c"), Vector.empty))
      else Right(n.toUpperCase)
    }
    assertEquals(ok.map(_._2), List("A", "D"))
    assertEquals(
      ko.map((p, why) => (p._1, why.sentences)),
      List("c" -> Vector("no legal open-access PDF found for c"))
    )
    assertEquals(
      AcquisitionSweepRules.ingestOa(picks, (_: (String, Boolean))._2, Allowance(0))(p => Right(p._1)),
      (Nil, Nil),
      "nothing offered at 0"
    )
