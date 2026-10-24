package com.histmersion.web

import com.histmersion.api.{JobInfo, JobState, ListFilters}
import com.histmersion.api.Agents.{ApprovalInfo, Decision, RunInfo, RunState}

/** The pure halves of the list windows' filters (ListFilterBar, and the approvals, runs and jobs
  * windows): the chips, a typed or picked value, the query each window sends, the rows a filter shows
  * before the server's page lands, and the bulk decision's targets and confirmation. */
class ListFiltersViewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash("")

  test("chips: the server's counts in its order, then a picked value it did not count, at 0"):
    val counts = List("goal-plan" -> 64, "article-publish" -> 30, "edge" -> 3)
    assertEquals(ListFilterBar.chipItems(counts, List("edge")), counts)
    assertEquals(ListFilterBar.chipItems(counts, List("hint-effect")), counts :+ ("hint-effect" -> 0))
    assertEquals(ListFilterBar.chipItems(Nil, Nil), Nil)
    assertEquals(ListFilterBar.toggled(List("edge"), "goal-plan"), List("edge", "goal-plan"))
    assertEquals(ListFilterBar.toggled(List("edge", "goal-plan"), "edge"), List("goal-plan"))
    assertEquals(ListFilterBar.optionLabel("review", 1223), "review (1,223)")
    assertEquals(ListFilterBar.optionLabel("seed:x", 0), "seed:x")

  test("a typed text and a picked day commit trimmed, or not at all"):
    assertEquals(ListFilterBar.typed("  tin "), Some("tin"))
    assertEquals(ListFilterBar.typed("   "), None)
    assertEquals(ListFilterBar.date("2026-10-01"), Some("2026-10-01"))
    assertEquals(ListFilterBar.date(""), None)
    assertEquals(ListFilterBar.date("01/10/2026"), None)

  test("'clear filters' is offered while a filter is set, and clears them all in one commit"):
    val fields: List[Field[?]] = List(UiState.approvalState, UiState.approvalKinds, UiState.approvalSubject)
    assert(!ListFilterBar.anySet(fields))
    UiState.set(UiState.approvalKinds := List("edge"), UiState.approvalSubject := Some("tin"))()
    assert(ListFilterBar.anySet(fields))
    ListFilterBar.clear(fields)
    assert(!ListFilterBar.anySet(fields))
    assertEquals(UiState.approvalKinds.now(), Nil)
    assert(!UiState.hashString.contains("apprKind"), UiState.hashString)

  private def appr(id: Long, kind: String, state: String = "open", note: Option[String] = None, result: Option[String] = None) =
    ApprovalInfo(id, None, kind, "", state = state, note = note, result = result)

  test("approvals: the query from the window's fields; the rows a state or kind shows at once"):
    UiState.set(UiState.approvalKinds := List("goal-plan"), UiState.approvalBy := List("person"))()
    assertEquals(ApprovalsView.currentFilter(), ListFilters.ApprovalFilter(kind = List("goal-plan"), decidedBy = List("person")))
    val rows = List(appr(1, "goal-plan"), appr(2, "edge"), appr(3, "goal-plan", "approved"))
    assertEquals(ApprovalsView.visible(rows, ListFilters.ApprovalFilter(kind = List("goal-plan"))).map(_.id), List(1L))
    assertEquals(ApprovalsView.visible(rows, ListFilters.ApprovalFilter(state = Some("all"), kind = List("goal-plan"))).map(_.id), List(1L, 3L))
    assertEquals(ApprovalsView.visible(rows, ListFilters.ApprovalFilter(state = Some("approved"))).map(_.id), List(3L))
    assertEquals(ApprovalsView.qualifier(ListFilters.ApprovalFilter(kind = List("goal-plan"))), Some("open goal-plan"))

  test("approvals: a bulk decision applies to the ticked rows still shown and open, and says what it will do"):
    val rows = List(appr(1, "goal-plan"), appr(2, "goal-plan"), appr(3, "edge"), appr(4, "edge", "approved"))
    assertEquals(ApprovalsView.bulkTargets(Set(1, 3, 4, 9), rows).map(_.id), List(1L, 3L))
    val text = ApprovalsView.confirmText(Decision.Approve, rows.take(3))
    assert(text.startsWith("Approve 3 approvals (2 goal-plan, 1 edge)?"), text)
    assert(ApprovalsView.confirmText(Decision.Reject, rows.take(1)).startsWith("Reject 1 approval (1 goal-plan)?"))

  test("approvals: who decided, as the row says it"):
    assertEquals(ApprovalsView.decidedLine(appr(1, "goal-plan")), None)
    assertEquals(ApprovalsView.decidedLine(appr(1, "goal-plan", "approved", note = Some("autopilot: approved — ok"))),
      Some(ListFilters.DecidedBy.Autopilot.describe))
    assertEquals(ApprovalsView.decidedLine(appr(1, "edge", "approved", result = Some("applied automatically: x"))),
      Some(ListFilters.DecidedBy.Auto.describe))

  private def run(id: String, status: String, pipeline: String, origin: Option[String] = None, manager: Option[String] = None) =
    RunInfo(id, pipeline, "t", status, origin = origin, managerId = manager)

  test("runs: the query from the fields; the rows the state, pipelines, origins and manager show at once"):
    val f = RunsView.filterOf(Some(RunState.Failed), List("review-article"), Nil, Some("review"), Some("codex"), None,
      Some("g"), Some("2026-10-01"), None, Some("tin"))
    assertEquals(f, ListFilters.RunFilter(List("failed"), List("review-article"), List("review"), Some("g"), Nil, List("codex"),
      Nil, Some("2026-10-01"), None, Some("tin")))
    val rows = List(run("a", "failed", "review-article", Some("manager"), Some("review")), run("b", "done", "review-article"),
      run("c", "failed", "enrich-source"))
    assertEquals(RunsView.visible(rows, ListFilters.RunFilter(pipeline = List("review-article"))).map(_.id), List("a", "b"))
    assertEquals(RunsView.visible(rows, ListFilters.RunFilter(status = List("failed"), manager = List("review"))).map(_.id), List("a"))
    // a run with no origin was a person's
    assertEquals(RunsView.visible(rows, ListFilters.RunFilter(origin = List("person"))).map(_.id), List("b", "c"))

  test("jobs: the query from the fields; the rows the states and kinds show at once"):
    assertEquals(JobsView.filterOf(Some(JobState.Failed), List("ingest"), None, Some("2026-10-01"), None),
      ListFilters.JobFilter(List("failed"), List("ingest"), None, Some("2026-10-01"), None))
    val rows = List(JobInfo("1", "ingest", "done"), JobInfo("2", "reocr", "failed"), JobInfo("3", "ingest", "failed"))
    assertEquals(JobsView.visible(rows, ListFilters.JobFilter(kind = List("ingest"))).map(_.id), List("1", "3"))
    assertEquals(JobsView.visible(rows, ListFilters.JobFilter(status = List("failed"), kind = List("ingest"))).map(_.id), List("3"))
