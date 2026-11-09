package com.histmersion
package domain.automation.managers

import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.util.jobs.{Job, RunRow}

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Autopilot.{DrainInfo, DrainSet, EngineStatus}
import com.histmersion.api.automation.Queue.QueueAdd

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.automation.Agents
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.automation.Queue

/** Drain mode (#3762 proposal 4): `histmersion agent autopilot drain` stops the central scheduler from admitting
  * anything new — and the managers' tick from deciding approvals by code (a decision wakes a waiting run) and from
  * draining the ingest backlog — while the runs in flight finish. Once no agent run is queued or running it is DRAINED
  * (recorded once, a worklog line): safe to stop the server for a deploy; what waits is checkpointed and goes on after
  * the restart. `undrain` (or turning the autopilot on) lifts it. This replaces switching the autopilot off for a
  * deploy, which paused every manager it ran; `off` still works as before.
  */
final class DrainService(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsControllers
):

  private val agentKind: Job.Kind = Job.Kind.assume(Agents.JobKind)

  private def occupied: RowCount =
    agents.jobRuns.count(
      agentKind,
      (RunRow.Status(Agents.RunState.Queued.wire.value), RunRow.Status(Agents.RunState.Running.wire.value))
    )

  private def count(n: Int, noun: String): String =
    Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(noun)).value

  /** Draining now? */
  def draining: Boolean = agents.autopilotRow.drain().isDefined

  /** The drain as shown (None: not draining). */
  def info(): Option[DrainInfo] =
    agents.autopilotRow.drain().map { (since, by, at) =>
      val active = occupied.value
      DrainInfo(
        DrainInfo.Since(since.value),
        DrainInfo.By(by.value),
        DrainInfo.Drained(at.isDefined && active == 0),
        at.map(a => DrainInfo.DrainedAt(a.value)),
        DrainInfo.InFlight(active)
      )
    }

  /** Why nothing new starts (the scheduler's stop), while draining. */
  def block: Option[SchedulerPolicy.Block] =
    agents.autopilotRow
      .drain()
      .map((since, by, _) =>
        SchedulerPolicy.Block(
          SchedulerPolicy.Block.Why.Draining(
            SchedulerPolicy.Block.Since(since.value),
            Option.when(by.value.nonEmpty)(SchedulerPolicy.Block.By(by.value))
          )
        )
      )

  /** From the managers' tick: the first tick with no run queued or running — and no acquisition list re-check job,
    * which stops after the item in flight — records it drained (a run that resumes by itself afterwards — an allowance,
    * a restart — makes it draining again).
    */
  def tick(now: Instant): List[String] =
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
  def set(in: DrainSet, now: Instant = Instant.now(agents.clock)): EngineStatus =
    val by = in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
    val changed = agents.autopilotRow.setDrain(in.on.value, AutopilotRepo.Row.ChangedBy(by))
    val line =
      if in.on.value then
        (if changed then s"draining (by $by): nothing new starts; the runs in flight finish" else "draining already"
      )
      else if changed then s"drain lifted (by $by): the scheduler admits work again"
      else "not draining"
    if changed then log(s"autopilot: $line")
    agents.managers.nudge()
    val _ = tick(now)
    agents.autopilot.status(now).copy(changes = List(EngineStatus.Changes(line)))

  private def log(line: String): Unit =
    val _ = agents.workLists.add(WorkListControllers.ListName("log"), QueueAdd(Queue.QueueItem.Text(line.take(1000))))
    scribe.info(line)
