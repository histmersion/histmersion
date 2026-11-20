package com.histmersion
package domain.automation

import com.histmersion.domain.automation.agents.{ApprovalService, RunService}
import com.histmersion.domain.automation.autopilot.AutopilotRowService
import com.histmersion.util.jobs.{JobRuns, RunRow}
import com.histmersion.domain.automation.goals.{FocusService, GoalService}
import com.histmersion.domain.automation.managers.ManagerService
import com.augustnagro.magnum.DbCon
import com.histmersion.domain.library.workflows.SourceApprovalService
import com.histmersion.domain.automation.agents.AgentStateService
import com.histmersion.domain.research.Slug
import com.histmersion.domain.Title
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.TestPermits.given

/** The engine features' services over one store — the database's scratch copy or the shared in-memory state — and how a
  * contract makes the rows of other domains a feature reads (an article, a source) and runs a service's `…In` operation
  * in a transaction of its own.
  */
trait EngineStores:
  def sourceApprovals: SourceApprovalService
  def autopilot: AutopilotRowService
  def goals: GoalService
  def approvals: ApprovalService
  def runs: JobRuns
  def agentRuns: RunService
  def managers: ManagerService
  def focus: FocusService
  def agentState: AgentStateService
  def article(slug: Slug, title: Title): Unit
  def source(citekey: Citekey, title: Title): Unit
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
        RunRow.Kind("agent"),
        RunRow.Status("queued"),
        pipeline,
        RunRow.PipelineVersion(1),
        RunRow.TargetKind("source"),
        target,
        None,
        Some(RunRow.Model("local:scripted")),
        BigDecimal(1),
        RunRow.BudgetTokens(1000L),
        RunRow.Params("{}"),
        None,
        goal,
        None,
        RunRow.Origin("person"),
        RunRow.DryRun(false),
        manager,
        item
      )
    )
