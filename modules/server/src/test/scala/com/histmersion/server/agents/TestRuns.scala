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

  /** The library's routing of a source's proposals, as the composition builds it for engine `a`. */
  def proposals(
      a: com.histmersion.domain.automation.agents.AgentsHandlers,
      h: com.histmersion.server.Handlers
  ): SourceProposals =
    SourceProposals(
      a.approvals,
      a.config,
      ck => h.getSource(ck, body = false).toOption.map(_.source.status),
      p => h.patchSource(p.citekey, p.patch).left.map(_.message).map(_ => p.describe)
    )
