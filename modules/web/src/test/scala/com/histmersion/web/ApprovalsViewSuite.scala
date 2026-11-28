package com.histmersion
package web

import com.histmersion.api.automation.agents.ApprovalInfo
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.managers.HintProposal
import com.histmersion.api.automation.managers.ManagerInfo
import com.histmersion.api.automation.managers.HintEffect

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Goal.Plans.Plan
import com.histmersion.api.automation.Agents
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Managers

/** The pure parts of the approvals window. */
class ApprovalsViewSuite extends munit.FunSuite:

  // #882 (headless-Chrome check): a plan approval printed its rationale twice — once as the
  // evidence line, once at the top of the plan block
  test("a plan's rationale is shown once, by the plan block; other evidence keeps its line"):
    val plan = ApprovalInfo(
      ApprovalInfo.Id(1),
      Some(RunInfo.Id("r")),
      ApprovalInfo.Kind("goal-plan"),
      Citekey(""),
      evidence = ApprovalInfo.Evidence("tin sources and their dating"),
      plan = Some(Plan(Goal.Slug("g"), rationale = Plan.Rationale("tin sources and their dating"))),
      subject = Some(ApprovalInfo.Subject("g"))
    )
    assertEquals(ApprovalsView.evidenceLine(plan), None)
    val status = ApprovalInfo(
      ApprovalInfo.Id(2),
      Some(RunInfo.Id("r")),
      ApprovalInfo.Kind("status-change"),
      Citekey("a2020_b"),
      evidence = ApprovalInfo.Evidence("a retraction notice")
    )
    assertEquals(ApprovalsView.evidenceLine(status), Some("a retraction notice"))
    assertEquals(ApprovalsView.evidenceLine(status.modify(_.evidence).setTo(ApprovalInfo.Evidence(""))), None)

  test("a model's hint action reads as what it does to which manager; the phase 2 kinds read as themselves"):
    val h = ApprovalInfo(
      ApprovalInfo.Id(3),
      None,
      ApprovalInfo.Kind("hint-effect"),
      Citekey(""),
      subject = Some(ApprovalInfo.Subject("review")),
      hintEffect = Some(
        HintProposal(
          ManagerInfo.Id("review"),
          HintProposal.Hint(7),
          HintProposal.Text("wait with tin"),
          HintEffect(
            HintEffect.Action("hold"),
            List(HintEffect.Targets("tin")),
            until = Some(HintEffect.Until("2026-12-01"))
          ),
          HintProposal.By("mcp")
        )
      )
    )
    assertEquals(ApprovalsView.describe(h), "manager review: hold tin until 2026-12-01")
    val plan = ApprovalInfo(
      ApprovalInfo.Id(4),
      None,
      ApprovalInfo.Kind("article-plan"),
      Citekey(""),
      subject = Some(ApprovalInfo.Subject("g"))
    )
    assertEquals(ApprovalsView.describe(plan), "compile 0 new articles for goal g")
