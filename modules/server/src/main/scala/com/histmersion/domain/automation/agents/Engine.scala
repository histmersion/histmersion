package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{External, Problem, ProblemLog}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.log.TraceScope

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.workflows.Pipelines
import com.histmersion.server.agents.CliProcess

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.workflows.{ToolName, Workflows}
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, Semaphore}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import com.histmersion.api.automation.{AgentsApi, AgentsMarkdown, PlanQuota}
import com.histmersion.api.Api
import com.histmersion.domain.automation.Run
import com.histmersion.util.jobs.Carried
import com.histmersion.util.jobs.Quiescent
import com.histmersion.util.jobs.Job
import com.histmersion.util.jobs.RunRow
import com.histmersion.domain.automation.AgentRole
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.jobs.JobRepo
import com.histmersion.util.jobs.JobRow
import com.histmersion.util.jobs.JobRuns
import Halt.Stopped
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Pipeline as RunPipeline
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Goal
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The run pool: `agents.max-concurrent-runs` workers draining a queue of run ids, on one daemon thread hosting an Ox
  * scope (the same arrangement as the OCR [[com.histmersion.server.Jobs]]). Runs are durable — `jobs` + `agent_steps` —
  * so the in-memory queue is rebuilt at [[start]].
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class Engine(
    val ds: DataSource,
    cfgNow: => AgentsConfig,
    val agents: AgentsControllers,
    val workflows: Workflows
) extends Quiescent:
  import Engine.{Era, Paused}

  /** Read at each use (a config reload switches it); a run reads it once, into its [[RunCtx]]. */
  def cfg: AgentsConfig = cfgNow

  /** A queued run and the generation it was queued on: it runs there — on the database it was queued in, even after a
    * config reload swapped the database (docs/research-plugins.md §3.6). `era` is the restore era it was queued in: one
    * queued before the last restore names a row the restore replaced, and is dropped.
    */
  final private case class Queued(id: Run.Id, work: Carried[Unit], era: Era)
  private val queue = LinkedBlockingQueue[Queued]()

  // ── a restore ──────────────────────────────────────────────────────────────────────────────────

  @volatile private var era: Era = Era()

  /** Held while a restore runs: no worker takes a queued run, the resumer does nothing. A `var` guarded by `hold`'s
    * monitor, because a worker must BLOCK on it rather than spin.
    */
  private val hold = Object()
  private var holding = false

  private def awaitRelease(): Unit = hold.synchronized(while holding do hold.wait())

  val name: Quiescent.Name = Quiescent.Name("the agent run pool")

  /** No queued run starts; every running run is interrupted, so that it pauses at once as interrupted (a run nobody
    * watches goes on by itself after the restore, as after a restart).
    */
  def quiesce(): Unit =
    hold.synchronized { holding = true }
    running.values.asScala.foreach(_.thread.interrupt())

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def busy: Vector[Quiescent.Busy] = running.keySet.asScala.toVector.sorted.map(id => Quiescent.Busy(s"agent run $id"))

  /** After a restore: the runs queued in memory name rows the restore replaced, so they are dropped; the restored
    * database's runs are reconciled as at a start (its running ones paused as interrupted — none of them runs here —
    * its queued ones queued).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def restored()(using WritePermit)(using Creator.New): Unit =
    era = Era()
    Iterator.continually(queue.poll()).takeWhile(_ != null).foreach(_.work.drop())
    // an engine that never started (agents off, the lease elsewhere) leaves the rows to the start that comes
    if scope.isDefined then
      val (paused, queued) = reconcile(except = Nil)
      queued.foreach(submit)
      if paused != RowCount(0) then
        scribe.info(s"restore: $paused agent run(s) of the restored database paused as interrupted")

  def resume(): Unit = hold.synchronized { holding = false; hold.notifyAll() }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jobKind: Job.Kind = Job.Kind.assume(JobKind)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def rowStatus(s: RunState): RunRow.Status = RunRow.Status(s.wire.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val llmSlots: Semaphore = Semaphore(math.max(1, cfg.maxLlmCallsInFlight.value))

  /** The tools a step of `run` offers its agent: the agent's own, as far as its workflow lets the agent's role use them
    * (a run of no registered workflow offers none).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def offered(run: RunInfo, agent: AgentDef): List[ToolName] =
    workflows
      .parse(run.pipeline.value)
      .fold(Nil)(w => agent.tools.filter(w.allowedTools(agent.role.transformInto[AgentRole])))

  /** When the trace was last pruned (epoch ms; 0 = not yet in this process). */
  private val lastPrune = java.util.concurrent.atomic.AtomicLong(0L)

  /** Delete traced messages older than `agents.trace.keep-messages-days`, at most once every `prune-every-minutes` (or
    * now with `force`). Returns how many went. Never fails a run: a failed prune is logged and tried again next time.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pruneTrace(
      force: Engine.PruneTraceForce = Engine.PruneTraceForce(false),
      now: PlanQuotaRepo.EpochMillis = PlanQuotaRepo.EpochMillis(agents.clock.millis())
  )(using WritePermit): RowCount =
    val every = math.max(1, cfg.trace.pruneEveryMinutes.value).toLong * 60000L
    val last = lastPrune.get()
    if cfg.trace.keepMessagesDays.value <= 0 || (!force.value && now.value - last < every) || !lastPrune.compareAndSet(
        last,
        now.value
      )
    then RowCount(0)
    else
      try RowCount(agents.runs.pruneMessages(RunService.RetentionDays(cfg.trace.keepMessagesDays.value)).value)
      catch
        case e: Exception =>
          scribe.warn(s"agent trace pruning failed: ${e.getMessage}")
          RowCount(0)

  /** How many traced messages [[pruneTrace]] deleted, said in the log. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def logPruned(n: RowCount): Unit =
    if n.value > 0 then
      scribe.info(s"agent trace: pruned $n message(s) older than ${cfg.trace.keepMessagesDays} day(s)")

  /** What a person asked of a RUNNING run, read by the run between steps — and what the stall watchdog watches: the
    * last sign of life, the step it was in, and the CLI processes it has alive.
    */
  final private class Control(val thread: Thread):
    @volatile var pause: Boolean = false
    @volatile var cancel: Boolean = false
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @volatile var lastBeat: PlanQuotaRepo.EpochMillis = PlanQuotaRepo.EpochMillis(agents.clock.millis())

    /** When `lastBeat` was last written to `jobs.heartbeat_at` (at most every heartbeat-seconds). */
    @volatile var persisted: PlanQuotaRepo.EpochMillis = PlanQuotaRepo.EpochMillis(0L)
    @volatile var seq: Int = 0

    /** Set by the watchdog before it interrupts the run: how the run settles. */
    @volatile var stalled: Option[Halt] = None
    val procs: java.util.Set[Process] = ConcurrentHashMap.newKeySet[Process]()

  object Control:
    type Pause = Pause.T
    object Pause extends Newtype[Boolean, Pure]
    type Cancel = Cancel.T
    object Cancel extends Newtype[Boolean, Pure]
    type LastBeat = LastBeat.T
    object LastBeat extends Newtype[Long, Pure]
    type Persisted = Persisted.T
    object Persisted extends Newtype[Long, Pure]
    type Seq = Seq.T
    object Seq extends Newtype[Int, Pure]
  private val running = ConcurrentHashMap[Run.Id, Control]()

  /** A sign of life of run `id` (a step boundary, a model call, a CLI line); `seq` = the step it is in. Called from the
    * run's thread and from the threads reading its processes and model calls: the heartbeat's write is a unit of the
    * engine's worker (the run's own unit, on its thread).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[histmersion] def beat(id: Run.Id, seq: Option[Engine.BeatSeq] = None): Unit =
    Option(running.get(id)).foreach { c =>
      val now = PlanQuotaRepo.EpochMillis(agents.clock.millis())
      c.lastBeat = now
      seq.foreach(s => c.seq = s.value)
      if now.value - c.persisted.value >= math.max(1, cfg.watchdog.heartbeatSeconds.value).toLong * 1000L then
        c.persisted = now
        try agents.workers.unit(agents.jobRuns.heartbeat(jobKind, id))
        catch case e: Exception => scribe.warn(s"agent run $id: heartbeat not written: ${e.getMessage}")
    }

  /** Take a plan pool's slot, beating while waiting for it (other runs' calls hold it). */
  private[histmersion] def acquireBeating(id: Run.Id, slot: Semaphore)(using WritePermit): Unit =
    while !slot.tryAcquire(Engine.BeatWhileWaitingSeconds, java.util.concurrent.TimeUnit.SECONDS) do beat(id)

  /** Called with every run that settles (done, failed, cancelled, paused, waiting) — the managers' nudge. A `var`
    * because the supervisor is attached after construction.
    */
  @volatile private var settledHooks: List[RunInfo => WritePermit ?=> Unit] = Nil

  /** `f` runs when a run settles, on the run's thread, under the run's unit permit. */
  def onSettled(f: RunInfo => WritePermit ?=> Unit): Unit = settledHooks = settledHooks :+ f

  /** The stall watchdog's pass at `now` (epoch ms): every running run whose last sign of life is older than
    * `agents.watchdog.stall-minutes` has its processes killed and is interrupted; it settles paused `stalled` (resuming
    * after resume-minutes), or failed on its max-stalls-th stall of one step. Returns the ids.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def checkStalls(now: PlanQuotaRepo.EpochMillis = PlanQuotaRepo.EpochMillis(agents.clock.millis()))(using
      WritePermit
  ): List[Run.Id] =
    val limit = cfg.watchdog.stallMinutesFor(cfg.providers).value.toLong * 60000L
    running.asScala.toList
      .filter((_, c) => c.stalled.isEmpty && !c.cancel && now.value - c.lastBeat.value > limit)
      .map { (id, c) =>
        val mins = (now.value - c.lastBeat.value) / 60000L
        val mark = s"${Engine.StallMark} ${c.seq}"
        val prior = agents.runs.get(id).toList.flatMap(_.warnings).count(_.value.startsWith(mark + " "))
        val n = prior + 1
        agents.jobRuns.warn(
          jobKind,
          id,
          JobRepo.WarningText(s"$mark (${n}x): no sign of life for $mins min; its processes were killed".take(500))
        )
        val (step, minutes) = (Problem.Count(c.seq.toLong), Problem.Count(mins))
        c.stalled = Some(if n >= math.max(1, cfg.watchdog.maxStalls.value) then
          Halt.Fail(Problem.Run.Stopped(Problem.Run.Stop.Stalled(step, minutes, Problem.Count(n.toLong))))
        else
          Halt.Pause(
            PauseReason.Stalled,
            Problem.Run.Stopped(
              Problem.Run.Stop.Silent(step, minutes, Problem.Count(n.toLong), Problem.Count.of(cfg.watchdog.maxStalls))
            ),
            Some(
              java.time.Instant
                .ofEpochMilli(now.value)
                .plusSeconds(math.max(0, cfg.watchdog.resumeMinutes.value).toLong * 60L)
            )
          ))
        scribe.warn(s"agent run $id stalled at step ${c.seq} (no sign of life for $mins min); killing its processes")
        c.thread.interrupt()
        c.procs.asScala.toList.foreach(p =>
          try CliProcess.kill(p)
          catch case _: Exception => ()
        )
        id
      }

  private[histmersion] def stalled(id: Run.Id): Option[Halt] = Option(running.get(id)).flatMap(_.stalled)

  /** The scope thread; a `var` because the pool starts after construction (no side effects in initialisers) and stops
    * in tests.
    */
  private var scope: Option[Thread] = None

  /** Reconcile (an interrupted run is paused, not failed), re-enqueue queued runs, start the workers. Idempotent.
    * Returns how many runs were paused as interrupted.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def start()(using WritePermit)(using Creator.New): Int =
    val (paused, queued) = reconcile()
    logPruned(pruneTrace(force = Engine.PruneTraceForce(true)))
    queued.foreach(submit)
    if scope.isEmpty then
      stopping = false
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agents").unstarted(() => pool())
      scope = Some(t)
      t.start()
      val w = Thread.ofPlatform().daemon(true).name("histmersion-agents-resume").unstarted(() => resumer())
      waker = Some(w)
      w.start()
    paused.value

  /** [[start]]'s first half: a run left `running` is paused as interrupted (and, when nobody watches it, set to resume
    * by itself shortly). Returns how many were paused, and the queued runs.
    */
  private[histmersion] def reconcile()(using WritePermit): (RowCount, Vector[Run.Id]) = reconcile(
    running.keySet.asScala.toList
  )

  /** As [[reconcile]], leaving the runs `except` be: after a restore none (a run still running here is fenced off and
    * cannot go on with the restored rows).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def reconcile(except: List[Run.Id])(using WritePermit): (RowCount, Vector[Run.Id]) =
    // a run this engine is running is not interrupted, and one it holds queued is not queued twice: a
    // config reload that swapped back to its database must leave them be (docs/research-plugins.md §3.6)
    val inQueue = queue.asScala.map(_.id).toSet
    val (paused, queuedAll) = agents.jobRuns.reconcile(
      jobKind,
      rowStatus(RunState.Running),
      rowStatus(RunState.Paused),
      Stopped.Interrupted.problem,
      rowStatus(RunState.Queued),
      except
    )
    val queued = queuedAll.filterNot(inQueue)
    // a run nobody is watching goes on by itself after a restart; a person's waits for them
    val at = java.time.Instant
      .now(agents.clock)
      .plusSeconds(math.max(0, cfg.watchdog.interruptedResumeMinutes.value).toLong * 60L)
    paused.foreach { (id, origin) =>
      if origin.flatMap(o => Origin.parse(Origin.Wire(o.value))).exists(_.unattended)
        && !agents.jobRuns.resumeAt(jobKind, id, rowStatus(RunState.Paused), at)
      then scribe.warn(s"agent run $id was not set to resume by itself: its row is no longer paused")
    }
    (RowCount(paused.size), queued)

  /** The thread that resumes runs paused for an allowance; a `var` for the same reason as [[scope]]. */
  private var waker: Option[Thread] = None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def resumer()(using Creator.New): Unit =
    // the stall check shares this loop: at least once a minute
    val every = math.min(60, math.max(5, cfg.allowanceCheckSeconds.value)).toLong * 1000L
    try
      while !stopping do
        Thread.sleep(every)
        awaitRelease() // a restore holds the resumer here
        try
          agents.workers.unit(agents.pins.pinned {
            val now = java.time.Instant.now(agents.clock)
            val _ = checkStalls(PlanQuotaRepo.EpochMillis(now.toEpochMilli))
            val _ = resumeDue(now)
            val _ = resumeFreed(now)
          })
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent resume check failed: ${e.getMessage}")
    catch case _: InterruptedException => ()

  /** Re-queue every run paused for an allowance whose window has freed (`resume_at` ≤ now). Returns the ids. When the
    * window has not freed enough after all (another run spent it meanwhile), the run simply pauses again with the next
    * time.
    */
  def resumeDue(now: java.time.Instant)(using WritePermit)(using Creator.New): List[Run.Id] =
    agents.jobRuns.due(jobKind, rowStatus(RunState.Paused), now).toList.filter { id =>
      transition(id, RunState.Paused, RunState.Queued).map(_ => submit(id)).isDefined
    }

  /** Re-queue every run paused on a plan pool (`plan-quota`) that would get a model NOW, before the reset it waits for:
    * its role is picked again — which takes a fresh reading of a pool whose verdict hangs only on a stale one (at most
    * once per `stale-minutes` per pool), and sees a hold released. A run whose pool is still below its floor stays
    * paused; nothing is read for a server with no such run. Returns the ids.
    */
  def pausedWaiting(now: java.time.Instant): List[Paused] =
    agents.jobRuns
      .pausedWaiting(jobKind, rowStatus(RunState.Paused), now)
      .toList
      .map { (id, pipeline, _, error, resumes) =>
        val at = resumes.flatMap(_.instant)
        val byItself = at.isDefined || error.exists(Stopped.of(_).pausedOn(PauseReason.PlanQuota))
        Paused(id, pipeline, error, at, if byItself then Actor.Agents else Actor.Person)
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resumeFreed(now: java.time.Instant)(using WritePermit)(using Creator.New): List[Run.Id] =
    agents.jobRuns
      .pausedWaiting(jobKind, rowStatus(RunState.Paused), now)
      .toList
      .filter((_, _, _, error, _) => error.exists(Stopped.of(_).pausedOn(PauseReason.PlanQuota)))
      .flatMap { (id, pipeline, model, error, _) =>
        val roles = error
          .flatMap(ModelPicker.pausedRole)
          .map(List(_))
          .getOrElse(Pipeline.parse(Pipeline.Wire(pipeline.value)).map(_.roles).getOrElse(Nil))
        val notes = List.newBuilder[String]
        // the run's own model for the role: its --model-for, else its --model
        val run = agents.runs.get(id)
        def modelOf(r: Role) =
          run.fold(model.map(m => AgentsControllers.ModelName(m.value)))(
            _.modelFor(r).map(m => AgentsControllers.ModelName(m.value))
          )
        val free = roles.find(r =>
          agents.picker
            .pick(
              r,
              modelOf(r),
              PlanQuotaRepo.EpochMillis(now.toEpochMilli),
              Some(PlanQuota.RefreshTrigger.Paused),
              n => notes += s"${r.wire}: $n"
            )
            .isRight
        )
        free.flatMap { r =>
          transition(id, RunState.Paused, RunState.Queued).map { _ =>
            (notes.result() :+ s"resumed before its reset: the ${r.wire} chain has a usable model again")
              .foreach(w => agents.jobRuns.warn(jobKind, id, JobRepo.WarningText(w.take(500))))
            scribe.info(s"agent run $id resumed: the ${r.wire} chain has a usable model again")
            submit(id)
            id
          }
        }
      }

  /** Set by [[stop]] before the pool is interrupted. A worker clears its thread's interrupt after each run (a cancel
    * aimed at one run must not leak into the next), which also swallowed the pool's own shutdown interrupt when it
    * arrived mid-run: the worker went back to `queue.take()`, the supervised scope never finished, and the pool thread
    * lived forever (#865). Workers check this flag instead of relying on the interrupt surviving.
    */
  @volatile private var stopping = false

  def stop(): Unit =
    stopping = true
    // the queued runs stay queued in the database (a start re-enqueues them); their pins go
    Iterator.continually(queue.poll()).takeWhile(_ != null).foreach(_.work.drop())
    scope.foreach(_.interrupt())
    scope = None
    waker.foreach(_.interrupt())
    waker = None

  /** Compare-and-set state change through [[RunState.next]]: `None` when refused or when the row had already moved on
    * (another worker, a concurrent cancel). `resumeAt` is when a run paused for an allowance may go on; every
    * transition sets it (None clears it).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def transition(
      id: Run.Id,
      from: RunState,
      to: RunState,
      error: Option[Problem] = None,
      progress: Option[String] = None,
      resumeAt: Option[java.time.Instant] = None
  )(using WritePermit): Option[RunState] =
    RunState
      .next(from, to)
      .flatMap(target =>
        Option.when(
          agents.jobRuns.move(
            jobKind,
            id,
            rowStatus(from),
            rowStatus(target),
            error,
            progress.map(JobRow.Progress(_)),
            resumeAt
          )
        )(target)
      )

  def submit(id: Run.Id)(using Creator.New): Unit =
    queue.put(Queued(id, agents.pins.carry(agents.workers.unit(executeInItsTrace(id))), era))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pool(): Unit =
    try
      ox.supervised {
        (1 to math.max(1, cfg.maxConcurrentRuns.value)).foreach { _ =>
          val _ = ox.forkUser(worker())
        }
        ()
      }
    catch
      case _: InterruptedException => ()
      case NonFatal(e)             => scribe.error(s"agent pool died: ${e.getMessage}", e)

  private def worker(): Unit =
    var alive = true // the loop's exit flag; interruption of the pool clears it
    while alive && !stopping do
      try
        val q = queue.take()
        awaitRelease() // a restore holds the pool here
        Thread.interrupted() // a cancel aimed at the previous run must not leak into this one
        // a run queued before a restore names a row the restore replaced: its restored row was reconciled instead
        if !stopping && (q.era eq era) then
          agents.workers.unit {
            q.work.run()
            logPruned(pruneTrace())
          }
        else q.work.drop()
      // a cancel or a restore that interrupted a run as it ended must not end the worker: only the pool's stop does
      catch case _: InterruptedException => alive = !stopping

  private def executeInItsTrace(id: Run.Id)(using WritePermit)(using Creator.New): Unit =
    TraceScope.resumed(agents.jobRuns.trace(id))(execute(id))

  /** Run one queued run to its next resting state. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private[histmersion] def execute(id: Run.Id)(using WritePermit)(using Creator.New): Unit =
    agents.runs.get(id) match
      case None      => ()
      case Some(run) =>
        val pipe = workflows.parse(run.pipeline.value)
        val sameVersion = pipe.forall(w => agents.jobRuns.pipelineVersion(id).forall(_ == w.version))
        if transition(id, RunState.Queued, RunState.Running).isDefined then
          val ctl = Control(Thread.currentThread())
          running.put(id, ctl)
          val outcome: Either[Halt, String] =
            try
              pipe match
                case None =>
                  Left(Halt.Fail(Problem.Run.Stopped(Problem.Run.Stop.NoPipeline(Problem.Raw.of(run.pipeline)))))
                case Some(_) if !sameVersion => Left(Halt.Fail(Problem.Run.Stopped(Problem.Run.Stop.PipelineChanged)))
                case Some(p)                 =>
                  val hooks = CliProcess.Hooks(
                    pr => { val _ = ctl.procs.add(pr) },
                    pr => { val _ = ctl.procs.remove(pr) },
                    () => beat(id)
                  )
                  CliProcess.withHooks(hooks) {
                    val ctx = RunCtx(run.modify(_.status).setTo(RunInfo.Status(RunState.Running.wire.value)), this, p)
                    given Creator.New = ctx.creator
                    p.run(ctx).map(_.value)
                  }
            catch
              case _: InterruptedException if ctl.cancel            => Left(Halt.Cancel)
              case _: InterruptedException if ctl.stalled.isDefined => Left(ctl.stalled.get)
              case e: InterruptedException                          =>
                // the pool stopping under a run nobody watches: it goes on after the restart by itself
                val at = Option.when(run.origin.flatMap(Origin.parse).exists(_.unattended))(
                  java.time.Instant
                    .now(agents.clock)
                    .plusSeconds(math.max(0, cfg.watchdog.interruptedResumeMinutes.value).toLong * 60L)
                )
                Left(Halt.Pause(PauseReason.Interrupted, Problem.Run.Interrupted, at))
              case e: Exception =>
                Left(Halt.Fail(ProblemLog.unexpected(e, Problem.Where(s"agent run $id"))))
            finally
              running.remove(id)
              Thread.interrupted()
          // a cancel or a stall decides how the run settles, whatever the interrupted step made of it
          val settled = if ctl.cancel then Left(Halt.Cancel) else ctl.stalled.map(Left(_)).getOrElse(outcome)
          val recorded = settled match
            case Right(summary) =>
              transition(id, RunState.Running, RunState.Done, progress = Some(summary.take(500)))
            case Left(Halt.Cancel) =>
              transition(
                id,
                RunState.Running,
                RunState.Cancelled,
                error = Some(Problem.Run.Cancelled(Problem.Run.Actor.Person))
              )
            case Left(Halt.Fail(m))                => transition(id, RunState.Running, RunState.Failed, error = Some(m))
            case Left(Halt.Pause(reason, why, at)) =>
              transition(
                id,
                RunState.Running,
                RunState.Paused,
                error = Some(Stopped.Paused(reason, why).problem),
                resumeAt = at
              )
            case Left(Halt.Wait(approval, why)) =>
              val waiting =
                transition(id, RunState.Running, RunState.WaitingApproval, progress = Some(why.value.take(500)))
              // decided in the instant between opening it and this transition: go straight back to the queue
              if waiting.isDefined && agents.approvalStore
                  .approval(Approval.Id(approval.value))
                  .exists(_.state != ApprovalState.Open.wire)
              then
                // not woken: the decision's own wake moved it first
                if !wake(id) then scribe.debug(s"agent run $id: its approval's decision had already woken it")
              waiting
          // a row that moved on while the run ended (a restore replaced it) keeps what it says
          if recorded.isEmpty then
            scribe.warn(s"agent run $id ended, but its row was no longer running (moved on): left as it is")
          notify(run, settled)
          agents.runs
            .get(id)
            .foreach(r =>
              settledHooks.foreach(f =>
                try f(r)
                catch case e: Exception => scribe.warn(s"agent run $id: a settle hook failed: ${e.getMessage}")
              )
            )

  /** A run nobody is watching (started by a schedule or an ingest) that fails or pauses leaves a backlog item — the
    * notification of §5.2, without an external service (never a dry run). An ingest's run paused for an allowance or a
    * plan floor resumes by itself, so it leaves none (a batch of imports would otherwise leave one per source each time
    * a plan dips below its floor).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def notify(run: RunInfo, settled: Either[Halt, String])(using WritePermit): Unit =
    val scheduled = run.origin.contains(Origin.Scheduler.wire)
    val ingested = run.origin.contains(Origin.FromIngest.wire)
    // a dry run writes nothing, its backlog item included
    if (scheduled || ingested) && !run.dryRun.value then
      val why = settled match
        case Left(Halt.Fail(m)) => Some(s"failed: ${m.sentence.take(300)}")
        case Left(Halt.Pause(r, _, _)) if ingested && (r == PauseReason.Allowance || r == PauseReason.PlanQuota) => None
        case Left(Halt.Pause(r, d, _)) => Some(s"paused (${r.wire}): ${d.sentence.take(300)}")
        case _                         => None
      why.foreach { w =>
        val text =
          if scheduled then
            s"scheduled ${run.pipeline}${run.goal.fold("")(g => s" for goal $g")} (agent run ${run.id.value.take(8)}) $w"
          else s"${run.pipeline} of [[${run.target}]] queued by its import (agent run ${run.id.value.take(8)}) $w"
        val _ = agents.queues.add(
          Queue.Kind.Tasks,
          Queue.QueueAdd(
            Queue.QueueItem.Text(text),
            topic = Some(Queue.QueueItem.Topic(if scheduled then "agent schedules" else "auto-enrich")),
            goal = Pipelines.goalOf(run).map(g => Goal.Slug(g.value)),
            job = Some(SpatialHierarchyResult.Job.Id(run.id.value))
          ),
          domain.automation.workflows.InTreeWorkflow.RunNotices.creator
        )
      }

  /** A waiting run whose approval was decided goes back to the queue. `false` = it was not waiting. */
  def wake(id: Run.Id)(using WritePermit)(using Creator.New): Boolean =
    transition(id, RunState.WaitingApproval, RunState.Queued).map(_ => submit(id)).isDefined

  /** Between steps: a requested pause or cancel takes effect here. */
  private[histmersion] def checkControl(id: Run.Id): Either[Halt, Unit] =
    Option(running.get(id)) match
      case Some(c) if c.cancel => Left(Halt.Cancel)
      case Some(c) if c.pause  =>
        Left(Halt.Pause(PauseReason.Requested, Problem.Run.Paused(Problem.Run.PauseReason.Requested, None)))
      case _ => Right(())

  /** Before an LLM call: the run's own budget, and the global daily and monthly caps. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private[histmersion] def checkBudget(id: Run.Id): Either[Halt, Unit] =
    agents.runs.get(id) match
      case None    => Left(Halt.Fail(Problem.Run.Stopped(Problem.Run.Stop.Vanished)))
      case Some(r) =>
        val (day, month) = agents.runs.spendDayMonth()
        val weight = Usage.CacheReadWeight(cfg.budgets.cacheReadWeight.value)
        if r.costUsd.value >= r.budgetUsd.value then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              Problem.Run.Stopped(
                Problem.Run.Stop.Over(
                  Problem.Run.Spending.Usd(
                    Problem.Raw(AgentsMarkdown.usd(r.costUsd.value)),
                    Problem.Raw(AgentsMarkdown.usd(r.budgetUsd.value))
                  )
                )
              )
            )
          )
        else if r.usage.budgeted(weight).value >= r.budgetTokens.value then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              Problem.Run.Stopped(
                Problem.Run.Stop.Over(
                  Problem.Run.Spending.Tokens(
                    Problem.Count.of(r.usage.budgeted(weight)),
                    Problem.Count.of(r.budgetTokens),
                    Problem.Raw.of(cfg.budgets.cacheReadWeight)
                  )
                )
              )
            )
          )
        else if day >= BigDecimal(cfg.budgets.dailyUsd.value) then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              Problem.Run.Stopped(
                Problem.Run.Stop
                  .Over(Problem.Run.Spending.Capped(Problem.Run.Cap.Daily, Problem.Raw.of(cfg.budgets.dailyUsd)))
              )
            )
          )
        else if month >= BigDecimal(cfg.budgets.monthlyUsd.value) then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              Problem.Run.Stopped(
                Problem.Run.Stop
                  .Over(Problem.Run.Spending.Capped(Problem.Run.Cap.Monthly, Problem.Raw.of(cfg.budgets.monthlyUsd)))
              )
            )
          )
        else Right(())

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[histmersion] def budgetInterceptor(id: Run.Id, billing: Billing): RunBudget =
    val r = agents.runs.get(id)
    val weight = Usage.CacheReadWeight(cfg.budgets.cacheReadWeight.value)
    val (use, cost) = agents.runs.runSpend(Job.Id.assume(id.value))
    new RunBudget(
      cost,
      RunBudget.SpentTokens(use.budgeted(weight).value),
      r.map(x => BigDecimal(x.budgetUsd.value)).getOrElse(BigDecimal(0)),
      RunBudget.CapTokens(r.map(_.budgetTokens.value).getOrElse(0L)),
      billing,
      weight
    )

  // ── control ────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def conflict(m: Problem) =
    Api.Error(m, Some(Api.Error.Code(AgentsApi.CodeConflict)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pause(id: Run.Id)(using WritePermit): Either[Api.Error, RunInfo] =
    withRun(id) { r =>
      RunState.parse(RunState.Wire(r.status.value)) match
        case Some(RunState.Running) =>
          Option(running.get(id)).foreach(_.pause = true)
          Right(r)
        case Some(RunState.Queued) =>
          transition(
            id,
            RunState.Queued,
            RunState.Paused,
            error = Some(Stopped.Paused(PauseReason.Requested, Problem.Run.Unstarted).problem)
          )
            .toRight(conflict(Problem.Run.Stopped(Problem.Run.Stop.MovedOn(Problem.Raw.of(id)))))
            .flatMap(_ => get(id))
        case _ =>
          Left(conflict(Problem.Run.NotNow(Problem.Raw.of(id), Problem.Raw.of(r.status), Problem.Run.Control.Pause)))
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resume(
      id: Run.Id,
      budgetUsd: Option[AgentsControllers.BudgetUsd],
      budgetTokens: Option[AgentsControllers.BudgetTokens]
  )(using WritePermit)(using Creator.New): Either[Api.Error, RunInfo] =
    withRun(id) { r =>
      if !RunState.parse(RunState.Wire(r.status.value)).contains(RunState.Paused) then
        Left(conflict(Problem.Run.NotNow(Problem.Raw.of(id), Problem.Raw.of(r.status), Problem.Run.Control.Resume)))
      else
        agents.jobRuns.budget(
          jobKind,
          id,
          budgetUsd.map(b => BigDecimal(b.value)),
          budgetTokens.map(t => JobRepo.TokenBudget(t.value))
        )
        transition(id, RunState.Paused, RunState.Queued)
          .toRight(conflict(Problem.Run.Stopped(Problem.Run.Stop.MovedOn(Problem.Raw.of(id)))))
          .flatMap { _ => submit(id); get(id) }
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cancel(id: Run.Id)(using WritePermit): Either[Api.Error, RunInfo] =
    withRun(id) { r =>
      RunState.parse(RunState.Wire(r.status.value)) match
        case Some(RunState.Running) =>
          Option(running.get(id)).foreach { c => c.cancel = true; c.thread.interrupt() }
          Right(r)
        case Some(s @ (RunState.Queued | RunState.Paused | RunState.WaitingApproval)) =>
          transition(id, s, RunState.Cancelled, error = Some(Problem.Run.Cancelled(Problem.Run.Actor.Person)))
            .toRight(conflict(Problem.Run.Stopped(Problem.Run.Stop.MovedOn(Problem.Raw.of(id)))))
            .flatMap { _ =>
              val withdrawn = agents.approvalStore.closeForRun(
                Job.Id.assume(id.value),
                Problem.Run.Stopped(Problem.Run.Stop.Withdrawn)
              )
              if withdrawn != RowCount(0) then
                scribe.info(s"agent run $id cancelled: $withdrawn open approval(s) withdrawn")
              get(id)
            }
        case _ =>
          Left(conflict(Problem.Run.NotNow(Problem.Raw.of(id), Problem.Raw.of(r.status), Problem.Run.Control.Cancel)))
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def get(id: Run.Id): Either[Api.Error, RunInfo] =
    agents.runs
      .get(id)
      .toRight(
        Api.Error(
          Problem.NotFound.Entity(Problem.TypeName.of(Run.Id), Problem.Raw(s"$id")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )

  private def withRun[A](id: Run.Id)(f: RunInfo => Either[Api.Error, A]): Either[Api.Error, A] = get(id).flatMap(f)

/** The trace of one LLM step, from the conversation sttp-ai's loop built: prompts, assistant turns, tool calls with
  * their results — every body truncated to `max` characters.
  */
object Engine:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]

  final case class Paused(
      run: Run.Id,
      pipeline: RunPipeline.Wire,
      cause: Option[Problem],
      resumes: Option[java.time.Instant],
      actor: Actor
  )

  /** The time between two restores, as an engine sees it: a new one begins with each restore, told apart by identity.
    */
  final class Era
  type OutputCapLimit = OutputCapLimit.T
  object OutputCapLimit extends Newtype[Int, Pure]
  type Stall = Stall.T
  object Stall extends Newtype[String, Pure]

  /** How long a run waits for a slot before it beats again (waiting behind other runs is not a stall). */

  type PruneTraceForce = PruneTraceForce.T
  object PruneTraceForce extends Newtype[Boolean, Pure]
  type BeatSeq = BeatSeq.T
  object BeatSeq extends Newtype[Int, Pure]
  type CapKeyOverridden = CapKeyOverridden.T
  object CapKeyOverridden extends Newtype[Boolean, Pure]

  val BeatWhileWaitingSeconds = 30L

  /** The warning a stall leaves on its run, `<mark> <seq> (…)`; counting them per step decides the fail. */
  val StallMark = "stalled at step"

  /** The tool-loop turns of one step: its agent's `max-turns`, never the global `agents.step.max-iterations` (which
    * once capped a reviewer meant for 20 lookups at 6).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stepTurns(agent: AgentDef): AgentDef.MaxTurns = AgentDef.MaxTurns(math.max(1, agent.maxTurns.value))

  /** [[stepTurns]], unless the pipeline sized this one call itself (`review-article`'s fixer gets more turns the more
    * findings it has to settle).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stepTurns(agent: AgentDef, overridden: Option[AgentDef.MaxTurns]): AgentDef.MaxTurns =
    overridden.fold(stepTurns(agent))(t => AgentDef.MaxTurns(math.max(1, t.value)))

  /** The note of a blocking approval a dry run did not open (it goes on as if approved). */
  val DryRunApproval = "dry run: not recorded; treated as approved"

  /** The setting that sets a step's output cap: `agents.step.max-tokens`, unless the pipeline passed the step its own
    * cap (the fixer's and the writer's).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ceiling(overridden: CapKeyOverridden): Problem.Run.Ceiling =
    if overridden.value then Problem.Run.Ceiling.StepOwn else Problem.Run.Ceiling.StepMaxTokens

  private val OutputCap = "exceeded the (\\d+) output token maximum".r

  /** #1450: the output cap a provider says the answer ran into — Claude Code's "Claude's response exceeded the 4096
    * output token maximum", in its error result or the failure's text. The CLI has already retried the call itself by
    * then (a failed cold-reader attempt recorded 16384 output tokens: four 4096-token tries), and the same cap fails
    * the same way again, so this is a CONFIGURATION error: the step stops at once rather than spending its retries.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def outputCapHit[Out](res: AgentResult[Either[AgentFailure, Out]]): Option[OutputCapLimit] =
    val texts = (res.finishReason match
      case FinishReason.Error(m) => List(m)
      case _                     => Nil
    ) ++ res.finalAnswer.left.toOption.map(_.rawAnswer).toList
    texts.iterator
      .flatMap(t => OutputCap.findFirstMatchIn(t))
      .flatMap(_.group(1).toIntOption)
      .map(OutputCapLimit(_))
      .nextOption()

  /** The raw text of an answer that was JSON but not the typed answer — what a retry hands back to be amended. None for
    * no answer at all, or a decoded one.
    */
  def undecoded(a: Either[AgentFailure, ?]): Option[String] =
    val raw = a match
      case Left(AgentParseError(r, _))          => Some(r)
      case Left(AgentIncomplete(r, _, Some(_))) => Some(r) // an HTTP loop that gave up on undecodable answers
      case _                                    => None
    raw.map(_.trim).filter(_.nonEmpty)

  /** How much of the raw answer a decode problem quotes. */
  val AnswerExcerpt = 300

  /** #941: why an answer is not the step's typed answer, for the step's record and the retry prompt: the decoder's own
    * error — WHERE in the answer (the JSON path) and WHAT (missing field, wrong type) — then the start of the answer;
    * or, for no answer at all, how the call ended.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def answerProblem(f: AgentFailure, finish: FinishReason): Problem =
    def began(raw: String): Option[External.ModelSaid] =
      Option(raw.trim).filter(_.nonEmpty).map(r => External.ModelSaid(r.take(AnswerExcerpt)))
    def why(cause: Throwable): Problem.Content.Decoding =
      SttpAiJson
        .decodingFailure(cause)
        .getOrElse(
          Problem.Content.Decoding.Unreadable(
            External.Reported.of(Option(cause.getMessage).getOrElse(cause.getClass.getSimpleName))
          )
        )
    def ended(r: FinishReason): Option[External.Reported] = Some(External.Reported.of(LlmClient.finish(r)))
    f match
      case AgentParseError(raw, cause) => Problem.Content.Undecoded(why(cause), None, began(raw))
      // sttp-ai's own loop re-asks after an undecodable answer and, out of turns, keeps the last error
      case AgentIncomplete(raw, fin, Some(cause)) => Problem.Content.Undecoded(why(cause), ended(fin), began(raw))
      case other => Problem.Content.Undecoded(Problem.Content.Decoding.NotSchema, ended(finish), began(other.rawAnswer))
