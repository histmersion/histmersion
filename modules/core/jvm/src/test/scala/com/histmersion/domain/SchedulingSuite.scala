package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.Agents.{ApprovalKind, RunState}
import com.histmersion.domain.Scheduling.*
import com.histmersion.values.EdgeKind

/** The schedules' pure rules: when a due schedule may start, what a sweep may queue, how a ranking is checked, and what
  * a currency check makes of a citing paper.
  */
class SchedulingSuite extends munit.FunSuite:

  private val zero = BigDecimal(0)

  test("start only when the previous run has settled, the caps leave room and a slot is free"):
    assertEquals(decide(None, zero, zero, 10, 150, active = 0, maxConcurrent = 4), Decision.Start)
    assertEquals(decide(Some(RunState.Done), zero, zero, 10, 150, 0, 4), Decision.Start)
    assertEquals(decide(Some(RunState.Failed), zero, zero, 10, 150, 0, 4), Decision.Start)
    assert(decide(Some(RunState.Paused), zero, zero, 10, 150, 0, 4).isInstanceOf[Decision.Skip])
    assert(decide(Some(RunState.WaitingApproval), zero, zero, 10, 150, 0, 4).isInstanceOf[Decision.Skip])

  test("the daily and monthly caps are checked BEFORE a start (skip); a full pool defers"):
    decide(None, BigDecimal(10), zero, 10, 150, 0, 4) match
      case Decision.Skip(why) => assert(why.contains("daily-usd"), why)
      case other              => fail(s"expected a skip, got $other")
    decide(None, BigDecimal(1), BigDecimal(150), 10, 150, 0, 4) match
      case Decision.Skip(why) => assert(why.contains("monthly-usd"), why)
      case other              => fail(s"expected a skip, got $other")
    assert(decide(None, zero, zero, 10, 150, active = 4, maxConcurrent = 4).isInstanceOf[Decision.Defer])

  test("a scheduled run's cap: the schedule's, else the goal's, else the server's"):
    assertEquals(budget(Some(0.5), Some(1.0), 2.0), 0.5)
    assertEquals(budget(None, Some(1.0), 2.0), 1.0)
    assertEquals(budget(None, None, 2.0), 2.0)
    assertEquals(budget(Some(0), None, 2.0), 2.0)

  test("DOIs are found in an item's text whatever their form"):
    assertEquals(dois("Smith (2020). Tin. https://doi.org/10.1000/ABC.1 — for goal g"), Set("10.1000/abc.1"))
    assertEquals(dois("doi:10.5555/x and 10.6666/y."), Set("10.5555/x", "10.6666/y"))
    assertEquals(dois("no doi here"), Set.empty[String])

  test("a sweep never offers what the corpus or the queue has; each work once; capped"):
    val w = (doi: String, inDb: Boolean) => WorkRef(doi = Some(doi), title = Some(doi), inDb = inDb)
    val found = List(
      "q1" -> w("10.1000/a", false),
      "q1" -> w("10.1000/b", true),
      "q2" -> w("10.1000/a", false),
      "q2" -> w("10.1000/c", false),
      "q2" -> WorkRef(title = Some("no doi")),
      "q2" -> w("10.1000/d", false),
      "q2" -> WorkRef(doi = Some("10.1000/e"), isRetracted = true)
    )
    val (cs, skipped) = candidates(found, queued = Set("10.1000/c"), max = 1)
    assertEquals(cs.map(c => (c.doi, c.query)), List(("10.1000/a", "q1")))
    assertEquals(
      skipped,
      Map("in the corpus" -> 1, "already queued" -> 1, "no DOI" -> 1, "retracted" -> 1, "over the sweep limit" -> 1)
    )

  test("a ranking may only pick listed candidates"):
    val c = Candidate("10.1000/a", "A", Some(2020), Nil, None, isOa = true, "q", None)
    val ok = checkRanking(
      Ranking(List(Pick("https://doi.org/10.1000/A", "fits"), Pick("10.1000/a", "twice"))),
      List(c),
      last = false
    )
    assertEquals(ok.map(_._1), Right(List((c, "fits"))))
    assert(checkRanking(Ranking(List(Pick("10.9999/zzz", "invented"))), List(c), last = false).isLeft)
    assertEquals(
      checkRanking(Ranking(List(Pick("10.9999/zzz", "invented"))), List(c), last = true).map(_._1),
      Right(Nil)
    )
    assertEquals(acquireText(c, "g", "fits"), "(2020). A. https://doi.org/10.1000/a (open access) — for goal g: fits")

  test(
    "currency: a disagreeing paper in the corpus is an edge proposal (supersedes points older → newer), outside it an acquisition"
  ):
    val row = FollowupRow(
      title = Some("New"),
      year = Some(2024),
      doi = Some("10.1000/new"),
      contexts = List("we refute X"),
      proposedKind = "refutes",
      citekey = Some("new2024_x")
    )
    currencyAction("old2000_y", row) match
      case CurrencyAction.Propose(p) =>
        assertEquals(
          (p.kind, p.citekey, p.edgeKind, p.target),
          (ApprovalKind.Edge, "new2024_x", Some(EdgeKind.Refutes), Some("old2000_y"))
        )
        assert(p.evidence.contains("we refute X"), p.evidence)
      case other => fail(s"expected a proposal, got $other")
    currencyAction("old2000_y", row.copy(proposedKind = "supersedes")) match
      case CurrencyAction.Propose(p) =>
        assertEquals((p.citekey, p.edgeKind, p.target), ("old2000_y", Some(EdgeKind.SupersededBy), Some("new2024_x")))
      case other => fail(s"expected a proposal, got $other")
    assertEquals(currencyAction("old2000_y", row.copy(citekey = None)), CurrencyAction.Fetch(row.copy(citekey = None)))
    assertEquals(currencyAction("old2000_y", row.copy(proposedKind = "confirms")), CurrencyAction.Ignore)
    assertEquals(currencyAction("old2000_y", row.copy(proposedKind = "cites")), CurrencyAction.Ignore)
    assertEquals(currencyAction("new2024_x", row), CurrencyAction.Ignore, "a paper citing itself proposes nothing")

  test("ingestPicks: open-access picks in order until the cap of SUCCESSFUL ingests; a failure tries the next"):
    val picks = List("a" -> true, "b" -> false, "c" -> true, "d" -> true, "e" -> true)
    val tried = scala.collection.mutable.ListBuffer.empty[String]
    val got = Scheduling.ingestPicks(picks, (_: (String, Boolean))._2, 2) { (n, _) =>
      tried += n
      Option.when(n != "c")(n.toUpperCase)
    }
    assertEquals(got.map(_._2), List("A", "D"))
    assertEquals(tried.toList, List("a", "c", "d"), "closed ones are never offered, nothing past the cap")
    assertEquals(Scheduling.ingestPicks(picks, (_: (String, Boolean))._2, 0)(p => Some(p._1)), Nil)

  test(
    "ingestOa: the same order and cap as ingestPicks, and what failed with why (the unoffered rest is the caller's)"
  ):
    val picks = List("a" -> true, "b" -> false, "c" -> true, "d" -> true, "e" -> true)
    val (ok, ko) = Scheduling.ingestOa(picks, (_: (String, Boolean))._2, 2) { (n, _) =>
      if n == "c" then Left("no legal open-access copy of c") else Right(n.toUpperCase)
    }
    assertEquals(ok.map(_._2), List("A", "D"))
    assertEquals(ko.map((p, why) => (p._1, why)), List("c" -> "no legal open-access copy of c"))
    assertEquals(
      Scheduling.ingestOa(picks, (_: (String, Boolean))._2, 0)(p => Right(p._1)),
      (Nil, Nil),
      "nothing offered at 0"
    )
