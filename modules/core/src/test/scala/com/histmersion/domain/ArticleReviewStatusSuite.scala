package com.histmersion
package domain

import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Severity
import com.histmersion.domain.research.workflows.Ledger
import com.histmersion.domain.research.workflows.Entry
import com.histmersion.domain.research.workflows.Status
import com.histmersion.domain.research.workflows.Closure

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.research.{ReviewApproval, ReviewState}
import com.histmersion.domain.ArticleReviewStatus.*
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.api.research.ReviewRound
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ArticleReviewStatusSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def finding(id: Finding.Id, r: Reviewer, s: Severity): Finding =
    Finding(
      id,
      r,
      s,
      Finding.Kind("undefined-term"),
      Finding.Where("Background ¶2"),
      Finding.Quote("a quote"),
      Finding.Problem(s"problem ${id.value}"),
      Finding.Fix(s"fix ${id.value}")
    )

  private val ledgerFacts = LedgerFacts(
    openMajors = ArticleReviewStatus.LedgerFacts.OpenMajors(0),
    openMinors = ArticleReviewStatus.LedgerFacts.OpenMinors(0),
    findings = ArticleReviewStatus.LedgerFacts.Findings(0),
    runs = ArticleReviewStatus.LedgerFacts.Runs(0)
  )

  test("no item and no ledger: not reviewed"):
    val b = badge(Facts())
    assertEquals(b.state, ReviewState.Unreviewed)
    assertEquals(b.label, "not reviewed")
    assert(b.detail.value.startsWith("Not reviewed yet"), b.detail)

  test("an empty ledger and no item is still not reviewed; a cancelled item is no item"):
    assertEquals(badge(Facts(ledger = Some(ledgerFacts))).state, ReviewState.Unreviewed)
    assertEquals(badge(Facts(item = Some(ItemFacts(ItemStatus.Cancelled)))).state, ReviewState.Unreviewed)
    val reviewed = Some(
      ledgerFacts
        .modify(_.openMajors)
        .setTo(ArticleReviewStatus.LedgerFacts.OpenMajors(2))
        .modify(_.findings)
        .setTo(ArticleReviewStatus.LedgerFacts.Findings(5))
        .modify(_.runs)
        .setTo(ArticleReviewStatus.LedgerFacts.Runs(1))
    )
    assertEquals(badge(Facts(Some(ItemFacts(ItemStatus.Cancelled)), reviewed)).state, ReviewState.MajorsOpen)

  test("a pending item that never ran is queued"):
    val b = badge(Facts(item = Some(ItemFacts(ItemStatus.Pending))))
    assertEquals(b.state, ReviewState.Queued)
    assertEquals(b.label, "queued")
    assert(b.detail.value.contains("when a run slot is free"), b.detail)

  test("a pending item after a run says how the run ended and what is open"):
    val b = badge(
      Facts(
        item = Some(
          ItemFacts(
            ItemStatus.Pending,
            Some(ArticleReviewStatus.ItemFacts.Outcome("1 open MAJOR after run 1; again after a 30-minute cooldown")),
            ArticleReviewStatus.ItemFacts.Attempts(1)
          )
        ),
        ledger = Some(
          LedgerFacts(
            ArticleReviewStatus.LedgerFacts.OpenMajors(1),
            ArticleReviewStatus.LedgerFacts.OpenMinors(4),
            ArticleReviewStatus.LedgerFacts.Findings(20),
            ArticleReviewStatus.LedgerFacts.Runs(1)
          )
        )
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
      Facts(
        item = Some(ItemFacts(ItemStatus.Active, None, ArticleReviewStatus.ItemFacts.Attempts(2))),
        ledger = Some(
          LedgerFacts(
            ArticleReviewStatus.LedgerFacts.OpenMajors(2),
            ArticleReviewStatus.LedgerFacts.OpenMinors(1),
            ArticleReviewStatus.LedgerFacts.Findings(9),
            ArticleReviewStatus.LedgerFacts.Runs(2)
          )
        ),
        round = Some(ReviewRound.Round(3))
      )
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
        item = Some(
          ItemFacts(
            ItemStatus.Done,
            Some(ArticleReviewStatus.ItemFacts.Outcome("converged after 1 run")),
            ArticleReviewStatus.ItemFacts.Attempts(1)
          )
        ),
        ledger = Some(
          LedgerFacts(
            ArticleReviewStatus.LedgerFacts.OpenMajors(0),
            ArticleReviewStatus.LedgerFacts.OpenMinors(0),
            ArticleReviewStatus.LedgerFacts.Findings(3),
            ArticleReviewStatus.LedgerFacts.Runs(1)
          )
        ),
        round = Some(ReviewRound.Round(2))
      )
    )
    assertEquals(b.round, None)

  test("done with no open MAJOR: passed, with the minors still open and the manager's word"):
    val b = badge(
      Facts(
        item = Some(
          ItemFacts(
            ItemStatus.Done,
            Some(ArticleReviewStatus.ItemFacts.Outcome("converged after 1 run")),
            ArticleReviewStatus.ItemFacts.Attempts(1)
          )
        ),
        ledger = Some(
          LedgerFacts(
            ArticleReviewStatus.LedgerFacts.OpenMajors(0),
            ArticleReviewStatus.LedgerFacts.OpenMinors(2),
            ArticleReviewStatus.LedgerFacts.Findings(7),
            ArticleReviewStatus.LedgerFacts.Runs(1)
          )
        )
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
    val b = badge(
      Facts(ledger =
        Some(
          LedgerFacts(
            ArticleReviewStatus.LedgerFacts.OpenMajors(0),
            ArticleReviewStatus.LedgerFacts.OpenMinors(0),
            ArticleReviewStatus.LedgerFacts.Findings(4),
            ArticleReviewStatus.LedgerFacts.Runs(2)
          )
        )
      )
    )
    assertEquals(b.state, ReviewState.Passed)
    assertEquals(b.detail, "Reviewed: passed — no MAJOR finding is open. 4 findings raised over 2 runs.")

  test("done but MAJOR findings open: majors open"):
    val b = badge(
      Facts(
        item = Some(ItemFacts(ItemStatus.Done, None, ArticleReviewStatus.ItemFacts.Attempts(2))),
        ledger = Some(
          LedgerFacts(
            ArticleReviewStatus.LedgerFacts.OpenMajors(3),
            ArticleReviewStatus.LedgerFacts.OpenMinors(0),
            ArticleReviewStatus.LedgerFacts.Findings(12),
            ArticleReviewStatus.LedgerFacts.Runs(2)
          )
        )
      )
    )
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
            Some(
              ArticleReviewStatus.ItemFacts.Outcome(
                "stopped: the open MAJORs did not go down over 2 runs (now 1) (task #12)"
              )
            ),
            ArticleReviewStatus.ItemFacts.Attempts(2)
          )
        ),
        ledger = Some(
          LedgerFacts(
            ArticleReviewStatus.LedgerFacts.OpenMajors(1),
            ArticleReviewStatus.LedgerFacts.OpenMinors(0),
            ArticleReviewStatus.LedgerFacts.Findings(30),
            ArticleReviewStatus.LedgerFacts.Runs(1)
          )
        )
      )
    )
    assertEquals(b.state, ReviewState.Stopped)
    assertEquals(b.label, "review stopped")
    assertEquals(
      b.detail,
      "Review stopped after 2 runs: the open MAJORs did not go down over 2 runs (now 1) (task #12). " +
        "1 MAJOR finding is still open. A person decides what happens next."
    )
    val failed = badge(
      Facts(item =
        Some(
          ItemFacts(
            ItemStatus.Failed,
            Some(ArticleReviewStatus.ItemFacts.Outcome("failed: no model")),
            ArticleReviewStatus.ItemFacts.Attempts(1)
          )
        )
      )
    )
    assertEquals(failed.state, ReviewState.Stopped)
    assertEquals(failed.label, "review failed")
    val older =
      badge(
        Facts(
          item = Some(ItemFacts(ItemStatus.Stopped, None, ArticleReviewStatus.ItemFacts.Attempts(1))),
          ledger = Some(
            LedgerFacts(
              ArticleReviewStatus.LedgerFacts.OpenMajors(2),
              ArticleReviewStatus.LedgerFacts.OpenMinors(0),
              ArticleReviewStatus.LedgerFacts.Findings(9),
              ArticleReviewStatus.LedgerFacts.Runs(3)
            )
          )
        )
      )
    assert(older.detail.value.startsWith("Review stopped after 3 runs."), older.detail)

  test("approvals waiting for a person are named in the detail"):
    val b = badge(Facts(waiting = List(ReviewApproval(ReviewApproval.Id(741), ReviewApproval.Kind("article-filter")))))
    assert(b.detail.value.endsWith(" Waiting for a person: approval #741 (article-filter)."), b.detail)

  // ── history ────────────────────────────────────────────────────────────────────────────────

  private val ledger = Ledger(
    Ledger.Slug("zz-art"),
    List(
      Entry(
        finding(Finding.Id("R1"), Reviewer.Rules, Severity.Major),
        Entry.Run("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59"),
        Entry.Round(1),
        Status.Fixed,
        closed = Some(
          Closure(
            Closure.By("rules"),
            Closure.Round(2),
            Closure.Run("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59"),
            Closure.Note("fixed in ¶2")
          )
        )
      ),
      Entry(
        finding(Finding.Id("S1"), Reviewer.Skeptic, Severity.Major),
        Entry.Run("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59"),
        Entry.Round(1),
        Status.Rejected,
        closed = Some(
          Closure(
            Closure.By("fixer"),
            Closure.Round(1),
            Closure.Run("6025c753-7a4c-04dd-8ac3-22285ab559dc"),
            Closure.Note("the record says otherwise")
          )
        )
      ),
      Entry(
        finding(Finding.Id("C1"), Reviewer.ColdReader, Severity.Minor),
        Entry.Run("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59"),
        Entry.Round(2)
      ),
      Entry(
        finding(Finding.Id("M1"), Reviewer.Mechanical, Severity.Major),
        Entry.Run("6025c753-7a4c-04dd-8ac3-22285ab559dc"),
        Entry.Round(1),
        deferred = Some(Run.Id("6025c753-7a4c-04dd-8ac3-22285ab559dc")),
        reopened = Entry.Reopened(1)
      )
    )
  )

  test("LedgerFacts counts open findings by severity and every run that raised or closed one"):
    assertEquals(
      LedgerFacts.of(ledger),
      LedgerFacts(
        openMajors = ArticleReviewStatus.LedgerFacts.OpenMajors(1),
        openMinors = ArticleReviewStatus.LedgerFacts.OpenMinors(1),
        findings = ArticleReviewStatus.LedgerFacts.Findings(4),
        runs = ArticleReviewStatus.LedgerFacts.Runs(2)
      )
    )

  test("the history groups findings by run and pass, oldest first, with what became of each"):
    val h = history(
      ledger,
      Map(
        Run.Id("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59") -> ReviewRound.StartedAt("2026-10-01T10:00:00Z"),
        Run.Id("6025c753-7a4c-04dd-8ac3-22285ab559dc") -> ReviewRound.StartedAt("2026-10-02T10:00:00Z")
      )
    )
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
    assert(m1.deferred.deferred && m1.outcome.value == "open" && m1.reopened.value == 1, m1.toString)

  test("runs order by their start; a run with no record left goes first; otherwise ledger order"):
    assertEquals(
      runOrder(
        ledger,
        Map(
          Run.Id("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59") -> ReviewRound.StartedAt("2026-10-03T00:00:00Z"),
          Run.Id("6025c753-7a4c-04dd-8ac3-22285ab559dc") -> ReviewRound.StartedAt("2026-10-02T00:00:00Z")
        )
      ),
      List("6025c753-7a4c-04dd-8ac3-22285ab559dc", "f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59")
    )
    assertEquals(
      runOrder(
        ledger,
        Map(Run.Id("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59") -> ReviewRound.StartedAt("2026-10-03T00:00:00Z"))
      ),
      List("6025c753-7a4c-04dd-8ac3-22285ab559dc", "f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59")
    )
    assertEquals(
      runOrder(ledger, Map.empty),
      List("f6ab9fa2-e87c-81f0-98bc-de62a7eb2f59", "6025c753-7a4c-04dd-8ac3-22285ab559dc")
    )
    assertEquals(history(Ledger(Ledger.Slug("zz-empty")), Map.empty), Nil)
