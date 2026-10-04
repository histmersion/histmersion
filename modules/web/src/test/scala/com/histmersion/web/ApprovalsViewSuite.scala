package com.histmersion.web

import com.histmersion.core.Agents.ApprovalInfo
import com.histmersion.core.GoalPlans.Plan

/** The pure parts of the approvals window. */
class ApprovalsViewSuite extends munit.FunSuite:

  // #882 (headless-Chrome check): a plan approval printed its rationale twice — once as the
  // evidence line, once at the top of the plan block
  test("a plan's rationale is shown once, by the plan block; other evidence keeps its line"):
    val plan = ApprovalInfo(1, Some("r"), "goal-plan", "", evidence = "tin sources and their dating",
      plan = Some(Plan("g", rationale = "tin sources and their dating")), subject = Some("g"))
    assertEquals(ApprovalsView.evidenceLine(plan), None)
    val status = ApprovalInfo(2, Some("r"), "status-change", "a2020_b", evidence = "a retraction notice")
    assertEquals(ApprovalsView.evidenceLine(status), Some("a retraction notice"))
    assertEquals(ApprovalsView.evidenceLine(status.copy(evidence = "")), None)
