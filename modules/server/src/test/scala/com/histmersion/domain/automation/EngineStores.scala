package com.histmersion.domain.automation

import com.histmersion.domain.automation.agents.{ApprovalService, RunService}
import com.histmersion.domain.automation.autopilot.AutopilotRowService
import com.histmersion.util.jobs.{JobRuns, RunRow}
import com.histmersion.domain.automation.goals.{FocusService, GoalService}
import com.histmersion.domain.automation.managers.ManagerService
import com.augustnagro.magnum.DbCon

/** The engine features' services over one store — the database's scratch copy or the shared in-memory state — and how a
  * contract makes the rows of other domains a feature reads (an article, a source) and runs a service's `…In` operation
  * in a transaction of its own.
  */
trait EngineStores:
  def sourceApprovals: com.histmersion.domain.library.workflows.SourceApprovalService
  def autopilot: AutopilotRowService
  def goals: GoalService
  def approvals: ApprovalService
  def runs: JobRuns
  def agentRuns: RunService
  def managers: ManagerService
  def focus: FocusService
  def agentState: com.histmersion.domain.automation.agents.AgentStateService
  def article(slug: com.histmersion.domain.research.Slug, title: com.histmersion.domain.Title): Unit
  def source(citekey: com.histmersion.domain.library.Citekey, title: com.histmersion.domain.Title): Unit
  def within[A](work: DbCon ?=> A): A

  /** A new queued agent run of `pipeline` on `target` (for `goal`, by `manager`'s `item`); its id. */
  def run(
      target: RunRow.Target,
      goal: Option[RunRow.Goal] = None,
      manager: Option[RunRow.ManagerId] = None,
      item: Option[RunRow.ManagerItem] = None,
      pipeline: RunRow.Pipeline = RunRow.Pipeline("enrich-source")
  ): Run.Id =
    runs.create(
      RunRow(
        com.histmersion.util.jobs.RunRow.Kind("agent"),
        com.histmersion.util.jobs.RunRow.Status("queued"),
        pipeline,
        com.histmersion.util.jobs.RunRow.PipelineVersion(1),
        com.histmersion.util.jobs.RunRow.TargetKind("source"),
        target,
        None,
        Some(com.histmersion.util.jobs.RunRow.Model("local:scripted")),
        BigDecimal(1),
        com.histmersion.util.jobs.RunRow.BudgetTokens(1000L),
        com.histmersion.util.jobs.RunRow.Params("{}"),
        None,
        goal,
        None,
        com.histmersion.util.jobs.RunRow.Origin("person"),
        com.histmersion.util.jobs.RunRow.DryRun(false),
        manager,
        item
      )
    )
