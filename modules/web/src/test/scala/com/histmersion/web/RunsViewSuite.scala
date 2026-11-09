package com.histmersion
package web

import com.histmersion.api.Problem
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

/** The pure parts of the agent runs and approvals windows: what is live, the poll schedule, the filter, the actions a
  * state allows, why a run paused, the typed budget, an approval in words, and the state filters' round trip through
  * the URL hash.
  */
class RunsViewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash(UiState.UrlHash(""))

  private def run(
      status: RunInfo.Status,
      error: Option[Problem] = None,
      pausedFor: Option[Agents.PauseReason] = None
  ) =
    RunInfo(
      Agents.RunInfo.Id("r1"),
      Agents.RunInfo.Pipeline("enrich-source"),
      Agents.RunInfo.Target("a2020_b"),
      status,
      error = error,
      pausedFor = pausedFor
    )

  test("only queued and running runs are live; the poll stops once nothing is"):
    assert(RunsView.live(Agents.RunInfo.Status("queued")) && RunsView.live(Agents.RunInfo.Status("running")))
    assert(
      !RunsView.live(Agents.RunInfo.Status("paused")) && !RunsView.live(Agents.RunInfo.Status("done")) && !RunsView
        .live(Agents.RunInfo.Status("nonsense"))
    )
    assertEquals(RunsView.nextPoll(None, anyLive = Poll.LiveMode(false), changed = Poll.DataChanged(true)), None)
    assertEquals(
      RunsView.nextPoll(None, anyLive = Poll.LiveMode(true), changed = Poll.DataChanged(true)),
      Some(JobsView.Poll.FirstMs)
    )
    val slower = RunsView
      .nextPoll(
        Some(Poll.Revision(JobsView.Poll.FirstMs)),
        anyLive = Poll.LiveMode(true),
        changed = Poll.DataChanged(false)
      )
      .get
    assert(slower.value > JobsView.Poll.FirstMs)
    assertEquals(
      RunsView.nextPoll(
        Some(Poll.Revision(JobsView.Poll.MaxMs)),
        anyLive = Poll.LiveMode(true),
        changed = Poll.DataChanged(false)
      ),
      Some(JobsView.Poll.MaxMs)
    )

  test("the actions a state allows"):
    import RunsView.Action.*
    assertEquals(RunsView.actions(Agents.RunInfo.Status("running")), List(Pause, Cancel))
    assertEquals(RunsView.actions(Agents.RunInfo.Status("queued")), List(Pause, Cancel))
    assertEquals(RunsView.actions(Agents.RunInfo.Status("paused")), List(Resume, Cancel))
    assertEquals(RunsView.actions(Agents.RunInfo.Status("done")), Nil)
    assertEquals(RunsView.actions(Agents.RunInfo.Status("cancelled")), Nil)

  test("why a run paused, the filter, and a typed budget"):
    assertEquals(
      RunsView.pausedFor(
        run(
          RunInfo.Status("paused"),
          Some(Problem("budget: the run spent 2 of its 2 USD")),
          Some(Agents.PauseReason.Budget)
        )
      ),
      Some(Agents.PauseReason.Budget)
    )
    assertEquals(
      RunsView.pausedFor(
        run(RunInfo.Status("paused"), Some(Problem("interrupted")), Some(Agents.PauseReason.Interrupted))
      ),
      Some(Agents.PauseReason.Interrupted)
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
        Agents.ApprovalInfo.Id(1),
        None,
        Agents.ApprovalInfo.Kind("status-change"),
        Citekey("a2020_b"),
        status = Some(Agents.ApprovalInfo.Status("contested")),
        current = Some(Agents.ApprovalInfo.Current("current"))
      )
    val edge = ApprovalInfo(
      Agents.ApprovalInfo.Id(2),
      None,
      Agents.ApprovalInfo.Kind("edge"),
      Citekey("a2020_b"),
      edgeKind = Some(Edge.Kind.Wire("superseded_by")),
      target = Some(Agents.ApprovalInfo.Target("c2024_d")),
      state = Agents.ApprovalInfo.State("approved")
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
    assertEquals(RunsView.actions(Agents.RunInfo.Status("waiting")), List(Cancel))
    assert(!RunsView.live(Agents.RunInfo.Status("waiting")), "a waiting run waits for a person, not for the server")
    val w = RunInfo(
      Agents.RunInfo.Id("r2"),
      Agents.RunInfo.Pipeline("plan-goal"),
      Agents.RunInfo.Target("g"),
      Agents.RunInfo.Status("waiting"),
      progress = Some(Agents.RunInfo.Progress("approve: waiting for approval 42"))
    )
    assertEquals(RunsView.waitingFor(w), Some(42L))
    assertEquals(RunsView.waitingFor(run(RunInfo.Status("done"))), None)

  test("a goal plan and an article to publish, in words"):
    val plan = ApprovalInfo(
      Agents.ApprovalInfo.Id(3),
      Some(Agents.RunInfo.Id("r")),
      Agents.ApprovalInfo.Kind("goal-plan"),
      Citekey(""),
      subject = Some(Agents.ApprovalInfo.Subject("bronze-age")),
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
      Agents.ApprovalInfo.Id(4),
      Some(Agents.RunInfo.Id("r")),
      Agents.ApprovalInfo.Kind("article-publish"),
      Citekey(""),
      subject = Some(Agents.ApprovalInfo.Subject("tin")),
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
      Agents.StepInfo.Seq(3),
      Agents.StepInfo.Name("draft"),
      Agents.StepInfo.Kind("llm"),
      Agents.StepInfo.Attempt(2),
      Agents.StepInfo.Status("failed"),
      model = Some(Agents.StepInfo.Model("anthropic:claude")),
      usage = Usage(Agents.Usage.Input(120), Agents.Usage.Output(45)),
      costUsd = Agents.StepInfo.CostUsd(0.25),
      error = Some(Problem("provider: overloaded"))
    )
    val cells = RunsView.stepCells(s)
    assertEquals(cells.map(_._1), RunsView.StepColumns)
    assertEquals(cells.toMap.get("step"), Some("draft"))
    assertEquals(cells.toMap.get("tokens"), Some("120/45"))
    assertEquals(cells.toMap.get("note"), Some("provider: overloaded"))
    // nothing to say is an empty cell (left out of a card), never a missing column
    val bare = RunsView.stepCells(
      StepInfo(
        Agents.StepInfo.Seq(1),
        Agents.StepInfo.Name("plan"),
        Agents.StepInfo.Kind("tool"),
        Agents.StepInfo.Attempt(1),
        Agents.StepInfo.Status("done")
      )
    )
    assertEquals(bare.size, RunsView.StepColumns.size)
    assertEquals(bare.filter(_._2.value.isEmpty).map(_._1), List("model", "note"))
    // #992: the model the provider reported beside the configured one; a dry run's skipped write
    val eval = RunsView.stepCells(
      StepInfo(
        Agents.StepInfo.Seq(3),
        Agents.StepInfo.Name("patch"),
        Agents.StepInfo.Kind("code"),
        Agents.StepInfo.Attempt(1),
        Agents.StepInfo.Status("done"),
        model = Some(Agents.StepInfo.Model("claude-code:haiku")),
        actualModel = Some(Agents.StepInfo.ActualModel("claude-haiku-5-5")),
        wouldWrite = List(WouldWrite(Agents.WouldWrite.What("patch [[x]]"), Agents.WouldWrite.Payload("{}")))
      )
    )
    assertEquals(eval.toMap.get("model"), Some("claude-code:haiku → claude-haiku-5-5"))
    assertEquals(eval.toMap.get("note"), Some("dry run: not written: patch [[x]]"))
