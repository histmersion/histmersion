package com.histmersion.web

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.api.automation.GoalPlans.Plan

/** The pure parts of the approvals window. */
class ApprovalsViewSuite extends munit.FunSuite:

  // #882 (headless-Chrome check): a plan approval printed its rationale twice — once as the
  // evidence line, once at the top of the plan block
  test("a plan's rationale is shown once, by the plan block; other evidence keeps its line"):
    val plan = ApprovalInfo(
      1,
      Some("r"),
      "goal-plan",
      "",
      evidence = "tin sources and their dating",
      plan = Some(Plan("g", rationale = "tin sources and their dating")),
      subject = Some("g")
    )
    assertEquals(ApprovalsView.evidenceLine(plan), None)
    val status = ApprovalInfo(2, Some("r"), "status-change", "a2020_b", evidence = "a retraction notice")
    assertEquals(ApprovalsView.evidenceLine(status), Some("a retraction notice"))
    assertEquals(ApprovalsView.evidenceLine(status.copy(evidence = "")), None)

  test("a model's hint action reads as what it does to which manager; the phase 2 kinds read as themselves"):
    import com.histmersion.api.automation.Managers.{HintEffect, HintProposal}
    val h = ApprovalInfo(
      3,
      None,
      "hint-effect",
      "",
      subject = Some("review"),
      hintEffect = Some(
        HintProposal("review", 7, "wait with tin", HintEffect("hold", List("tin"), until = Some("2026-12-01")), "mcp")
      )
    )
    assertEquals(ApprovalsView.describe(h), "manager review: hold tin until 2026-12-01")
    val plan = ApprovalInfo(4, None, "article-plan", "", subject = Some("g"))
    assertEquals(ApprovalsView.describe(plan), "compile 0 new articles for goal g")
