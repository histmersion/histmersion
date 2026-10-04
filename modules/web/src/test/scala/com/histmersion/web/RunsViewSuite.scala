package com.histmersion.web

import com.histmersion.core.Agents.*

/** The pure parts of the agent runs and approvals windows: what is live, the poll schedule, the
  * filter, the actions a state allows, why a run paused, the typed budget, an approval in words, and
  * the state filters' round trip through the URL hash. */
class RunsViewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash("")

  private def run(status: String, error: Option[String] = None) = RunInfo("r1", "enrich-source", "a2020_b", status, error = error)

  test("only queued and running runs are live; the poll stops once nothing is"):
    assert(RunsView.live("queued") && RunsView.live("running"))
    assert(!RunsView.live("paused") && !RunsView.live("done") && !RunsView.live("nonsense"))
    assertEquals(RunsView.nextPoll(None, anyLive = false, changed = true), None)
    assertEquals(RunsView.nextPoll(None, anyLive = true, changed = true), Some(JobsView.Poll.FirstMs))
    val slower = RunsView.nextPoll(Some(JobsView.Poll.FirstMs), anyLive = true, changed = false).get
    assert(slower > JobsView.Poll.FirstMs)
    assertEquals(RunsView.nextPoll(Some(JobsView.Poll.MaxMs), anyLive = true, changed = false), Some(JobsView.Poll.MaxMs))

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
    val status = ApprovalInfo(1, None, "status-change", "a2020_b", status = Some("contested"), current = Some("current"))
    val edge   = ApprovalInfo(2, None, "edge", "a2020_b", edgeKind = Some("superseded_by"), target = Some("c2024_d"),
      state = "approved")
    assertEquals(ApprovalsView.describe(status), "status of a2020_b: current → contested")
    assertEquals(ApprovalsView.describe(edge), "a2020_b superseded by c2024_d")
    assert(ApprovalsView.isOpen(status) && !ApprovalsView.isOpen(edge))
    assertEquals(ApprovalsView.stateQuery(all = true), Some("all"))
    assertEquals(ApprovalsView.stateQuery(all = false), None)

  test("the runs filter and the approvals toggle live in the URL hash"):
    UiState.set(UiState.runStatus := Some(RunState.Paused), UiState.approvalsAll := true)()
    val hash = UiState.hashString
    assert(hash.contains("runs=paused") && hash.contains("apprAll=true"), hash)
    UiState.loadHash("")
    assertEquals(UiState.runStatus.now(), None)
    UiState.loadHash(hash)
    assertEquals(UiState.runStatus.now(), Some(RunState.Paused))
    assert(UiState.approvalsAll.now())
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
    val plan = ApprovalInfo(3, Some("r"), "goal-plan", "", subject = Some("bronze-age"),
      plan = Some(com.histmersion.core.GoalPlans.Plan("bronze-age", queries = List("tin"),
        subGoals = List(com.histmersion.core.GoalPlans.PlannedGoal("tin", "Tin")))))
    assertEquals(ApprovalsView.describe(plan), "plan for goal bronze-age: 1 sub-goal, 1 query, 0 works")
    val draft = com.histmersion.core.GoalPlans.ArticleDraft("tin", com.histmersion.core.GoalPlans.DraftMode.Create, "Tin", "concept", "x")
    val pub = ApprovalInfo(4, Some("r"), "article-publish", "", subject = Some("tin"), article = Some(draft))
    assertEquals(ApprovalsView.describe(pub), "publish the new article tin: Tin")
    assertEquals(ApprovalsView.describe(pub.copy(article = Some(draft.copy(mode = com.histmersion.core.GoalPlans.DraftMode.Extend)))),
      "append a section to the article tin")
    assertEquals(ApprovalsView.selfCheck(draft), "The code self-check found nothing.")
    assertEquals(ApprovalsView.selfCheck(draft.copy(problems = List("a", "b"))),
      "The code self-check still found 2 problems after the writer's last attempt:")
