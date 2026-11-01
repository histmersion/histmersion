package com.histmersion.server.agents

import com.histmersion.api.Agents.*
import com.histmersion.db.AgentRepo
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.JobRuns
import com.augustnagro.magnum.DbCon

import javax.sql.DataSource

/** How the agent suites set up runs by hand: through the jobs area, as the engine records them. */
object TestRuns:
  private def runs(ds: DataSource): JobRuns = JobRuns(Transactor(ds))

  def create(
      ds: DataSource,
      p: Pipeline,
      target: String,
      model: Option[String],
      budgetUsd: BigDecimal,
      budgetTokens: Long,
      params: RunParams = RunParams(),
      acquireItem: Option[Int] = None,
      goal: Option[String] = None,
      parent: Option[String] = None,
      origin: Origin = Origin.Person,
      dryRun: Boolean = false
  ): RunInfo =
    val id = runs(ds).create(
      AgentRepo.runRow(p, target, model, budgetUsd, budgetTokens, params, acquireItem, goal, parent, origin, dryRun)
    )
    AgentRepo.get(ds, id).get

  def createIn(
      ds: DataSource,
      p: Pipeline,
      target: String,
      model: Option[String],
      budgetUsd: BigDecimal,
      budgetTokens: Long,
      params: RunParams,
      acquireItem: Option[Int],
      goal: Option[String],
      parent: Option[String],
      origin: Origin,
      dryRun: Boolean,
      managerId: Option[String] = None,
      managerItem: Option[Long] = None
  )(using DbCon): String =
    runs(ds).createIn(
      AgentRepo.runRow(
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
      id: String,
      from: RunState,
      to: RunState,
      error: Option[String] = None,
      resumeAt: Option[java.time.Instant] = None
  ): Option[RunState] =
    RunState
      .next(from, to)
      .flatMap(t => Option.when(runs(ds).move(JobKind, id, from.wire, t.wire, error, None, resumeAt))(t))

  def reconcile(ds: DataSource): (Vector[(String, Option[String])], Vector[String]) =
    runs(ds).reconcile(
      JobKind,
      RunState.Running.wire,
      RunState.Paused.wire,
      PauseReason.Interrupted.wire,
      RunState.Queued.wire,
      Nil
    )
