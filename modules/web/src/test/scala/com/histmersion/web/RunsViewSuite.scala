package com.histmersion
package web

import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.agents.PauseReason
import com.histmersion.api.automation.agents.ApprovalInfo
import com.histmersion.api.automation.agents.StepInfo
import com.histmersion.api.automation.agents.Usage
import com.histmersion.api.automation.agents.WouldWrite
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Agents
import com.histmersion.api.UiLabels
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Edge
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Goal
import com.histmersion.api.research.Article
import com.histmersion.lint.{tolerated, LintRule}

/** The pure parts of the agent runs and approvals windows: what is live, the poll schedule, the filter, the actions a
  * state allows, why a run paused, the typed budget, an approval in words, and the state filters' round trip through
  * the URL hash.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class RunsViewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash(UiState.UrlHash(""))

  private def run(
      status: RunInfo.Status,
      error: Option[Problem] = None,
      pausedFor: Option[PauseReason] = None
  ) =
    RunInfo(
      RunInfo.Id("r1"),
      RunInfo.Pipeline("enrich-source"),
      RunInfo.Target("a2020_b"),
      status,
      error = error,
      pausedFor = pausedFor
    )

  test("only queued and running runs are live; the poll stops once nothing is"):
    assert(RunsView.live(RunInfo.Status("queued")) && RunsView.live(RunInfo.Status("running")))
    assert(
      !RunsView.live(RunInfo.Status("paused")) && !RunsView.live(RunInfo.Status("done")) && !RunsView
        .live(RunInfo.Status("nonsense"))
    )
    assertEquals(RunsView.nextPoll(None, anyLive = Poll.LiveMode.Paused, changed = Poll.DataChanged.Changed), None)
    assertEquals(
      RunsView.nextPoll(None, anyLive = Poll.LiveMode.Live, changed = Poll.DataChanged.Changed),
      Some(JobsView.Poll.FirstMs)
    )
    val slower = RunsView
      .nextPoll(
        Some(Poll.Revision(JobsView.Poll.FirstMs)),
        anyLive = Poll.LiveMode.Live,
        changed = Poll.DataChanged.Unchanged
      )
      .get
    assert(slower.value > JobsView.Poll.FirstMs)
    assertEquals(
      RunsView.nextPoll(
        Some(Poll.Revision(JobsView.Poll.MaxMs)),
        anyLive = Poll.LiveMode.Live,
        changed = Poll.DataChanged.Unchanged
      ),
      Some(JobsView.Poll.MaxMs)
    )

  test("the actions a state allows"):
    import RunsView.Action.*
    assertEquals(RunsView.actions(RunInfo.Status("running")), List(Pause, Cancel))
    assertEquals(RunsView.actions(RunInfo.Status("queued")), List(Pause, Cancel))
    assertEquals(RunsView.actions(RunInfo.Status("paused")), List(Resume, Cancel))
    assertEquals(RunsView.actions(RunInfo.Status("done")), Nil)
    assertEquals(RunsView.actions(RunInfo.Status("cancelled")), Nil)

  test("why a run paused, the filter, and a typed budget"):
    assertEquals(
      RunsView.pausedFor(
        run(
          RunInfo.Status("paused"),
          Some(Problem.Unclassified(Legacy.Text("budget: the run spent 2 of its 2 USD"))),
          Some(PauseReason.Budget)
        )
      ),
      Some(PauseReason.Budget)
    )
    assertEquals(
      RunsView.pausedFor(
        run(
          RunInfo.Status("paused"),
          Some(Problem.Unclassified(Legacy.Text("interrupted"))),
          Some(PauseReason.Interrupted)
        )
      ),
      Some(PauseReason.Interrupted)
    )
    assertEquals(RunsView.pausedFor(run(RunInfo.Status("done"))), None)
    val rs = List(run(RunInfo.Status("done")), run(RunInfo.Status("paused")), run(RunInfo.Status("done")))
    assertEquals(RunsView.visible(rs, Some(RunState.Done)).size, 2)
    assertEquals(RunsView.visible(rs, None).size, 3)
    assertEquals(RunsView.counts(rs), Map("done" -> 2, "paused" -> 1))
    assertEquals(RunsView.budget(UiLabels.Text(" 2.5 ")), Some(2.5))
    assertEquals(RunsView.budget(UiLabels.Text("0")), None)
    assertEquals(RunsView.budget(UiLabels.Text("two")), None)

  test("an approval in words, and which ones can still be decided"):
    val status =
      ApprovalInfo(
        ApprovalInfo.Id(1),
        None,
        ApprovalInfo.Kind("status-change"),
        Citekey("a2020_b"),
        status = Some(ApprovalInfo.Status("contested")),
        current = Some(ApprovalInfo.Current("current"))
      )
    val edge = ApprovalInfo(
      ApprovalInfo.Id(2),
      None,
      ApprovalInfo.Kind("edge"),
      Citekey("a2020_b"),
      edgeKind = Some(Edge.Kind.Wire("superseded_by")),
      target = Some(ApprovalInfo.Target("c2024_d")),
      state = ApprovalInfo.State("approved")
    )
    assertEquals(ApprovalsView.describe(status), "status of a2020_b: current → contested")
    assertEquals(ApprovalsView.describe(edge), "a2020_b superseded by c2024_d")
    assert(ApprovalsView.isOpen(status) && !ApprovalsView.isOpen(edge))
    // the open ones are the default state (nothing in the URL); every other button names its state
    assertEquals(ApprovalsView.stateValue(Approval.State.Wire("open")), None)
    assertEquals(ApprovalsView.stateValue(Approval.State.Wire("all")), Some("all"))
    assertEquals(ApprovalsView.shownState(None), "open")
    assertEquals(ApprovalsView.shownState(Some(Approval.State.Wire("rejected"))), "rejected")

  test("the runs, approvals and jobs filters live in the URL hash"):
    UiState.set(
      UiState.runStatus := Some(RunState.Paused),
      UiState.approvalState := Some("all"),
      UiState.approvalKinds := List("goal-plan", "edge"),
      UiState.runPipelines := List("review-article"),
      UiState.runManager := Some("seed:tin"),
      UiState.runSince := Some("2026-10-01"),
      UiState.jobKinds := List("ingest"),
      UiState.jobSubject := Some("a2020")
    )()
    val hash = UiState.hashString
    assert(
      hash.value.contains("runs=paused") && hash.value.contains("apprState=all") && hash.value.contains(
        "apprKind=goal-plan"
      ) &&
        hash.value.contains("apprKind=edge") && hash.value.contains("runPipeline=review-article") && hash.value
          .contains(
            "runSince=2026-10-01"
          ) &&
        hash.value.contains("jobKind=ingest") && hash.value.contains("jobSubject=a2020"),
      hash.value
    )
    UiState.loadHash(UiState.UrlHash(""))
    assertEquals(UiState.runStatus.now(), None)
    assertEquals(UiState.approvalKinds.now(), Nil)
    UiState.loadHash(hash)
    assertEquals(UiState.runStatus.now(), Some(RunState.Paused))
    assertEquals(UiState.approvalState.now(), Some("all"))
    assertEquals(UiState.approvalKinds.now(), List("goal-plan", "edge"))
    assertEquals(UiState.runManager.now(), Some("seed:tin"))
    assertEquals(UiState.jobKinds.now(), List("ingest"))
    UiState.loadHash(UiState.UrlHash("runs=bogus"))
    assertEquals(UiState.runStatus.now(), None)

  test("a run waiting for an approval: only cancel, and which approval it waits on"):
    import RunsView.Action.*
    assertEquals(RunsView.actions(RunInfo.Status("waiting")), List(Cancel))
    assert(!RunsView.live(RunInfo.Status("waiting")), "a waiting run waits for a person, not for the server")
    val w = RunInfo(
      RunInfo.Id("r2"),
      RunInfo.Pipeline("plan-goal"),
      RunInfo.Target("g"),
      RunInfo.Status("waiting"),
      progress = Some(RunInfo.Progress("approve: waiting for approval 42"))
    )
    assertEquals(RunsView.waitingFor(w), Some(42L))
    assertEquals(RunsView.waitingFor(run(RunInfo.Status("done"))), None)

  test("a goal plan and an article to publish, in words"):
    val plan = ApprovalInfo(
      ApprovalInfo.Id(3),
      Some(RunInfo.Id("r")),
      ApprovalInfo.Kind("goal-plan"),
      Citekey(""),
      subject = Some(ApprovalInfo.Subject("bronze-age")),
      plan = Some(
        Goal.Plans.Plan(
          Goal.Slug("bronze-age"),
          queries = List(Goal.Plans.Plan.Queries("tin")),
          subGoals = List(Goal.Plans.PlannedGoal(Goal.Slug("tin"), Goal.Title("Tin")))
        )
      )
    )
    assertEquals(ApprovalsView.describe(plan), "plan for goal bronze-age: 1 sub-goal, 1 query, 0 works")
    val draft = Goal.Plans.ArticleDraft(
      Article.Slug("tin"),
      Goal.Plans.DraftMode.Create,
      Article.Title("Tin"),
      Article.Kind.Wire("concept"),
      Goal.Plans.ArticleDraft.Text("x")
    )
    val pub = ApprovalInfo(
      ApprovalInfo.Id(4),
      Some(RunInfo.Id("r")),
      ApprovalInfo.Kind("article-publish"),
      Citekey(""),
      subject = Some(ApprovalInfo.Subject("tin")),
      article = Some(draft)
    )
    assertEquals(ApprovalsView.describe(pub), "publish the new article tin: Tin")
    assertEquals(
      ApprovalsView.describe(
        pub.modify(_.article).setTo(Some(draft.copy(mode = Goal.Plans.DraftMode.Extend)))
      ),
      "append a section to the article tin"
    )
    assertEquals(ApprovalsView.selfCheck(draft), "The code self-check found nothing.")
    assertEquals(
      ApprovalsView.selfCheck(
        draft.copy(problems = List(Goal.Plans.ArticleDraft.Problems("a"), Goal.Plans.ArticleDraft.Problems("b")))
      ),
      "The code self-check still found 2 problems after the writer's last attempt:"
    )

  test("a step's cells name their columns, so a narrow card can label them (#887)"):
    val s = StepInfo(
      StepInfo.Seq(3),
      StepInfo.Name("draft"),
      StepInfo.Kind("llm"),
      StepInfo.Attempt(2),
      StepInfo.Status("failed"),
      model = Some(StepInfo.Model("anthropic:claude")),
      usage = Usage(Usage.Input(120), Usage.Output(45)),
      costUsd = StepInfo.CostUsd(0.25),
      error = Some(Problem.Unclassified(Legacy.Text("provider: overloaded")))
    )
    val cells = RunsView.stepCells(s)
    assertEquals(cells.map(_._1), RunsView.StepColumns)
    assertEquals(cells.toMap.get("step"), Some("draft"))
    assertEquals(cells.toMap.get("tokens"), Some("120/45"))
    assertEquals(cells.toMap.get("note"), Some("provider: overloaded"))
    // nothing to say is an empty cell (left out of a card), never a missing column
    val bare = RunsView.stepCells(
      StepInfo(
        StepInfo.Seq(1),
        StepInfo.Name("plan"),
        StepInfo.Kind("tool"),
        StepInfo.Attempt(1),
        StepInfo.Status("done")
      )
    )
    assertEquals(bare.size, RunsView.StepColumns.size)
    assertEquals(bare.filter(_._2.value.isEmpty).map(_._1), List("model", "note"))
    // #992: the model the provider reported beside the configured one; a dry run's skipped write
    val eval = RunsView.stepCells(
      StepInfo(
        StepInfo.Seq(3),
        StepInfo.Name("patch"),
        StepInfo.Kind("code"),
        StepInfo.Attempt(1),
        StepInfo.Status("done"),
        model = Some(StepInfo.Model("claude-code:haiku")),
        actualModel = Some(StepInfo.ActualModel("claude-haiku-5-5")),
        wouldWrite = List(WouldWrite(WouldWrite.What("patch [[x]]"), WouldWrite.Payload("{}")))
      )
    )
    assertEquals(eval.toMap.get("model"), Some("claude-code:haiku → claude-haiku-5-5"))
    assertEquals(eval.toMap.get("note"), Some("dry run: not written: patch [[x]]"))
