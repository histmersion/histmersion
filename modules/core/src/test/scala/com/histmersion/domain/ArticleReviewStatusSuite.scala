package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.research.{ReviewApproval, ReviewState}
import com.histmersion.domain.ArticleReviewStatus.*
import com.histmersion.domain.ReviewArticle.{Finding, Reviewer, Severity}
import com.histmersion.domain.ReviewLedger.{Closure, Entry, Ledger, Status}
import com.histmersion.domain.automation.ItemStatus

class ArticleReviewStatusSuite extends munit.FunSuite:

  private def finding(id: String, r: Reviewer, s: Severity): Finding =
    Finding(id, r, s, "undefined-term", "Background ¶2", "a quote", s"problem $id", s"fix $id")

  private val ledgerFacts = LedgerFacts(openMajors = 0, openMinors = 0, findings = 0, runs = 0)

  test("no item and no ledger: not reviewed"):
    val b = badge(Facts())
    assertEquals(b.state, ReviewState.Unreviewed)
    assertEquals(b.label, "not reviewed")
    assert(b.detail.startsWith("Not reviewed yet"), b.detail)

  test("an empty ledger and no item is still not reviewed; a cancelled item is no item"):
    assertEquals(badge(Facts(ledger = Some(ledgerFacts))).state, ReviewState.Unreviewed)
    assertEquals(badge(Facts(item = Some(ItemFacts(ItemStatus.Cancelled)))).state, ReviewState.Unreviewed)
    val reviewed = Some(ledgerFacts.modify(_.openMajors).setTo(2).modify(_.findings).setTo(5).modify(_.runs).setTo(1))
    assertEquals(badge(Facts(Some(ItemFacts(ItemStatus.Cancelled)), reviewed)).state, ReviewState.MajorsOpen)

  test("a pending item that never ran is queued"):
    val b = badge(Facts(item = Some(ItemFacts(ItemStatus.Pending))))
    assertEquals(b.state, ReviewState.Queued)
    assertEquals(b.label, "queued")
    assert(b.detail.contains("when a run slot is free"), b.detail)

  test("a pending item after a run says how the run ended and what is open"):
    val b = badge(
      Facts(
        item =
          Some(ItemFacts(ItemStatus.Pending, Some("1 open MAJOR after run 1; again after a 30-minute cooldown"), 1)),
        ledger = Some(LedgerFacts(1, 4, 20, 1))
      )
    )
    assertEquals(b.state, ReviewState.Queued)
    assertEquals(
      b.detail,
      "Queued for review. Run 1 ended without converging: 1 open MAJOR after run 1; again after a 30-minute cooldown. " +
        "Open so far: 1 MAJOR, 4 minor."
    )
    assertEquals(badge(Facts(item = Some(ItemFacts(ItemStatus.Held)))).state, ReviewState.Queued)

  test("an active item is in review, with its run and the pass it is in"):
    val b = badge(
      Facts(item = Some(ItemFacts(ItemStatus.Active, None, 2)), ledger = Some(LedgerFacts(2, 1, 9, 2)), round = Some(3))
    )
    assertEquals(b.state, ReviewState.InReview)
    assertEquals(b.label, "in review · round 3")
    assertEquals(b.round, Some(3))
    assertEquals(
      b.detail,
      "In review: the review panel is working on it now (round 3 of this run). Open so far: 2 MAJOR, 1 minor."
    )
    val first = badge(Facts(item = Some(ItemFacts(ItemStatus.Active))))
    assertEquals(first.label, "in review")
    assertEquals(first.detail, "In review: the review panel is working on it now.")

  test("a round outside a run in progress is not reported"):
    val b = badge(
      Facts(
        item = Some(ItemFacts(ItemStatus.Done, Some("converged after 1 run"), 1)),
        ledger = Some(LedgerFacts(0, 0, 3, 1)),
        round = Some(2)
      )
    )
    assertEquals(b.round, None)

  test("done with no open MAJOR: passed, with the minors still open and the manager's word"):
    val b = badge(
      Facts(
        item = Some(ItemFacts(ItemStatus.Done, Some("converged after 1 run"), 1)),
        ledger = Some(LedgerFacts(0, 2, 7, 1))
      )
    )
    assertEquals(b.state, ReviewState.Passed)
    assertEquals(b.label, "passed")
    assertEquals(
      b.detail,
      "Reviewed: passed — no MAJOR finding is open. 2 minor findings still open. 7 findings raised over 1 run. " +
        "Review manager: converged after 1 run."
    )

  test("a ledger without an item (a review run outside the manager) counts as reviewed"):
    val b = badge(Facts(ledger = Some(LedgerFacts(0, 0, 4, 2))))
    assertEquals(b.state, ReviewState.Passed)
    assertEquals(b.detail, "Reviewed: passed — no MAJOR finding is open. 4 findings raised over 2 runs.")

  test("done but MAJOR findings open: majors open"):
    val b = badge(Facts(item = Some(ItemFacts(ItemStatus.Done, None, 2)), ledger = Some(LedgerFacts(3, 0, 12, 2))))
    assertEquals(b.state, ReviewState.MajorsOpen)
    assertEquals(b.label, "3 majors open")
    assertEquals(b.openMajors, 3)
    assertEquals(b.detail, "Reviewed: 3 MAJOR findings are still open. Found over 2 runs.")

  test("a stopped item: stopped, the manager's reason without its own 'stopped:' lead"):
    val b = badge(
      Facts(
        item = Some(
          ItemFacts(
            ItemStatus.Stopped,
            Some("stopped: the open MAJORs did not go down over 2 runs (now 1) (task #12)"),
            2
          )
        ),
        ledger = Some(LedgerFacts(1, 0, 30, 1))
      )
    )
    assertEquals(b.state, ReviewState.Stopped)
    assertEquals(b.label, "review stopped")
    assertEquals(
      b.detail,
      "Review stopped after 2 runs: the open MAJORs did not go down over 2 runs (now 1) (task #12). " +
        "1 MAJOR finding is still open. A person decides what happens next."
    )
    val failed = badge(Facts(item = Some(ItemFacts(ItemStatus.Failed, Some("failed: no model"), 1))))
    assertEquals(failed.state, ReviewState.Stopped)
    assertEquals(failed.label, "review failed")
    val older =
      badge(Facts(item = Some(ItemFacts(ItemStatus.Stopped, None, 1)), ledger = Some(LedgerFacts(2, 0, 9, 3))))
    assert(older.detail.startsWith("Review stopped after 3 runs."), older.detail)

  test("approvals waiting for a person are named in the detail"):
    val b = badge(Facts(waiting = List(ReviewApproval(741, "article-filter"))))
    assert(b.detail.endsWith(" Waiting for a person: approval #741 (article-filter)."), b.detail)

  // ── history ────────────────────────────────────────────────────────────────────────────────

  private val ledger = Ledger(
    "zz-art",
    List(
      Entry(
        finding("R1", Reviewer.Rules, Severity.Major),
        "run-a",
        1,
        Status.Fixed,
        closed = Some(Closure("rules", 2, "run-a", "fixed in ¶2"))
      ),
      Entry(
        finding("S1", Reviewer.Skeptic, Severity.Major),
        "run-a",
        1,
        Status.Rejected,
        closed = Some(Closure("fixer", 1, "run-b", "the record says otherwise"))
      ),
      Entry(finding("C1", Reviewer.ColdReader, Severity.Minor), "run-a", 2),
      Entry(finding("M1", Reviewer.Mechanical, Severity.Major), "run-b", 1, deferred = Some("run-b"), reopened = 1)
    )
  )

  test("LedgerFacts counts open findings by severity and every run that raised or closed one"):
    assertEquals(LedgerFacts.of(ledger), LedgerFacts(openMajors = 1, openMinors = 1, findings = 4, runs = 2))

  test("the history groups findings by run and pass, oldest first, with what became of each"):
    val h = history(ledger, Map("run-a" -> "2026-10-01T10:00:00Z", "run-b" -> "2026-10-02T10:00:00Z"))
    assertEquals(
      h.map(r => (r.runNumber, r.round, r.findings.map(_.id))),
      List((1, 1, List("R1", "S1")), (1, 2, List("C1")), (2, 1, List("M1")))
    )
    assertEquals(h.head.startedAt, Some("2026-10-01T10:00:00Z"))
    val s1 = h.head.findings(1)
    assertEquals(
      (s1.reviewer, s1.outcome, s1.note, s1.closedBy, s1.closedRunNumber, s1.closedRound),
      ("skeptic", "rejected", "the record says otherwise", Some("fixer"), Some(2), Some(1))
    )
    val m1 = h.last.findings.head
    assert(m1.deferred && m1.outcome == "open" && m1.reopened == 1, m1.toString)

  test("runs order by their start; a run with no record left goes first; otherwise ledger order"):
    assertEquals(
      runOrder(ledger, Map("run-a" -> "2026-10-03T00:00:00Z", "run-b" -> "2026-10-02T00:00:00Z")),
      List("run-b", "run-a")
    )
    assertEquals(runOrder(ledger, Map("run-a" -> "2026-10-03T00:00:00Z")), List("run-b", "run-a"))
    assertEquals(runOrder(ledger, Map.empty), List("run-a", "run-b"))
    assertEquals(history(Ledger("zz-empty"), Map.empty), Nil)
