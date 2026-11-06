package com.histmersion.domain.library.workflows

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import AcquisitionSweepRules.*
import com.histmersion.api.library.WorkRef

/** An acquisition sweep's pure rules: what it may offer the ranking, how a ranking is checked, which picks are
  * ingested.
  */
class AcquisitionSweepRulesSuite extends munit.FunSuite:

  test("a sweep never offers what the corpus or the queue has; each work once; capped"):
    val w = (doi: String, inDb: Boolean) => WorkRef(doi = (Some(doi)).map(com.histmersion.api.library.Doi.apply), title = (Some(doi)).map(com.histmersion.api.library.Title.apply), inDb = inDb)
    val found = List(
      "q1" -> w("10.1000/a", false),
      "q1" -> w("10.1000/b", true),
      "q2" -> w("10.1000/a", false),
      "q2" -> w("10.1000/c", false),
      "q2" -> WorkRef(title = (Some("no doi")).map(com.histmersion.api.library.Title.apply)),
      "q2" -> w("10.1000/d", false),
      "q2" -> WorkRef(doi = (Some("10.1000/e")).map(com.histmersion.api.library.Doi.apply), isRetracted = com.histmersion.api.library.WorkRef.IsRetracted(true))
    )
    val (cs, skipped) = candidates(found, queued = Set(com.histmersion.domain.library.Doi("10.1000/c")), max = com.histmersion.domain.library.workflows.AcquisitionSweepRules.Limit(1))
    assertEquals(cs.map(c => (c.doi, c.query)), List(("10.1000/a", "q1")))
    assertEquals(
      skipped,
      Map("in the corpus" -> 1, "already queued" -> 1, "no DOI" -> 1, "retracted" -> 1, "over the sweep limit" -> 1)
    )

  test("a ranking may only pick listed candidates"):
    val c = Candidate(com.histmersion.domain.library.Doi("10.1000/a"), com.histmersion.api.library.Title("A"), Some(com.histmersion.api.catalogue.Year(2020)), Nil, None, isOa = com.histmersion.api.library.WorkRef.IsOa(true), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Candidate.Query("q"), None)
    val ok = checkRanking(
      Ranking(List(Pick(com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.DoiText("https://doi.org/10.1000/A"), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.Why("fits")), Pick(com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.DoiText("10.1000/a"), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.Why("twice")))),
      List(c),
      last = com.histmersion.domain.library.workflows.AcquisitionSweepRules.LastAttempt(false)
    )
    assertEquals(ok.map(_._1), Right(List((c, "fits"))))
    assert(checkRanking(Ranking(List(Pick(com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.DoiText("10.9999/zzz"), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.Why("invented")))), List(c), last = com.histmersion.domain.library.workflows.AcquisitionSweepRules.LastAttempt(false)).isLeft)
    assertEquals(
      checkRanking(Ranking(List(Pick(com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.DoiText("10.9999/zzz"), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.Why("invented")))), List(c), last = com.histmersion.domain.library.workflows.AcquisitionSweepRules.LastAttempt(true)).map(_._1),
      Right(Nil)
    )
    assertEquals(acquireText(c, com.histmersion.domain.automation.Goal.Slug("g"), com.histmersion.domain.library.workflows.AcquisitionSweepRules.Pick.Why("fits")), "(2020). A. https://doi.org/10.1000/a (open access) — for goal g: fits")

  test("ingestPicks: open-access picks in order until the cap of SUCCESSFUL ingests; a failure tries the next"):
    val picks = List("a" -> true, "b" -> false, "c" -> true, "d" -> true, "e" -> true)
    val tried = scala.collection.mutable.ListBuffer.empty[String]
    val got = AcquisitionSweepRules.ingestPicks(picks, (_: (String, Boolean))._2, com.histmersion.domain.library.workflows.AcquisitionSweepRules.Allowance(2)) { (n, _) =>
      tried += n
      Option.when(n != "c")(n.toUpperCase)
    }
    assertEquals(got.map(_._2), List("A", "D"))
    assertEquals(tried.toList, List("a", "c", "d"), "closed ones are never offered, nothing past the cap")
    assertEquals(AcquisitionSweepRules.ingestPicks(picks, (_: (String, Boolean))._2, com.histmersion.domain.library.workflows.AcquisitionSweepRules.Allowance(0))(p => Some(p._1)), Nil)

  test(
    "ingestOa: the same order and cap as ingestPicks, and what failed with why (the unoffered rest is the caller's)"
  ):
    val picks = List("a" -> true, "b" -> false, "c" -> true, "d" -> true, "e" -> true)
    val (ok, ko) = AcquisitionSweepRules.ingestOa(picks, (_: (String, Boolean))._2, com.histmersion.domain.library.workflows.AcquisitionSweepRules.Allowance(2)) { (n, _) =>
      if n == "c" then Left("no legal open-access copy of c") else Right(n.toUpperCase)
    }
    assertEquals(ok.map(_._2), List("A", "D"))
    assertEquals(ko.map((p, why) => (p._1, why)), List("c" -> "no legal open-access copy of c"))
    assertEquals(
      AcquisitionSweepRules.ingestOa(picks, (_: (String, Boolean))._2, com.histmersion.domain.library.workflows.AcquisitionSweepRules.Allowance(0))(p => Right(p._1)),
      (Nil, Nil),
      "nothing offered at 0"
    )
