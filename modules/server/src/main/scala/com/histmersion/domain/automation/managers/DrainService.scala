package com.histmersion
package domain.automation.managers

import com.histmersion.api.automation.autopilot.DrainInfo
import com.histmersion.domain.automation.scheduler.Block
import com.histmersion.api.automation.autopilot.DrainSet
import com.histmersion.api.automation.autopilot.EngineStatus
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.api.automation.queue.QueueItem

import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.util.jobs.{Job, RunRow}

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.automation.Agents
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.automation.Queue
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}

/** Drain mode (#3762 proposal 4): `histmersion agent autopilot drain` stops the central scheduler from admitting
  * anything new — and the managers' tick from deciding approvals by code (a decision wakes a waiting run) and from
  * draining the ingest backlog — while the runs in flight finish. Once no agent run is queued or running it is DRAINED
  * (recorded once, a worklog line): safe to stop the server for a deploy; what waits is checkpointed and goes on after
  * the restart. `undrain` (or turning the autopilot on) lifts it. This replaces switching the autopilot off for a
  * deploy, which paused every manager it ran; `off` still works as before.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class DrainService(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsControllers
):

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val agentKind: Job.Kind = Job.Kind.assume(Agents.JobKind)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def occupied: RowCount =
    agents.jobRuns.count(
      agentKind,
      (RunRow.Status(Agents.RunState.Queued.wire.value), RunRow.Status(Agents.RunState.Running.wire.value))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def count(n: Int, noun: String): String =
    Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(noun)).value

  /** Draining now? */
  def draining: Boolean = agents.autopilotRow.drain().isDefined

  /** The drain as shown (None: not draining). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def info(): Option[DrainInfo] =
    agents.autopilotRow.drain().map { (since, by, at) =>
      val active = occupied.value
      DrainInfo(
        DrainInfo.Since(since.value),
        DrainInfo.By(by.value),
        (if at.isDefined && active == 0 then DrainInfo.Drained.Drained else DrainInfo.Drained.Running),
        at.map(a => DrainInfo.DrainedAt(a.value)),
        DrainInfo.InFlight(active)
      )
    }

  /** Why nothing new starts (the scheduler's stop), while draining. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def block: Option[Block] =
    agents.autopilotRow
      .drain()
      .map((since, by, _) =>
        Block(
          Block.Why.Draining(
            Block.Since(since.value),
            Option.when(by.value.nonEmpty)(Block.By(by.value))
          )
        )
      )

  /** From the managers' tick: the first tick with no run queued or running — and no acquisition list re-check job,
    * which stops after the item in flight — records it drained (a run that resumes by itself afterwards — an allowance,
    * a restart — makes it draining again).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tick(now: Instant)(using WritePermit): List[String] =
    agents.autopilotRow.drain() match
      case None                 => Nil
      case Some((since, _, at)) =>
        val active = occupied.value + (if agents.workflows.managerKinds.exists(_.busy) then 1 else 0)
        if active == 0 && at.isEmpty then
          agents.autopilotRow.drained(Some(now))
          log(
            s"autopilot: drained (draining since ${since.value}): no agent run is queued or running — safe to stop the server"
          )
          List("drained")
        else if active > 0 && at.isDefined then
          agents.autopilotRow.drained(None)
          List(s"draining again: ${count(active, "run")} in flight")
        else Nil

  /** Start or lift the drain; answers the autopilot's status with what it did. */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def set(in: DrainSet, now: Instant = Instant.now(agents.clock))(using WritePermit): EngineStatus =
    val by = in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
    val changed = agents.autopilotRow.setDrain(in.on.on, AutopilotRepo.Row.ChangedBy(by))
    val line =
      if in.on.on then
        (if changed then s"draining (by $by): nothing new starts; the runs in flight finish" else "draining already"
      )
      else if changed then s"drain lifted (by $by): the scheduler admits work again"
      else "not draining"
    if changed then log(s"autopilot: $line")
    agents.managers.nudge()
    val _ = tick(now)
    agents.autopilot.status(now).copy(changes = List(EngineStatus.Changes(line)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def log(line: String): Unit =
    agents.workLists.logLine(
      QueueAdd(QueueItem.Text(line.take(1000))),
      domain.automation.workflows.InTreeWorkflow.Drain.creator
    )
    scribe.info(line)
