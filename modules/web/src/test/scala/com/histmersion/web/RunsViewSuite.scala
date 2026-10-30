package com.histmersion.web

import com.histmersion.api.Agents.*

/** The pure parts of the agent runs and approvals windows: what is live, the poll schedule, the filter, the actions a
  * state allows, why a run paused, the typed budget, an approval in words, and the state filters' round trip through
  * the URL hash.
  */
class RunsViewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash("")

  private def run(status: String, error: Option[String] = None) =
    RunInfo("r1", "enrich-source", "a2020_b", status, error = error)

  test("only queued and running runs are live; the poll stops once nothing is"):
    assert(RunsView.live("queued") && RunsView.live("running"))
    assert(!RunsView.live("paused") && !RunsView.live("done") && !RunsView.live("nonsense"))
    assertEquals(RunsView.nextPoll(None, anyLive = false, changed = true), None)
    assertEquals(RunsView.nextPoll(None, anyLive = true, changed = true), Some(JobsView.Poll.FirstMs))
    val slower = RunsView.nextPoll(Some(JobsView.Poll.FirstMs), anyLive = true, changed = false).get
    assert(slower > JobsView.Poll.FirstMs)
    assertEquals(
      RunsView.nextPoll(Some(JobsView.Poll.MaxMs), anyLive = true, changed = false),
      Some(JobsView.Poll.MaxMs)
    )

  test("the actions a state allows"):
    import RunsView.Action.*
    assertEquals(RunsView.actions("running"), List(Pause, Cancel))
    assertEquals(RunsView.actions("queued"), List(Pause, Cancel))
    assertEquals(RunsView.actions("paused"), List(Resume, Cancel))
    assertEquals(RunsView.actions("done"), Nil)
    assertEquals(RunsView.actions("cancelled"), Nil)

  test("why a run paused, the filter, and a typed budget"):
    assertEquals(RunsView.pausedFor(run("paused", Some("budget: the run spent 2 of its 2 USD"))), Some("budget"))
    assertEquals(RunsView.pausedFor(run("paused", Some("interrupted"))), Some("interrupted"))
    assertEquals(RunsView.pausedFor(run("done")), None)
    val rs = List(run("done"), run("paused"), run("done"))
    assertEquals(RunsView.visible(rs, Some(RunState.Done)).size, 2)
    assertEquals(RunsView.visible(rs, None).size, 3)
    assertEquals(RunsView.counts(rs), Map("done" -> 2, "paused" -> 1))
    assertEquals(RunsView.budget(" 2.5 "), Some(2.5))
    assertEquals(RunsView.budget("0"), None)
    assertEquals(RunsView.budget("two"), None)

  test("an approval in words, and which ones can still be decided"):
    val status =
      ApprovalInfo(1, None, "status-change", "a2020_b", status = Some("contested"), current = Some("current"))
    val edge = ApprovalInfo(
      2,
      None,
      "edge",
      "a2020_b",
      edgeKind = Some("superseded_by"),
      target = Some("c2024_d"),
      state = "approved"
    )
    assertEquals(ApprovalsView.describe(status), "status of a2020_b: current → contested")
    assertEquals(ApprovalsView.describe(edge), "a2020_b superseded by c2024_d")
    assert(ApprovalsView.isOpen(status) && !ApprovalsView.isOpen(edge))
    // the open ones are the default state (nothing in the URL); every other button names its state
    assertEquals(ApprovalsView.stateValue("open"), None)
    assertEquals(ApprovalsView.stateValue("all"), Some("all"))
    assertEquals(ApprovalsView.shownState(None), "open")
    assertEquals(ApprovalsView.shownState(Some("rejected")), "rejected")

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
    UiState.loadHash("")
    assertEquals(UiState.runStatus.now(), None)
    assertEquals(UiState.approvalKinds.now(), Nil)
    UiState.loadHash(hash)
    assertEquals(UiState.runStatus.now(), Some(RunState.Paused))
    assertEquals(UiState.approvalState.now(), Some("all"))
    assertEquals(UiState.approvalKinds.now(), List("goal-plan", "edge"))
    assertEquals(UiState.runManager.now(), Some("seed:tin"))
    assertEquals(UiState.jobKinds.now(), List("ingest"))
    UiState.loadHash("runs=bogus")
    assertEquals(UiState.runStatus.now(), None)

  test("a run waiting for an approval: only cancel, and which approval it waits on"):
    import RunsView.Action.*
    assertEquals(RunsView.actions("waiting"), List(Cancel))
    assert(!RunsView.live("waiting"), "a waiting run waits for a person, not for the server")
    val w = RunInfo("r2", "plan-goal", "g", "waiting", progress = Some("approve: waiting for approval 42"))
    assertEquals(RunsView.waitingFor(w), Some(42L))
    assertEquals(RunsView.waitingFor(run("done")), None)

  test("a goal plan and an article to publish, in words"):
    val plan = ApprovalInfo(
      3,
      Some("r"),
      "goal-plan",
      "",
      subject = Some("bronze-age"),
      plan = Some(
        com.histmersion.api.GoalPlans.Plan(
          "bronze-age",
          queries = List("tin"),
          subGoals = List(com.histmersion.api.GoalPlans.PlannedGoal("tin", "Tin"))
        )
      )
    )
    assertEquals(ApprovalsView.describe(plan), "plan for goal bronze-age: 1 sub-goal, 1 query, 0 works")
    val draft = com.histmersion.api.GoalPlans.ArticleDraft(
      "tin",
      com.histmersion.api.GoalPlans.DraftMode.Create,
      "Tin",
      "concept",
      "x"
    )
    val pub = ApprovalInfo(4, Some("r"), "article-publish", "", subject = Some("tin"), article = Some(draft))
    assertEquals(ApprovalsView.describe(pub), "publish the new article tin: Tin")
    assertEquals(
      ApprovalsView.describe(
        pub.copy(article = Some(draft.copy(mode = com.histmersion.api.GoalPlans.DraftMode.Extend)))
      ),
      "append a section to the article tin"
    )
    assertEquals(ApprovalsView.selfCheck(draft), "The code self-check found nothing.")
    assertEquals(
      ApprovalsView.selfCheck(draft.copy(problems = List("a", "b"))),
      "The code self-check still found 2 problems after the writer's last attempt:"
    )

  test("a step's cells name their columns, so a narrow card can label them (#887)"):
    val s = StepInfo(
      3,
      "draft",
      "llm",
      2,
      "failed",
      model = Some("anthropic:claude"),
      usage = Usage(120, 45),
      costUsd = 0.25,
      error = Some("provider: overloaded")
    )
    val cells = RunsView.stepCells(s)
    assertEquals(cells.map(_._1), RunsView.StepColumns)
    assertEquals(cells.toMap.get("step"), Some("draft"))
    assertEquals(cells.toMap.get("tokens"), Some("120/45"))
    assertEquals(cells.toMap.get("note"), Some("provider: overloaded"))
    // nothing to say is an empty cell (left out of a card), never a missing column
    val bare = RunsView.stepCells(StepInfo(1, "plan", "tool", 1, "done"))
    assertEquals(bare.size, RunsView.StepColumns.size)
    assertEquals(bare.filter(_._2.isEmpty).map(_._1), List("model", "note"))
    // #992: the model the provider reported beside the configured one; a dry run's skipped write
    val eval = RunsView.stepCells(
      StepInfo(
        3,
        "patch",
        "code",
        1,
        "done",
        model = Some("claude-code:haiku"),
        actualModel = Some("claude-haiku-5-5"),
        wouldWrite = List(WouldWrite("patch [[x]]", "{}"))
      )
    )
    assertEquals(eval.toMap.get("model"), Some("claude-code:haiku → claude-haiku-5-5"))
    assertEquals(eval.toMap.get("note"), Some("dry run: not written: patch [[x]]"))
