package com.histmersion.web

import com.histmersion.api.{UiLabels, Wording}
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.automation.ListFilters
import com.histmersion.api.automation.Agents.{ApprovalInfo, Decision, RunInfo, RunState}

/** The pure halves of the list windows' filters (ListFilterBar, and the approvals, runs and jobs windows): the chips, a
  * typed or picked value, the query each window sends, the rows a filter shows before the server's page lands, and the
  * bulk decision's targets and confirmation.
  */
class ListFiltersViewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash(com.histmersion.web.UiState.UrlHash(""))

  test("chips: the server's counts in its order, then a picked value it did not count, at 0"):
    val counts = List("goal-plan" -> 64, "article-publish" -> 30, "edge" -> 3)
    assertEquals(ListFilterBar.chipItems(counts, List("edge")), counts)
    assertEquals(ListFilterBar.chipItems(counts, List("hint-effect")), counts :+ ("hint-effect" -> 0))
    assertEquals(ListFilterBar.chipItems(Nil, Nil), Nil)
    assertEquals(ListFilterBar.toggled(List("edge"), "goal-plan"), List("edge", "goal-plan"))
    assertEquals(ListFilterBar.toggled(List("edge", "goal-plan"), "edge"), List("goal-plan"))
    assertEquals(ListFilterBar.optionLabel("review-article", 1223), "Review an article (1,223)")
    assertEquals(ListFilterBar.optionLabel("seed:x", 0, id => Wording.manager(Wording.ManagerId(id))), "Seeding manager · X")
    assertEquals(ListFilterBar.optionLabel("some-new-kind", 2), "Some new kind (2)", "an identifier never reads raw")
    assertEquals(
      ListFilterBar.tooltip("edge", id => UiLabels.label(UiLabels.Key(id)).value, _ => Some("A typed link.")),
      Some("A typed link. (edge)")
    )
    assertEquals(ListFilterBar.tooltip("x", identity, _ => None), None)

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
    assert(!UiState.hashString.value.contains("apprKind"), UiState.hashString)

  private def appr(
      id: ApprovalInfo.Id,
      kind: ApprovalInfo.Kind,
      state: ApprovalInfo.State = ApprovalInfo.State("open"),
      note: Option[ApprovalInfo.Note] = None,
      result: Option[ApprovalInfo.Result] = None
  ) =
    ApprovalInfo(id, None, kind, com.histmersion.api.library.Citekey(""), state = state, note = note, result = result)

  test("approvals: the query from the window's fields; the rows a state or kind shows at once"):
    UiState.set(UiState.approvalKinds := List("goal-plan"), UiState.approvalBy := List("person"))()
    assertEquals(
      ApprovalsView.currentFilter(),
      ListFilters.ApprovalFilter(kind = List(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")), decidedBy = List(com.histmersion.api.automation.Approval.DecidedBy.Wire("person")))
    )
    val rows = List(appr(ApprovalInfo.Id(1), ApprovalInfo.Kind("goal-plan")), appr(ApprovalInfo.Id(2), ApprovalInfo.Kind("edge")), appr(ApprovalInfo.Id(3), ApprovalInfo.Kind("goal-plan"), ApprovalInfo.State("approved")))
    assertEquals(ApprovalsView.visible(rows, ListFilters.ApprovalFilter(kind = List(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")))).map(_.id.value), List(1L))
    assertEquals(
      ApprovalsView.visible(rows, ListFilters.ApprovalFilter(state = Some(com.histmersion.api.automation.Approval.State.Wire("all")), kind = List(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")))).map(_.id.value),
      List(1L, 3L)
    )
    assertEquals(ApprovalsView.visible(rows, ListFilters.ApprovalFilter(state = Some(com.histmersion.api.automation.Approval.State.Wire("approved")))).map(_.id.value), List(3L))
    assertEquals(ApprovalsView.qualifier(ListFilters.ApprovalFilter(kind = List(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")))).map(_.value), Some("open goal-plan"))

  test("approvals: a bulk decision applies to the ticked rows still shown and open, and says what it will do"):
    val rows = List(appr(ApprovalInfo.Id(1), ApprovalInfo.Kind("goal-plan")), appr(ApprovalInfo.Id(2), ApprovalInfo.Kind("goal-plan")), appr(ApprovalInfo.Id(3), ApprovalInfo.Kind("edge")), appr(ApprovalInfo.Id(4), ApprovalInfo.Kind("edge"), ApprovalInfo.State("approved")))
    assertEquals(ApprovalsView.bulkTargets(Set(com.histmersion.api.automation.Agents.ApprovalInfo.Id(1), com.histmersion.api.automation.Agents.ApprovalInfo.Id(3), com.histmersion.api.automation.Agents.ApprovalInfo.Id(4), com.histmersion.api.automation.Agents.ApprovalInfo.Id(9)), rows).map(_.id.value), List(1L, 3L))
    val text = ApprovalsView.confirmText(Decision.Approve, rows.take(3))
    assert(text.startsWith("Approve 3 approvals (2 plan a goal, 1 link two sources)?"), text)
    assert(ApprovalsView.confirmText(Decision.Reject, rows.take(1)).startsWith("Reject 1 approval (1 plan a goal)?"))

  test("approvals: who decided, as the row says it"):
    assertEquals(ApprovalsView.decidedLine(appr(ApprovalInfo.Id(1), ApprovalInfo.Kind("goal-plan"))), None)
    assertEquals(
      ApprovalsView.decidedLine(appr(ApprovalInfo.Id(1), ApprovalInfo.Kind("goal-plan"), ApprovalInfo.State("approved"), note = Some(ApprovalInfo.Note("autopilot: approved — ok")))),
      Some(ListFilters.DecidedBy.Autopilot.describe.value)
    )
    assertEquals(
      ApprovalsView.decidedLine(appr(ApprovalInfo.Id(1), ApprovalInfo.Kind("edge"), ApprovalInfo.State("approved"), result = Some(ApprovalInfo.Result("applied automatically: x")))),
      Some(ListFilters.DecidedBy.Auto.describe.value)
    )

  private def run(
      id: RunInfo.Id,
      status: RunInfo.Status,
      pipeline: RunInfo.Pipeline,
      origin: Option[RunInfo.Origin] = None,
      manager: Option[com.histmersion.api.automation.Managers.ManagerInfo.Id] = None
  ) =
    RunInfo(id, pipeline, com.histmersion.api.automation.Agents.RunInfo.Target("t"), status, origin = origin, managerId = manager)

  test("runs: the query from the fields; the rows the state, pipelines, origins and manager show at once"):
    val f = RunsView.filterOf(
      Some(RunState.Failed),
      List(com.histmersion.api.automation.ListFilters.RunFilter.Pipeline("review-article")),
      Nil,
      Some(com.histmersion.api.automation.ListFilters.RunFilter.Manager("review")),
      Some(com.histmersion.api.automation.ListFilters.RunFilter.Provider("codex")),
      None,
      Some(com.histmersion.api.automation.Goal.Slug("g")),
      Some(com.histmersion.api.automation.ListFilters.RunFilter.Since("2026-10-01")),
      None,
      Some(com.histmersion.api.automation.ListFilters.RunFilter.Subject("tin"))
    )
    assertEquals(
      f,
      ListFilters.RunFilter(
        List(com.histmersion.api.automation.ListFilters.RunFilter.Status("failed")),
        List(com.histmersion.api.automation.ListFilters.RunFilter.Pipeline("review-article")),
        List(com.histmersion.api.automation.ListFilters.RunFilter.Manager("review")),
        Some(com.histmersion.api.automation.Goal.Slug("g")),
        Nil,
        List(com.histmersion.api.automation.ListFilters.RunFilter.Provider("codex")),
        Nil,
        Some(com.histmersion.api.automation.ListFilters.RunFilter.Since("2026-10-01")),
        None,
        Some(com.histmersion.api.automation.ListFilters.RunFilter.Subject("tin"))
      )
    )
    val rows = List(
      run(RunInfo.Id("a"), RunInfo.Status("failed"), RunInfo.Pipeline("review-article"), Some(RunInfo.Origin("manager")), Some(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"))),
      run(RunInfo.Id("b"), RunInfo.Status("done"), RunInfo.Pipeline("review-article")),
      run(RunInfo.Id("c"), RunInfo.Status("failed"), RunInfo.Pipeline("enrich-source"))
    )
    assertEquals(
      RunsView.visible(rows, ListFilters.RunFilter(pipeline = List(com.histmersion.api.automation.ListFilters.RunFilter.Pipeline("review-article")))).map(_.id.value),
      List("a", "b")
    )
    assertEquals(
      RunsView.visible(rows, ListFilters.RunFilter(status = List(com.histmersion.api.automation.ListFilters.RunFilter.Status("failed")), manager = List(com.histmersion.api.automation.ListFilters.RunFilter.Manager("review")))).map(_.id.value),
      List("a")
    )
    // a run with no origin was a person's
    assertEquals(RunsView.visible(rows, ListFilters.RunFilter(origin = List(com.histmersion.api.automation.Agents.Origin.Wire("person")))).map(_.id.value), List("b", "c"))

  test("jobs: the query from the fields; the rows the states and kinds show at once"):
    assertEquals(
      JobsView.filterOf(Some(JobState.Failed), List("ingest"), None, Some("2026-10-01"), None),
      ListFilters.JobFilter(List(com.histmersion.api.jobs.JobState.Wire("failed")), List(com.histmersion.api.jobs.JobKinds.Wire("ingest")), None, Some(com.histmersion.api.automation.ListFilters.JobFilter.Until("2026-10-01")), None)
    )
    val rows = List(JobInfo(com.histmersion.api.SpatialHierarchyResult.Job.Id("1"), com.histmersion.api.jobs.JobKinds.Wire("ingest"), com.histmersion.api.jobs.JobInfo.Status("done")), JobInfo(com.histmersion.api.SpatialHierarchyResult.Job.Id("2"), com.histmersion.api.jobs.JobKinds.Wire("reocr"), com.histmersion.api.jobs.JobInfo.Status("failed")), JobInfo(com.histmersion.api.SpatialHierarchyResult.Job.Id("3"), com.histmersion.api.jobs.JobKinds.Wire("ingest"), com.histmersion.api.jobs.JobInfo.Status("failed")))
    assertEquals(JobsView.visible(rows, ListFilters.JobFilter(kind = List(com.histmersion.api.jobs.JobKinds.Wire("ingest")))).map(_.id), List("1", "3").map(com.histmersion.api.SpatialHierarchyResult.Job.Id.apply))
    assertEquals(
      JobsView.visible(rows, ListFilters.JobFilter(status = List(com.histmersion.api.jobs.JobState.Wire("failed")), kind = List(com.histmersion.api.jobs.JobKinds.Wire("ingest")))).map(_.id),
      List("3").map(com.histmersion.api.SpatialHierarchyResult.Job.Id.apply)
    )
