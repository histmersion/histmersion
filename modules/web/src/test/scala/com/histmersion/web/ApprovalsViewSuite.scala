package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.api.automation.Goal.Plans.Plan

/** The pure parts of the approvals window. */
class ApprovalsViewSuite extends munit.FunSuite:

  // #882 (headless-Chrome check): a plan approval printed its rationale twice — once as the
  // evidence line, once at the top of the plan block
  test("a plan's rationale is shown once, by the plan block; other evidence keeps its line"):
    val plan = ApprovalInfo(
      com.histmersion.api.automation.Agents.ApprovalInfo.Id(1),
      Some(com.histmersion.api.automation.Agents.RunInfo.Id("r")),
      com.histmersion.api.automation.Agents.ApprovalInfo.Kind("goal-plan"),
      com.histmersion.api.library.Citekey(""),
      evidence = com.histmersion.api.automation.Agents.ApprovalInfo.Evidence("tin sources and their dating"),
      plan = Some(Plan(com.histmersion.api.automation.Goal.Slug("g"), rationale = com.histmersion.api.automation.Goal.Plans.Plan.Rationale("tin sources and their dating"))),
      subject = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Subject("g"))
    )
    assertEquals(ApprovalsView.evidenceLine(plan), None)
    val status = ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(2), Some(com.histmersion.api.automation.Agents.RunInfo.Id("r")), com.histmersion.api.automation.Agents.ApprovalInfo.Kind("status-change"), com.histmersion.api.library.Citekey("a2020_b"), evidence = com.histmersion.api.automation.Agents.ApprovalInfo.Evidence("a retraction notice"))
    assertEquals(ApprovalsView.evidenceLine(status), Some("a retraction notice"))
    assertEquals(ApprovalsView.evidenceLine(status.modify(_.evidence).setTo(ApprovalInfo.Evidence(""))), None)

  test("a model's hint action reads as what it does to which manager; the phase 2 kinds read as themselves"):
    import com.histmersion.api.automation.Managers.{HintEffect, HintProposal}
    val h = ApprovalInfo(
      com.histmersion.api.automation.Agents.ApprovalInfo.Id(3),
      None,
      com.histmersion.api.automation.Agents.ApprovalInfo.Kind("hint-effect"),
      com.histmersion.api.library.Citekey(""),
      subject = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Subject("review")),
      hintEffect = Some(
        HintProposal(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.HintProposal.Hint(7), com.histmersion.api.automation.Managers.HintProposal.Text("wait with tin"), HintEffect(com.histmersion.api.automation.Managers.HintEffect.Action("hold"), List(com.histmersion.api.automation.Managers.HintEffect.Targets("tin")), until = Some(com.histmersion.api.automation.Managers.HintEffect.Until("2026-12-01"))), com.histmersion.api.automation.Managers.HintProposal.By("mcp"))
      )
    )
    assertEquals(ApprovalsView.describe(h), "manager review: hold tin until 2026-12-01")
    val plan = ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(4), None, com.histmersion.api.automation.Agents.ApprovalInfo.Kind("article-plan"), com.histmersion.api.library.Citekey(""), subject = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Subject("g")))
    assertEquals(ApprovalsView.describe(plan), "compile 0 new articles for goal g")
