package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.*

import com.histmersion.domain.library.workflows.*

import com.augustnagro.magnum.connect

import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.{Job, JobRow, JobRuns, RunRow}
import com.histmersion.domain.automation.{Goal, Run}
import com.histmersion.domain.automation.goals.FocusRepo
import com.histmersion.domain.automation.managers.{ManagerLight, SweepAcquireItem}
import com.histmersion.domain.library.sources.SourcesControllers
import com.augustnagro.magnum.DbCon

import javax.sql.DataSource
import com.histmersion.server.ServerWiring

/** How the agent suites set up runs by hand: through the jobs area, as the engine records them. */
object TestRuns:
  private def runs(ds: DataSource): JobRuns = JobRuns(Transactor(ds))

  /** The approvals over `ds`, as the engine records them. */
  def approvals(ds: DataSource): ApprovalService =
    ApprovalService(Transactor(ds), java.time.Clock.systemUTC())

  /** The clock the time-of-day suites run on: noon UTC of the day it is made, moving with the wall clock — after the
    * approval digest's hour, and hours away from a watcher's weekly cron at 22:30 and from midnight, whenever they run.
    */
  def noonClock(wall: java.time.Clock = java.time.Clock.systemUTC()): java.time.Clock =
    val now = java.time.Instant.now(wall)
    val noon = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS).plusSeconds(12L * 3600L)
    java.time.Clock.offset(wall, java.time.Duration.between(now, noon))

  def create(
      ds: DataSource,
      p: Pipeline,
      target: AgentsControllers.Target,
      model: Option[AgentsControllers.ModelName],
      budgetUsd: BigDecimal,
      budgetTokens: AgentsControllers.BudgetTokens,
      params: RunParams = RunParams(),
      acquireItem: Option[SweepAcquireItem.Item] = None,
      goal: Option[Goal.Slug] = None,
      parent: Option[Run.Id] = None,
      origin: Origin = Origin.Person,
      dryRun: RunService.DryRun = RunService.DryRun(false)
  ): RunInfo =
    val id = runs(ds).create(
      RunService
        .row(p, target, model, budgetUsd, budgetTokens, params, acquireItem, goal, parent, origin, dryRun)
    )
    connect(ds)(AgentRepo.get(RunInfo.Id(id.value))).get

  def createIn(
      ds: DataSource,
      p: Pipeline,
      target: AgentsControllers.Target,
      model: Option[AgentsControllers.ModelName],
      budgetUsd: BigDecimal,
      budgetTokens: AgentsControllers.BudgetTokens,
      params: RunParams,
      acquireItem: Option[SweepAcquireItem.Item],
      goal: Option[Goal.Slug],
      parent: Option[Run.Id],
      origin: Origin,
      dryRun: RunService.DryRun,
      managerId: Option[ManagerLight.Id] = None,
      managerItem: Option[FocusRepo.WorkRow.Id] = None
  )(using DbCon): Run.Id =
    runs(ds).createIn(
      RunService.row(
        p,
        target,
        model,
        budgetUsd,
        budgetTokens,
        params,
        acquireItem,
        goal,
        parent,
        origin,
        dryRun,
        managerId,
        managerItem
      )
    )

  def transition(
      ds: DataSource,
      id: Run.Id,
      from: RunState,
      to: RunState,
      error: Option[JobRow.Error] = None,
      resumeAt: Option[java.time.Instant] = None
  ): Option[RunState] =
    RunState
      .next(from, to)
      .flatMap(t =>
        Option.when(runs(ds).move(Job.Kind.assume(JobKind), id, RunRow.Status(from.wire.value), RunRow.Status(t.wire.value), error, None, resumeAt))(t)
      )

  def reconcile(ds: DataSource): (Vector[(Run.Id, Option[RunRow.Origin])], Vector[Run.Id]) =
    runs(ds).reconcile(
      Job.Kind.assume(JobKind),
      RunRow.Status(RunState.Running.wire.value),
      RunRow.Status(RunState.Paused.wire.value),
      JobRow.Error(PauseReason.Interrupted.wire.value),
      RunRow.Status(RunState.Queued.wire.value),
      Nil
    )

  /** The library's routing of a source's proposals, as the composition builds it for engine `a`. */
  def proposals(
      a: AgentsControllers,
      h: ServerWiring,
      ds: DataSource
  ): SourceProposals =
    new SourceProposals(
      SourceApprovalService(Transactor(ds), a.approvalStore, a.clock),
      a.config,
      ck => h.library.sources.get(ck, body = SourcesControllers.GetBody(false)).toOption.map(_.source.status),
      p =>
        h.library.sources
          .patch(p.citekey, p.patch)
          .left
          .map(e => SourceProposals.Outcome.Failed.Why(e.message.value))
          .map(_ => SourceProposals.Outcome.Applied.What(p.describe.value))
    )
