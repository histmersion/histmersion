package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.*

/** The pure parts of the agent runs and approvals windows: what is live, the poll schedule, the filter, the actions a
  * state allows, why a run paused, the typed budget, an approval in words, and the state filters' round trip through
  * the URL hash.
  */
class RunsViewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash(com.histmersion.web.UiState.UrlHash(""))

  private def run(status: String, error: Option[String] = None) =
    RunInfo(com.histmersion.api.automation.Agents.RunInfo.Id("r1"), com.histmersion.api.automation.Agents.RunInfo.Pipeline("enrich-source"), com.histmersion.api.automation.Agents.RunInfo.Target("a2020_b"), status, error = error)

  test("only queued and running runs are live; the poll stops once nothing is"):
    assert(RunsView.live(com.histmersion.api.automation.Agents.RunInfo.Status("queued")) && RunsView.live(com.histmersion.api.automation.Agents.RunInfo.Status("running")))
    assert(!RunsView.live(com.histmersion.api.automation.Agents.RunInfo.Status("paused")) && !RunsView.live(com.histmersion.api.automation.Agents.RunInfo.Status("done")) && !RunsView.live(com.histmersion.api.automation.Agents.RunInfo.Status("nonsense")))
    assertEquals(RunsView.nextPoll(None, anyLive = com.histmersion.web.Poll.LiveMode(false), changed = com.histmersion.web.Poll.DataChanged(true)), None)
    assertEquals(RunsView.nextPoll(None, anyLive = com.histmersion.web.Poll.LiveMode(true), changed = com.histmersion.web.Poll.DataChanged(true)), Some(JobsView.Poll.FirstMs))
    val slower = RunsView.nextPoll(Some(JobsView.Poll.FirstMs), anyLive = com.histmersion.web.Poll.LiveMode(true), changed = com.histmersion.web.Poll.DataChanged(false)).get
    assert(slower > JobsView.Poll.FirstMs)
    assertEquals(
      RunsView.nextPoll(Some(JobsView.Poll.MaxMs), anyLive = com.histmersion.web.Poll.LiveMode(true), changed = com.histmersion.web.Poll.DataChanged(false)),
      Some(JobsView.Poll.MaxMs)
    )

  test("the actions a state allows"):
    import RunsView.Action.*
    assertEquals(RunsView.actions(com.histmersion.api.automation.Agents.RunInfo.Status("running")), List(Pause, Cancel))
    assertEquals(RunsView.actions(com.histmersion.api.automation.Agents.RunInfo.Status("queued")), List(Pause, Cancel))
    assertEquals(RunsView.actions(com.histmersion.api.automation.Agents.RunInfo.Status("paused")), List(Resume, Cancel))
    assertEquals(RunsView.actions(com.histmersion.api.automation.Agents.RunInfo.Status("done")), Nil)
    assertEquals(RunsView.actions(com.histmersion.api.automation.Agents.RunInfo.Status("cancelled")), Nil)

  test("why a run paused, the filter, and a typed budget"):
    assertEquals(RunsView.pausedFor(run("paused", Some("budget: the run spent 2 of its 2 USD"))), Some("budget"))
    assertEquals(RunsView.pausedFor(run("paused", Some("interrupted"))), Some("interrupted"))
    assertEquals(RunsView.pausedFor(run("done")), None)
    val rs = List(run("done"), run("paused"), run("done"))
    assertEquals(RunsView.visible(rs, Some(RunState.Done)).size, 2)
    assertEquals(RunsView.visible(rs, None).size, 3)
    assertEquals(RunsView.counts(rs), Map("done" -> 2, "paused" -> 1))
    assertEquals(RunsView.budget(com.histmersion.api.UiLabels.Text(" 2.5 ")), Some(2.5))
    assertEquals(RunsView.budget(com.histmersion.api.UiLabels.Text("0")), None)
    assertEquals(RunsView.budget(com.histmersion.api.UiLabels.Text("two")), None)

  test("an approval in words, and which ones can still be decided"):
    val status =
      ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(1), None, com.histmersion.api.automation.Agents.ApprovalInfo.Kind("status-change"), com.histmersion.api.library.Citekey("a2020_b"), status = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Status("contested")), current = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Current("current")))
    val edge = ApprovalInfo(
      com.histmersion.api.automation.Agents.ApprovalInfo.Id(2),
      None,
      com.histmersion.api.automation.Agents.ApprovalInfo.Kind("edge"),
      com.histmersion.api.library.Citekey("a2020_b"),
      edgeKind = Some(com.histmersion.api.library.Edge.Kind.Wire("superseded_by")),
      target = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Target("c2024_d")),
      state = "approved"
    )
    assertEquals(ApprovalsView.describe(status), "status of a2020_b: current → contested")
    assertEquals(ApprovalsView.describe(edge), "a2020_b superseded by c2024_d")
    assert(ApprovalsView.isOpen(status) && !ApprovalsView.isOpen(edge))
    // the open ones are the default state (nothing in the URL); every other button names its state
    assertEquals(ApprovalsView.stateValue(com.histmersion.api.automation.Approval.State.Wire("open")), None)
    assertEquals(ApprovalsView.stateValue(com.histmersion.api.automation.Approval.State.Wire("all")), Some("all"))
    assertEquals(ApprovalsView.shownState(None), "open")
    assertEquals(ApprovalsView.shownState(Some(com.histmersion.api.automation.Approval.State.Wire("rejected"))), "rejected")

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
      hash.contains("runs=paused") && hash.contains("apprState=all") && hash.contains("apprKind=goal-plan") &&
        hash.contains("apprKind=edge") && hash.contains("runPipeline=review-article") && hash.contains(
          "runSince=2026-10-01"
        ) &&
        hash.contains("jobKind=ingest") && hash.contains("jobSubject=a2020"),
      hash
    )
    UiState.loadHash(com.histmersion.web.UiState.UrlHash(""))
    assertEquals(UiState.runStatus.now(), None)
    assertEquals(UiState.approvalKinds.now(), Nil)
    UiState.loadHash(hash)
    assertEquals(UiState.runStatus.now(), Some(RunState.Paused))
    assertEquals(UiState.approvalState.now(), Some("all"))
    assertEquals(UiState.approvalKinds.now(), List("goal-plan", "edge"))
    assertEquals(UiState.runManager.now(), Some("seed:tin"))
    assertEquals(UiState.jobKinds.now(), List("ingest"))
    UiState.loadHash(com.histmersion.web.UiState.UrlHash("runs=bogus"))
    assertEquals(UiState.runStatus.now(), None)

  test("a run waiting for an approval: only cancel, and which approval it waits on"):
    import RunsView.Action.*
    assertEquals(RunsView.actions(com.histmersion.api.automation.Agents.RunInfo.Status("waiting")), List(Cancel))
    assert(!RunsView.live(com.histmersion.api.automation.Agents.RunInfo.Status("waiting")), "a waiting run waits for a person, not for the server")
    val w = RunInfo(com.histmersion.api.automation.Agents.RunInfo.Id("r2"), com.histmersion.api.automation.Agents.RunInfo.Pipeline("plan-goal"), com.histmersion.api.automation.Agents.RunInfo.Target("g"), com.histmersion.api.automation.Agents.RunInfo.Status("waiting"), progress = Some(com.histmersion.api.automation.Agents.RunInfo.Progress("approve: waiting for approval 42")))
    assertEquals(RunsView.waitingFor(w), Some(42L))
    assertEquals(RunsView.waitingFor(run("done")), None)

  test("a goal plan and an article to publish, in words"):
    val plan = ApprovalInfo(
      com.histmersion.api.automation.Agents.ApprovalInfo.Id(3),
      Some(com.histmersion.api.automation.Agents.RunInfo.Id("r")),
      com.histmersion.api.automation.Agents.ApprovalInfo.Kind("goal-plan"),
      com.histmersion.api.library.Citekey(""),
      subject = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Subject("bronze-age")),
      plan = Some(
        com.histmersion.api.automation.Goal.Plans.Plan(
          com.histmersion.api.automation.Goal.Slug("bronze-age"),
          queries = List("tin"),
          subGoals = List(com.histmersion.api.automation.Goal.Plans.PlannedGoal(com.histmersion.api.automation.Goal.Slug("tin"), com.histmersion.api.automation.Goal.Title("Tin")))
        )
      )
    )
    assertEquals(ApprovalsView.describe(plan), "plan for goal bronze-age: 1 sub-goal, 1 query, 0 works")
    val draft = com.histmersion.api.automation.Goal.Plans.ArticleDraft(
      com.histmersion.api.research.Article.Slug("tin"),
      com.histmersion.api.automation.Goal.Plans.DraftMode.Create,
      com.histmersion.api.research.Article.Title("Tin"),
      com.histmersion.api.research.Article.Kind.Wire("concept"),
      com.histmersion.api.automation.Goal.Plans.ArticleDraft.Text("x")
    )
    val pub = ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(4), Some(com.histmersion.api.automation.Agents.RunInfo.Id("r")), com.histmersion.api.automation.Agents.ApprovalInfo.Kind("article-publish"), com.histmersion.api.library.Citekey(""), subject = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Subject("tin")), article = Some(draft))
    assertEquals(ApprovalsView.describe(pub), "publish the new article tin: Tin")
    assertEquals(
      ApprovalsView.describe(
        pub.modify(_.article).setTo(Some(draft.copy(mode = com.histmersion.api.automation.Goal.Plans.DraftMode.Extend)))
      ),
      "append a section to the article tin"
    )
    assertEquals(ApprovalsView.selfCheck(draft), "The code self-check found nothing.")
    assertEquals(
      ApprovalsView.selfCheck(draft.copy(problems = List(com.histmersion.api.automation.Goal.Plans.ArticleDraft.Problems("a"), com.histmersion.api.automation.Goal.Plans.ArticleDraft.Problems("b")))),
      "The code self-check still found 2 problems after the writer's last attempt:"
    )

  test("a step's cells name their columns, so a narrow card can label them (#887)"):
    val s = StepInfo(
      com.histmersion.api.automation.Agents.StepInfo.Seq(3),
      com.histmersion.api.automation.Agents.StepInfo.Name("draft"),
      com.histmersion.api.automation.Agents.StepInfo.Kind("llm"),
      com.histmersion.api.automation.Agents.StepInfo.Attempt(2),
      com.histmersion.api.automation.Agents.StepInfo.Status("failed"),
      model = Some(com.histmersion.api.automation.Agents.StepInfo.Model("anthropic:claude")),
      usage = Usage(com.histmersion.api.automation.Agents.Usage.Input(120), com.histmersion.api.automation.Agents.Usage.Output(45)),
      costUsd = 0.25,
      error = Some(com.histmersion.api.automation.Agents.StepInfo.Error("provider: overloaded"))
    )
    val cells = RunsView.stepCells(s)
    assertEquals(cells.map(_._1), RunsView.StepColumns)
    assertEquals(cells.toMap.get("step"), Some("draft"))
    assertEquals(cells.toMap.get("tokens"), Some("120/45"))
    assertEquals(cells.toMap.get("note"), Some("provider: overloaded"))
    // nothing to say is an empty cell (left out of a card), never a missing column
    val bare = RunsView.stepCells(StepInfo(com.histmersion.api.automation.Agents.StepInfo.Seq(1), com.histmersion.api.automation.Agents.StepInfo.Name("plan"), com.histmersion.api.automation.Agents.StepInfo.Kind("tool"), com.histmersion.api.automation.Agents.StepInfo.Attempt(1), com.histmersion.api.automation.Agents.StepInfo.Status("done")))
    assertEquals(bare.size, RunsView.StepColumns.size)
    assertEquals(bare.filter(_._2.isEmpty).map(_._1), List("model", "note"))
    // #992: the model the provider reported beside the configured one; a dry run's skipped write
    val eval = RunsView.stepCells(
      StepInfo(
        com.histmersion.api.automation.Agents.StepInfo.Seq(3),
        com.histmersion.api.automation.Agents.StepInfo.Name("patch"),
        com.histmersion.api.automation.Agents.StepInfo.Kind("code"),
        com.histmersion.api.automation.Agents.StepInfo.Attempt(1),
        com.histmersion.api.automation.Agents.StepInfo.Status("done"),
        model = Some(com.histmersion.api.automation.Agents.StepInfo.Model("claude-code:haiku")),
        actualModel = Some(com.histmersion.api.automation.Agents.StepInfo.ActualModel("claude-haiku-5-5")),
        wouldWrite = List(WouldWrite(com.histmersion.api.automation.Agents.WouldWrite.What("patch [[x]]"), com.histmersion.api.automation.Agents.WouldWrite.Payload("{}")))
      )
    )
    assertEquals(eval.toMap.get("model"), Some("claude-code:haiku → claude-haiku-5-5"))
    assertEquals(eval.toMap.get("note"), Some("dry run: not written: patch [[x]]"))
