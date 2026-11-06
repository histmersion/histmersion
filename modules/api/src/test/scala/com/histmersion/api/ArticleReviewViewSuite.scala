package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import io.circe.parser.decode
import io.circe.syntax.*
import com.histmersion.api.research.{ReviewApproval, ReviewBadge, ReviewFinding, ReviewGoal, ReviewRound, ReviewState}
import com.histmersion.api.automation.Goal
import com.histmersion.api.research.Article

/** The review view's words: reviewer names, a finding's fate, the Markdown every surface returns, and the wire shape of
  * the state.
  */
class ArticleReviewViewSuite extends munit.FunSuite:

  private val rejected = ReviewFinding(
    com.histmersion.api.research.ReviewFinding.Id("S2"),
    com.histmersion.api.research.ReviewFinding.Reviewer("skeptic"),
    com.histmersion.api.research.ReviewFinding.Severity("major"),
    com.histmersion.api.research.ReviewFinding.Kind("omitted-critique"),
    com.histmersion.api.research.ReviewFinding.Where("Debate"),
    com.histmersion.api.research.ReviewFinding.Problem("no critique"),
    com.histmersion.api.research.ReviewFinding.Fix(""),
    com.histmersion.api.research.ReviewFinding.Quote(""),
    com.histmersion.api.research.ReviewFinding.Outcome("rejected"),
    note = com.histmersion.api.research.ReviewFinding.Note("the record does not bear on it"),
    closedBy = Some(com.histmersion.api.automation.Activity.Actor("fixer")),
    closedRound = Some(com.histmersion.api.research.ReviewRound.Round(2)),
    closedRunNumber = Some(com.histmersion.api.research.ReviewRound.RunNumber(1))
  )

  test("reviewers by the names a person reads"):
    assertEquals(
      List("rules", "cold-reader", "skeptic", "mechanical", "fixer").map(Article.ReviewView.reviewerName),
      List("rules reviewer", "cold reader", "skeptic", "mechanical check", "fixer")
    )

  test("a finding's fate in words"):
    assertEquals(
      Article.ReviewView.outcomeLine(rejected),
      "rejected by fixer in run 1, round 2: the record does not bear on it"
    )
    val open = rejected.modify(_.outcome).setTo("open").modify(_.note).setTo("").modify(_.closedBy).setTo(None).modify(_.closedRound).setTo(None).modify(_.closedRunNumber).setTo(None)
    assertEquals(Article.ReviewView.outcomeLine(open), "open")
    assertEquals(
      Article.ReviewView.outcomeLine(open.modify(_.deferred).setTo(true).modify(_.reopened).setTo(2)),
      "open (left for the next run) (reopened 2 times)"
    )
    assertEquals(Article.ReviewView.outcomeLine(open.modify(_.outcome).setTo("wont-fix")), "won't fix")

  test("the Markdown: state, goals, approvals waiting, then the history by run and round"):
    val v = Article.ReviewView(
      com.histmersion.api.research.Article.Slug("zz-art"),
      ReviewBadge(ReviewState.Passed, com.histmersion.api.research.ReviewBadge.Label("passed"), com.histmersion.api.research.ReviewBadge.Detail("Reviewed: passed — no MAJOR finding is open.")),
      outcome = Some(com.histmersion.api.research.Article.ReviewView.Outcome("converged after 1 run")),
      updatedAt = Some(com.histmersion.api.research.Article.UpdatedAt("2026-10-02T09:00:00Z")),
      rounds = List(ReviewRound(com.histmersion.api.research.ReviewRound.Run("r"), com.histmersion.api.research.ReviewRound.RunNumber(1), com.histmersion.api.research.ReviewRound.Round(1), None, List(rejected))),
      goals = List(ReviewGoal(com.histmersion.api.research.ReviewGoal.Slug("g"), com.histmersion.api.research.ReviewGoal.Title("A goal"), com.histmersion.api.research.ReviewGoal.Status("reviewed"))),
      approvals = List(ReviewApproval(com.histmersion.api.research.ReviewApproval.Id(7), com.histmersion.api.research.ReviewApproval.Kind("article-filter")))
    )
    val md = Article.ReviewView.markdown(v)
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
    assert(Article.ReviewView.markdown(v.modify(_.rounds).setTo(Nil)).endsWith("No finding recorded."))

  test("the state crosses the wire as its word"):
    val b = ReviewBadge(ReviewState.InReview, com.histmersion.api.research.ReviewBadge.Label("in review · round 2"), com.histmersion.api.research.ReviewBadge.Detail("In review."), round = Some(com.histmersion.api.research.ReviewRound.Round(2)))
    assert(b.asJson.noSpaces.contains("\"state\":\"in-review\""), b.asJson.noSpaces)
    assertEquals(decode[ReviewBadge](b.asJson.noSpaces), Right(b))
    assertEquals(ReviewState.parse(com.histmersion.api.research.ReviewState.Wire(" Majors-Open ")), Some(ReviewState.MajorsOpen))
    assertEquals(Goal.Stages.all.map(_._1), com.histmersion.api.automation.Goal.Status.all)
