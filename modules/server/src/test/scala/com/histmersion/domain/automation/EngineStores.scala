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
  def article(slug: String, title: String): Unit
  def source(citekey: String, title: String): Unit
  def within[A](work: DbCon ?=> A): A

  /** A new queued agent run of `pipeline` on `target` (for `goal`, by `manager`'s `item`); its id. */
  def run(
      target: String,
      goal: Option[String] = None,
      manager: Option[String] = None,
      item: Option[Long] = None,
      pipeline: String = "enrich-source"
  ): String =
    runs.create(
      RunRow(
        "agent",
        "queued",
        pipeline,
        1,
        "source",
        target,
        None,
        Some("local:scripted"),
        BigDecimal(1),
        1000L,
        "{}",
        None,
        goal,
        None,
        "person",
        false,
        manager,
        item
      )
    )
