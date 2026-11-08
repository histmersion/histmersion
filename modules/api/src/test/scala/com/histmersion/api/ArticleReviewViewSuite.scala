package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import io.circe.parser.decode
import io.circe.syntax.*
import com.histmersion.api.research.{ReviewApproval, ReviewBadge, ReviewFinding, ReviewGoal, ReviewRound, ReviewState}
import com.histmersion.api.automation.Goal
import com.histmersion.api.research.Article
import com.histmersion.api.automation.Activity

/** The review view's words: reviewer names, a finding's fate, the Markdown every surface returns, and the wire shape of
  * the state.
  */
class ArticleReviewViewSuite extends munit.FunSuite:

  private val rejected = ReviewFinding(
    ReviewFinding.Id("S2"),
    ReviewFinding.Reviewer("skeptic"),
    ReviewFinding.Severity("major"),
    ReviewFinding.Kind("omitted-critique"),
    ReviewFinding.Where("Debate"),
    ReviewFinding.Problem("no critique"),
    ReviewFinding.Fix(""),
    ReviewFinding.Quote(""),
    ReviewFinding.Outcome("rejected"),
    note = ReviewFinding.Note("the record does not bear on it"),
    closedBy = Some(Activity.Actor("fixer")),
    closedRound = Some(ReviewRound.Round(2)),
    closedRunNumber = Some(ReviewRound.RunNumber(1))
  )

  test("reviewers by the names a person reads"):
    assertEquals(
      List("rules", "cold-reader", "skeptic", "mechanical", "fixer").map(r => Article.ReviewView.reviewerName(ReviewFinding.Reviewer(r))),
      List("rules reviewer", "cold reader", "skeptic", "mechanical check", "fixer")
    )

  test("a finding's fate in words"):
    assertEquals(
      Article.ReviewView.outcomeLine(rejected),
      "rejected by fixer in run 1, round 2: the record does not bear on it"
    )
    val open = rejected.modify(_.outcome).setTo(ReviewFinding.Outcome("open")).modify(_.note).setTo(ReviewFinding.Note("")).modify(_.closedBy).setTo(None).modify(_.closedRound).setTo(None).modify(_.closedRunNumber).setTo(None)
    assertEquals(Article.ReviewView.outcomeLine(open), "open")
    assertEquals(
      Article.ReviewView.outcomeLine(open.modify(_.deferred).setTo(ReviewFinding.Deferred(true)).modify(_.reopened).setTo(ReviewFinding.Reopened(2))),
      "open (left for the next run) (reopened 2 times)"
    )
    assertEquals(Article.ReviewView.outcomeLine(open.modify(_.outcome).setTo(ReviewFinding.Outcome("wont-fix"))), "won't fix")

  test("the Markdown: state, goals, approvals waiting, then the history by run and round"):
    val v = Article.ReviewView(
      Article.Slug("zz-art"),
      ReviewBadge(ReviewState.Passed, ReviewBadge.Label("passed"), ReviewBadge.Detail("Reviewed: passed — no MAJOR finding is open.")),
      outcome = Some(Article.ReviewView.Outcome("converged after 1 run")),
      updatedAt = Some(Article.UpdatedAt("2026-10-02T09:00:00Z")),
      rounds = List(ReviewRound(ReviewRound.Run("r"), ReviewRound.RunNumber(1), ReviewRound.Round(1), None, List(rejected))),
      goals = List(ReviewGoal(ReviewGoal.Slug("g"), ReviewGoal.Title("A goal"), ReviewGoal.Status("reviewed"))),
      approvals = List(ReviewApproval(ReviewApproval.Id(7), ReviewApproval.Kind("article-filter")))
    )
    val md = Article.ReviewView.markdown(v)
    assert(md.value.startsWith("## Review of [[zz-art]]\n\n**passed** — Reviewed: passed — no MAJOR finding is open."), md)
    assert(md.value.contains("Review manager: converged after 1 run (2026-10-02T09:00:00Z)"), md)
    assert(md.value.contains("- `g` (reviewed) A goal"), md)
    assert(md.value.contains("- approval #7 (article-filter)"), md)
    assert(
      md.value.contains(
        "#### Run 1, round 1\n\n- **S2** MAJOR · skeptic · omitted-critique · Debate: no critique — " +
          "*rejected by fixer in run 1, round 2: the record does not bear on it*"
      ),
      md
    )
    assert(Article.ReviewView.markdown(v.modify(_.rounds).setTo(Nil)).value.endsWith("No finding recorded."))

  test("the state crosses the wire as its word"):
    val b = ReviewBadge(ReviewState.InReview, ReviewBadge.Label("in review · round 2"), ReviewBadge.Detail("In review."), round = Some(ReviewRound.Round(2)))
    assert(b.asJson.noSpaces.contains("\"state\":\"in-review\""), b.asJson.noSpaces)
    assertEquals(decode[ReviewBadge](b.asJson.noSpaces), Right(b))
    assertEquals(ReviewState.parse(ReviewState.Wire(" Majors-Open ")), Some(ReviewState.MajorsOpen))
    assertEquals(Goal.Stages.all.map(_._1), Goal.Status.all)
