package com.histmersion.api

import io.circe.parser.decode
import io.circe.syntax.*

/** The review view's words: reviewer names, a finding's fate, the Markdown every surface returns, and the wire shape of
  * the state.
  */
class ArticleReviewViewSuite extends munit.FunSuite:

  private val rejected = ReviewFinding(
    "S2",
    "skeptic",
    "major",
    "omitted-critique",
    "Debate",
    "no critique",
    "",
    "",
    "rejected",
    note = "the record does not bear on it",
    closedBy = Some("fixer"),
    closedRound = Some(2),
    closedRunNumber = Some(1)
  )

  test("reviewers by the names a person reads"):
    assertEquals(
      List("rules", "cold-reader", "skeptic", "mechanical", "fixer").map(ArticleReviewView.reviewerName),
      List("rules reviewer", "cold reader", "skeptic", "mechanical check", "fixer")
    )

  test("a finding's fate in words"):
    assertEquals(
      ArticleReviewView.outcomeLine(rejected),
      "rejected by fixer in run 1, round 2: the record does not bear on it"
    )
    val open = rejected.copy(outcome = "open", note = "", closedBy = None, closedRound = None, closedRunNumber = None)
    assertEquals(ArticleReviewView.outcomeLine(open), "open")
    assertEquals(
      ArticleReviewView.outcomeLine(open.copy(deferred = true, reopened = 2)),
      "open (left for the next run) (reopened 2 times)"
    )
    assertEquals(ArticleReviewView.outcomeLine(open.copy(outcome = "wont-fix")), "won't fix")

  test("the Markdown: state, goals, approvals waiting, then the history by run and round"):
    val v = ArticleReviewView(
      "zz-art",
      ReviewBadge(ReviewState.Passed, "passed", "Reviewed: passed — no MAJOR finding is open."),
      outcome = Some("converged after 1 run"),
      updatedAt = Some("2026-10-02T09:00:00Z"),
      rounds = List(ReviewRound("r", 1, 1, None, List(rejected))),
      goals = List(ReviewGoal("g", "A goal", "reviewed")),
      approvals = List(ReviewApproval(7, "article-filter"))
    )
    val md = ArticleReviewView.markdown(v)
    assert(md.startsWith("## Review of [[zz-art]]\n\n**passed** — Reviewed: passed — no MAJOR finding is open."), md)
    assert(md.contains("Review manager: converged after 1 run (2026-10-02T09:00:00Z)"), md)
    assert(md.contains("- `g` (reviewed) A goal"), md)
    assert(md.contains("- approval #7 (article-filter)"), md)
    assert(
      md.contains(
        "#### Run 1, round 1\n\n- **S2** MAJOR · skeptic · omitted-critique · Debate: no critique — " +
          "*rejected by fixer in run 1, round 2: the record does not bear on it*"
      ),
      md
    )
    assert(ArticleReviewView.markdown(v.copy(rounds = Nil)).endsWith("No finding recorded."))

  test("the state crosses the wire as its word"):
    val b = ReviewBadge(ReviewState.InReview, "in review · round 2", "In review.", round = Some(2))
    assert(b.asJson.noSpaces.contains("\"state\":\"in-review\""), b.asJson.noSpaces)
    assertEquals(decode[ReviewBadge](b.asJson.noSpaces), Right(b))
    assertEquals(ReviewState.parse(" Majors-Open "), Some(ReviewState.MajorsOpen))
    assertEquals(GoalStages.all.map(_._1), com.histmersion.values.GoalStatus.all)
