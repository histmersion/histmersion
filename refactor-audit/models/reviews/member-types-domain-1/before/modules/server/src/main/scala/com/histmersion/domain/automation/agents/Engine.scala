package com.histmersion.domain.automation.agents

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
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, Semaphore}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*
import com.histmersion.api.automation.{AgentsApi, AgentsMarkdown, PlanQuota}
import com.histmersion.api.Api

/** The run pool: `agents.max-concurrent-runs` workers draining a queue of run ids, on one daemon thread hosting an Ox
  * scope (the same arrangement as the OCR [[com.histmersion.server.Jobs]]). Runs are durable — `jobs` + `agent_steps` —
  * so the in-memory queue is rebuilt at [[start]].
  */
final class Engine(
    val ds: DataSource,
    cfgNow: => AgentsConfig,
    val agents: AgentsControllers,
    val workflows: Workflows
):

  /** Read at each use (a config reload switches it); a run reads it once, into its [[RunCtx]]. */
  def cfg: AgentsConfig = cfgNow

  /** A queued run and the generation it was queued on: it runs there — on the database it was queued in, even after a
    * config reload swapped the database (docs/research-plugins.md §3.6).
    */
  final private case class Queued(id: Queued.Id, work: com.histmersion.util.jobs.Carried[Unit])

  object Queued:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  private val queue = LinkedBlockingQueue[Queued]()
  val llmSlots: Semaphore = Semaphore(math.max(1, cfg.maxLlmCallsInFlight))

  /** The tools a step of `run` offers its agent: the agent's own, as far as its workflow lets the agent's role use them
    * (a run of no registered workflow offers none).
    */
  def offered(run: RunInfo, agent: AgentDef): List[ToolName] =
    workflows
      .parse(run.pipeline)
      .fold(Nil)(w =>
        agent.tools.filter(w.allowedTools(agent.role.transformInto[com.histmersion.domain.automation.AgentRole]))
      )

  /** When the trace was last pruned (epoch ms; 0 = not yet in this process). */
  private val lastPrune = java.util.concurrent.atomic.AtomicLong(0L)

  /** Delete traced messages older than `agents.trace.keep-messages-days`, at most once every `prune-every-minutes` (or
    * now with `force`). Returns how many went. Never fails a run: a failed prune is logged and tried again next time.
    */
  def pruneTrace(force: Boolean = false, now: Long = agents.clock.millis()): Int =
    val every = math.max(1, cfg.trace.pruneEveryMinutes).toLong * 60000L
    val last = lastPrune.get()
    if cfg.trace.keepMessagesDays <= 0 || (!force && now - last < every) || !lastPrune.compareAndSet(last, now) then 0
    else
      try
        val n = agents.runs.pruneMessages(cfg.trace.keepMessagesDays)
        if n > 0 then scribe.info(s"agent trace: pruned $n message(s) older than ${cfg.trace.keepMessagesDays} day(s)")
        n
      catch
        case e: Exception =>
          scribe.warn(s"agent trace pruning failed: ${e.getMessage}")
          0

  /** What a person asked of a RUNNING run, read by the run between steps — and what the stall watchdog watches: the
    * last sign of life, the step it was in, and the CLI processes it has alive.
    */
  final private class Control(val thread: Thread):
    @volatile var pause: Boolean = false
    @volatile var cancel: Boolean = false
    @volatile var lastBeat: Long = agents.clock.millis()

    /** When `lastBeat` was last written to `jobs.heartbeat_at` (at most every heartbeat-seconds). */
    @volatile var persisted: Long = 0L
    @volatile var seq: Int = 0

    /** Set by the watchdog before it interrupts the run: how the run settles. */
    @volatile var stalled: Option[Halt] = None
    val procs: java.util.Set[Process] = ConcurrentHashMap.newKeySet[Process]()
  private val running = ConcurrentHashMap[String, Control]()

  /** A sign of life of run `id` (a step boundary, a model call, a CLI line); `seq` = the step it is in. */
  private[histmersion] def beat(id: String, seq: Option[Int] = None): Unit =
    Option(running.get(id)).foreach { c =>
      val now = agents.clock.millis()
      c.lastBeat = now
      seq.foreach(c.seq = _)
      if now - c.persisted >= math.max(1, cfg.watchdog.heartbeatSeconds).toLong * 1000L then
        c.persisted = now
        try agents.jobRuns.heartbeat(JobKind, id)
        catch case e: Exception => scribe.warn(s"agent run $id: heartbeat not written: ${e.getMessage}")
    }

  /** Take a plan pool's slot, beating while waiting for it (other runs' calls hold it). */
  private[histmersion] def acquireBeating(id: String, slot: Semaphore): Unit =
    while !slot.tryAcquire(Engine.BeatWhileWaitingSeconds, java.util.concurrent.TimeUnit.SECONDS) do beat(id)

  /** Called with every run that settles (done, failed, cancelled, paused, waiting) — the managers' nudge. A `var`
    * because the supervisor is attached after construction.
    */
  @volatile private var settledHooks: List[RunInfo => Unit] = Nil

  def onSettled(f: RunInfo => Unit): Unit = settledHooks = settledHooks :+ f

  /** The stall watchdog's pass at `now` (epoch ms): every running run whose last sign of life is older than
    * `agents.watchdog.stall-minutes` has its processes killed and is interrupted; it settles paused `stalled` (resuming
    * after resume-minutes), or failed on its max-stalls-th stall of one step. Returns the ids.
    */
  def checkStalls(now: Long = agents.clock.millis()): List[String] =
    val limit = cfg.watchdog.stallMinutesFor(cfg.providers).toLong * 60000L
    running.asScala.toList.filter((_, c) => c.stalled.isEmpty && !c.cancel && now - c.lastBeat > limit).map { (id, c) =>
      val mins = (now - c.lastBeat) / 60000L
      val mark = s"${Engine.StallMark} ${c.seq}"
      val prior = agents.runs.get(id).toList.flatMap(_.warnings).count(_.startsWith(mark + " "))
      val n = prior + 1
      agents.jobRuns.warn(
        JobKind,
        id,
        s"$mark (${n}x): no sign of life for $mins min; its processes were killed".take(500)
      )
      c.stalled = Some(if n >= math.max(1, cfg.watchdog.maxStalls) then
        Halt.Fail(s"stalled $n times at step ${c.seq} (no sign of life for $mins min each time; agents.watchdog)")
      else
        Halt.Pause(
          PauseReason.Stalled,
          s"no sign of life for $mins min at step ${c.seq} (stall $n of ${cfg.watchdog.maxStalls})",
          Some(java.time.Instant.ofEpochMilli(now).plusSeconds(math.max(0, cfg.watchdog.resumeMinutes).toLong * 60L))
        ))
      scribe.warn(s"agent run $id stalled at step ${c.seq} (no sign of life for $mins min); killing its processes")
      c.procs.asScala.toList.foreach(p =>
        try CliProcess.kill(p)
        catch case _: Exception => ()
      )
      c.thread.interrupt()
      id
    }

  /** The scope thread; a `var` because the pool starts after construction (no side effects in initialisers) and stops
    * in tests.
    */
  private var scope: Option[Thread] = None

  /** Reconcile (an interrupted run is paused, not failed), re-enqueue queued runs, start the workers. Idempotent.
    * Returns how many runs were paused as interrupted.
    */
  def start(): Int =
    val (paused, queued) = reconcile()
    val _ = pruneTrace(force = true)
    queued.foreach(submit)
    if scope.isEmpty then
      stopping = false
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agents").unstarted(() => pool())
      scope = Some(t)
      t.start()
      val w = Thread.ofPlatform().daemon(true).name("histmersion-agents-resume").unstarted(() => resumer())
      waker = Some(w)
      w.start()
    paused

  /** [[start]]'s first half: a run left `running` is paused as interrupted (and, when nobody watches it, set to resume
    * by itself shortly). Returns how many were paused, and the queued runs.
    */
  private[histmersion] def reconcile(): (Int, Vector[String]) =
    // a run this engine is running is not interrupted, and one it holds queued is not queued twice: a
    // config reload that swapped back to its database must leave them be (docs/research-plugins.md §3.6)
    import scala.jdk.CollectionConverters.*
    val inQueue = queue.asScala.map(_.id).toSet
    val (paused, queuedAll) = agents.jobRuns.reconcile(
      JobKind,
      RunState.Running.wire,
      RunState.Paused.wire,
      PauseReason.Interrupted.wire,
      RunState.Queued.wire,
      running.keySet.asScala.toList
    )
    val queued = queuedAll.filterNot(inQueue)
    // a run nobody is watching goes on by itself after a restart; a person's waits for them
    val at = java.time.Instant.now(agents.clock).plusSeconds(math.max(0, cfg.watchdog.interruptedResumeMinutes).toLong * 60L)
    paused.foreach { (id, origin) =>
      if origin.flatMap(Origin.parse).exists(_.unattended) then
        val _ = agents.jobRuns.resumeAt(JobKind, id, RunState.Paused.wire, at)
    }
    (paused.size, queued)

  /** The thread that resumes runs paused for an allowance; a `var` for the same reason as [[scope]]. */
  private var waker: Option[Thread] = None

  private def resumer(): Unit =
    // the stall check shares this loop: at least once a minute
    val every = math.min(60, math.max(5, cfg.allowanceCheckSeconds)).toLong * 1000L
    try
      while !stopping do
        Thread.sleep(every)
        try
          agents.pins.pinned {
            val now = java.time.Instant.now(agents.clock)
            val _ = checkStalls(now.toEpochMilli)
            val _ = resumeDue(now)
            val _ = resumeFreed(now)
          }
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent resume check failed: ${e.getMessage}")
    catch case _: InterruptedException => ()

  /** Re-queue every run paused for an allowance whose window has freed (`resume_at` ≤ now). Returns the ids. When the
    * window has not freed enough after all (another run spent it meanwhile), the run simply pauses again with the next
    * time.
    */
  def resumeDue(now: java.time.Instant): List[String] =
    agents.jobRuns.due(JobKind, RunState.Paused.wire, now).toList.filter { id =>
      transition(id, RunState.Paused, RunState.Queued).map(_ => submit(id)).isDefined
    }

  /** Re-queue every run paused on a plan pool (`plan-quota`) that would get a model NOW, before the reset it waits for:
    * its role is picked again — which takes a fresh reading of a pool whose verdict hangs only on a stale one (at most
    * once per `stale-minutes` per pool), and sees a hold released. A run whose pool is still below its floor stays
    * paused; nothing is read for a server with no such run. Returns the ids.
    */
  def resumeFreed(now: java.time.Instant): List[String] =
    agents.jobRuns.pausedWith(JobKind, RunState.Paused.wire, s"${PauseReason.PlanQuota.wire}:", now).toList.flatMap {
      (id, pipeline, model, error) =>
        val roles = ModelPicker
          .pausedRole(error)
          .map(List(_))
          .getOrElse(Pipeline.parse(pipeline).map(_.roles).getOrElse(Nil))
        val notes = List.newBuilder[String]
        // the run's own model for the role: its --model-for, else its --model
        val run = agents.runs.get(id)
        def modelOf(r: Role) = run.fold(model)(_.modelFor(r))
        val free = roles.find(r =>
          agents.picker
            .pick(
              r,
              modelOf(r),
              now.toEpochMilli,
              Some(PlanQuota.RefreshTrigger.Paused),
              n => notes += s"${r.wire}: $n"
            )
            .isRight
        )
        free.flatMap { r =>
          transition(id, RunState.Paused, RunState.Queued).map { _ =>
            (notes.result() :+ s"resumed before its reset: the ${r.wire} chain has a usable model again")
              .foreach(w => agents.jobRuns.warn(JobKind, id, w.take(500)))
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
  def transition(
      id: String,
      from: RunState,
      to: RunState,
      error: Option[String] = None,
      progress: Option[String] = None,
      resumeAt: Option[java.time.Instant] = None
  ): Option[RunState] =
    RunState
      .next(from, to)
      .flatMap(target =>
        Option.when(agents.jobRuns.move(JobKind, id, from.wire, target.wire, error, progress, resumeAt))(target)
      )

  def submit(id: String): Unit = queue.put(Queued(id, agents.pins.carry(execute(id))))

  private def pool(): Unit =
    try
      ox.supervised {
        (1 to math.max(1, cfg.maxConcurrentRuns)).foreach { _ =>
          val _ = ox.forkUser(worker())
        }
        ()
      }
    catch
      case _: InterruptedException => ()
      case e: Throwable            => scribe.error(s"agent pool died: ${e.getMessage}", e)

  private def worker(): Unit =
    var alive = true // the loop's exit flag; interruption of the pool clears it
    while alive && !stopping do
      try
        val q = queue.take()
        Thread.interrupted() // a cancel aimed at the previous run must not leak into this one
        if !stopping then
          q.work.run()
          val _ = pruneTrace()
        else q.work.drop()
      catch case _: InterruptedException => alive = false

  /** Run one queued run to its next resting state. */
  private[histmersion] def execute(id: String): Unit =
    agents.runs.get(id) match
      case None      => ()
      case Some(run) =>
        val pipe = workflows.parse(run.pipeline)
        val sameVersion = pipe.forall(w => agents.jobRuns.pipelineVersion(id).forall(_ == w.version))
        if transition(id, RunState.Queued, RunState.Running).isDefined then
          val ctl = Control(Thread.currentThread())
          running.put(id, ctl)
          val outcome: Either[Halt, String] =
            try
              pipe match
                case None                    => Left(Halt.Fail(s"no pipeline '${run.pipeline}' in this build"))
                case Some(_) if !sameVersion =>
                  Left(Halt.Fail("the pipeline's version changed since this run started; start a new run"))
                case Some(p) =>
                  val hooks = CliProcess.Hooks(
                    pr => { val _ = ctl.procs.add(pr) },
                    pr => { val _ = ctl.procs.remove(pr) },
                    () => beat(id)
                  )
                  CliProcess.withHooks(hooks)(p.run(RunCtx(run.modify(_.status).setTo(RunState.Running.wire), this)))
            catch
              case _: InterruptedException if ctl.cancel            => Left(Halt.Cancel)
              case _: InterruptedException if ctl.stalled.isDefined => Left(ctl.stalled.get)
              case e: InterruptedException                          =>
                // the pool stopping under a run nobody watches: it goes on after the restart by itself
                val at = Option.when(run.origin.flatMap(Origin.parse).exists(_.unattended))(
                  java.time.Instant.now(agents.clock).plusSeconds(math.max(0, cfg.watchdog.interruptedResumeMinutes).toLong * 60L)
                )
                Left(Halt.Pause(PauseReason.Interrupted, "interrupted", at))
              case e: Exception =>
                scribe.error(s"agent run $id failed unexpectedly: ${e.getMessage}", e)
                Left(Halt.Fail(s"${e.getClass.getSimpleName}: ${e.getMessage}"))
            finally
              running.remove(id)
              Thread.interrupted()
          // a cancel or a stall decides how the run settles, whatever the interrupted step made of it
          val settled = if ctl.cancel then Left(Halt.Cancel) else ctl.stalled.map(Left(_)).getOrElse(outcome)
          val _ = settled match
            case Right(summary) =>
              transition(id, RunState.Running, RunState.Done, progress = Some(summary.take(500)))
            case Left(Halt.Cancel) =>
              transition(id, RunState.Running, RunState.Cancelled, error = Some("cancelled"))
            case Left(Halt.Fail(m)) =>
              transition(id, RunState.Running, RunState.Failed, error = Some(m.take(2000)))
            case Left(Halt.Pause(reason, why, at)) =>
              transition(
                id,
                RunState.Running,
                RunState.Paused,
                error = Some(s"${reason.wire}: ${why.take(1000)}"),
                resumeAt = at
              )
            case Left(Halt.Wait(approval, why)) =>
              val waiting =
                transition(id, RunState.Running, RunState.WaitingApproval, progress = Some(why.take(500)))
              // decided in the instant between opening it and this transition: go straight back to the queue
              if waiting.isDefined && agents.approvalStore.approval(approval).exists(_.state != ApprovalState.Open.wire)
              then
                val _ = wake(id)
              waiting
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
  private def notify(run: RunInfo, settled: Either[Halt, String]): Unit =
    val scheduled = run.origin.contains(Origin.Scheduler.wire)
    val ingested = run.origin.contains(Origin.FromIngest.wire)
    // a dry run writes nothing, its backlog item included
    if (scheduled || ingested) && !run.dryRun then
      val why = settled match
        case Left(Halt.Fail(m)) => Some(s"failed: ${m.take(300)}")
        case Left(Halt.Pause(r, _, _)) if ingested && (r == PauseReason.Allowance || r == PauseReason.PlanQuota) => None
        case Left(Halt.Pause(r, d, _)) => Some(s"paused (${r.wire}): ${d.take(300)}")
        case _                         => None
      why.foreach { w =>
        val text =
          if scheduled then
            s"scheduled ${run.pipeline}${run.goal.fold("")(g => s" for goal $g")} (agent run ${run.id.take(8)}) $w"
          else s"${run.pipeline} of [[${run.target}]] queued by its import (agent run ${run.id.take(8)}) $w"
        val _ = agents.queues.add(
          com.histmersion.api.automation.Queue.Kind.Tasks,
          com.histmersion.api.automation.Queue.QueueAdd(
            text,
            topic = Some(if scheduled then "agent schedules" else "auto-enrich"),
            goal = Pipelines.goalOf(run),
            job = Some(run.id)
          )
        )
      }

  /** A waiting run whose approval was decided goes back to the queue. `false` = it was not waiting. */
  def wake(id: String): Boolean =
    transition(id, RunState.WaitingApproval, RunState.Queued).map(_ => submit(id)).isDefined

  /** Between steps: a requested pause or cancel takes effect here. */
  private[histmersion] def checkControl(id: String): Either[Halt, Unit] =
    Option(running.get(id)) match
      case Some(c) if c.cancel => Left(Halt.Cancel)
      case Some(c) if c.pause  => Left(Halt.Pause(PauseReason.Requested, "paused on request"))
      case _                   => Right(())

  /** Before an LLM call: the run's own budget, and the global daily and monthly caps. */
  private[histmersion] def checkBudget(id: String): Either[Halt, Unit] =
    agents.runs.get(id) match
      case None    => Left(Halt.Fail("the run disappeared"))
      case Some(r) =>
        val (day, month) = agents.runs.spendDayMonth()
        if r.costUsd >= r.budgetUsd then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              s"the run spent ${AgentsMarkdown.usd(r.costUsd)} of its ${AgentsMarkdown.usd(r.budgetUsd)} USD"
            )
          )
        else if r.usage.budgeted(cfg.budgets.cacheReadWeight) >= r.budgetTokens then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              s"the run used ${r.usage.budgeted(cfg.budgets.cacheReadWeight)} of its ${r.budgetTokens} budget tokens " +
                s"(cache reads count ${cfg.budgets.cacheReadWeight}: agents.budgets.cache-read-weight)"
            )
          )
        else if day >= BigDecimal(cfg.budgets.dailyUsd) then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              s"today's agent spend reached agents.budgets.daily-usd (${cfg.budgets.dailyUsd})"
            )
          )
        else if month >= BigDecimal(cfg.budgets.monthlyUsd) then
          Left(
            Halt.Pause(
              PauseReason.Budget,
              s"this month's agent spend reached agents.budgets.monthly-usd (${cfg.budgets.monthlyUsd})"
            )
          )
        else Right(())

  private[histmersion] def budgetInterceptor(id: String, billing: Billing): RunBudget =
    val r = agents.runs.get(id)
    val (use, cost) = agents.runs.runSpend(id)
    RunBudget(
      cost,
      use.budgeted(cfg.budgets.cacheReadWeight),
      r.map(x => BigDecimal(x.budgetUsd)).getOrElse(BigDecimal(0)),
      r.map(_.budgetTokens).getOrElse(0L),
      billing,
      cfg.budgets.cacheReadWeight
    )

  // ── control ────────────────────────────────────────────────────────────────────────────────────

  private def conflict(m: String) = Api.Error(m, Some(AgentsApi.CodeConflict))

  def pause(id: String): Either[Api.Error, RunInfo] =
    withRun(id) { r =>
      RunState.parse(r.status) match
        case Some(RunState.Running) =>
          Option(running.get(id)).foreach(_.pause = true)
          Right(r)
        case Some(RunState.Queued) =>
          transition(
            id,
            RunState.Queued,
            RunState.Paused,
            error = Some(s"${PauseReason.Requested.wire}: paused before it started")
          )
            .toRight(conflict(s"run $id moved on; look again"))
            .flatMap(_ => get(id))
        case _ => Left(conflict(s"run $id is ${r.status}; only a queued or running run can be paused"))
    }

  def resume(id: String, budgetUsd: Option[Double], budgetTokens: Option[Long]): Either[Api.Error, RunInfo] =
    withRun(id) { r =>
      if !RunState.parse(r.status).contains(RunState.Paused) then
        Left(conflict(s"run $id is ${r.status}; only a paused run can be resumed"))
      else
        agents.jobRuns.budget(JobKind, id, budgetUsd.map(BigDecimal(_)), budgetTokens)
        transition(id, RunState.Paused, RunState.Queued)
          .toRight(conflict(s"run $id moved on; look again"))
          .flatMap { _ => submit(id); get(id) }
    }

  def cancel(id: String): Either[Api.Error, RunInfo] =
    withRun(id) { r =>
      RunState.parse(r.status) match
        case Some(RunState.Running) =>
          Option(running.get(id)).foreach { c => c.cancel = true; c.thread.interrupt() }
          Right(r)
        case Some(s @ (RunState.Queued | RunState.Paused | RunState.WaitingApproval)) =>
          transition(id, s, RunState.Cancelled, error = Some("cancelled"))
            .toRight(conflict(s"run $id moved on; look again"))
            .flatMap { _ =>
              val _ = agents.approvalStore.closeForRun(id, "rejected: the run was cancelled")
              get(id)
            }
        case _ => Left(conflict(s"run $id is ${r.status}; it has already finished"))
    }

  private def get(id: String): Either[Api.Error, RunInfo] =
    agents.runs.get(id).toRight(Api.Error(s"No agent run '$id'", Some(Write.CodeNotFound)))

  private def withRun[A](id: String)(f: RunInfo => Either[Api.Error, A]): Either[Api.Error, A] = get(id).flatMap(f)

/** The trace of one LLM step, from the conversation sttp-ai's loop built: prompts, assistant turns, tool calls with
  * their results — every body truncated to `max` characters.
  */
object Engine:
  /** How long a run waits for a slot before it beats again (waiting behind other runs is not a stall). */
  val BeatWhileWaitingSeconds = 30L

  /** The warning a stall leaves on its run, `<mark> <seq> (…)`; counting them per step decides the fail. */
  val StallMark = "stalled at step"

  /** The tool-loop turns of one step: its agent's `max-turns`, never the global `agents.step.max-iterations` (which
    * once capped a reviewer meant for 20 lookups at 6).
    */
  def stepTurns(agent: AgentDef): Int = math.max(1, agent.maxTurns)

  /** [[stepTurns]], unless the pipeline sized this one call itself (`review-article`'s fixer gets more turns the more
    * findings it has to settle).
    */
  def stepTurns(agent: AgentDef, overridden: Option[Int]): Int = overridden.fold(stepTurns(agent))(math.max(1, _))

  /** The note of a blocking approval a dry run did not open (it goes on as if approved). */
  val DryRunApproval = "dry run: not recorded; treated as approved"

  /** The config key that sets a step's output cap: `agents.step.max-tokens`, unless the pipeline passed the step its
    * own cap (the fixer's and the writer's).
    */
  def capKey(overridden: Boolean): String =
    if overridden then "the step's own cap (agents.review.fixer-max-tokens or agents.compile.draft-max-tokens)"
    else "agents.step.max-tokens (HISTMERSION_AGENTS_MAX_TOKENS)"

  private val OutputCap = "exceeded the (\\d+) output token maximum".r

  /** #1450: the output cap a provider says the answer ran into — Claude Code's "Claude's response exceeded the 4096
    * output token maximum", in its error result or the failure's text. The CLI has already retried the call itself by
    * then (a failed cold-reader attempt recorded 16384 output tokens: four 4096-token tries), and the same cap fails
    * the same way again, so this is a CONFIGURATION error: the step stops at once rather than spending its retries.
    */
  def outputCapHit[Out](res: AgentResult[Either[AgentFailure, Out]]): Option[Int] =
    val texts = (res.finishReason match
      case FinishReason.Error(m) => List(m)
      case _                     => Nil
    ) ++ res.finalAnswer.left.toOption.map(_.rawAnswer).toList
    texts.iterator.flatMap(t => OutputCap.findFirstMatchIn(t)).flatMap(_.group(1).toIntOption).nextOption()

  /** What the run says when [[outputCapHit]]: the cap, the model, the key to raise. */
  def outputCapMessage(name: String, cap: Int, sent: Int, model: String, key: String): String =
    s"$name: $model's answer exceeded the $cap output-token cap (the step sent $sent) — raise $key " +
      "(a thinking model's reasoning counts toward it), restart the server, then resume the run"

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
  def answerProblem(f: AgentFailure, finish: FinishReason): String =
    def excerpt(raw: String) =
      val r = raw.trim
      if r.isEmpty then "" else s" — the answer began: ${r.take(AnswerExcerpt)}"
    def why(cause: Throwable): String = cause match
      case d: io.circe.DecodingFailure =>
        val path = io.circe.CursorOp.opsToPath(d.history)
        s"the answer does not match the schema at ${if path.isEmpty then "the top level" else path}: ${d.message}"
      case p: io.circe.ParsingFailure => s"the answer is not valid JSON: ${p.message}"
      case e => s"the answer could not be decoded: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}"
    f match
      case AgentParseError(raw, cause) => why(cause) + excerpt(raw)
      // sttp-ai's own loop re-asks after an undecodable answer and, out of turns, keeps the last error
      case AgentIncomplete(raw, fin, Some(cause)) => s"${why(cause)} (${LlmClient.finish(fin)})${excerpt(raw)}"
      case other                                  =>
        s"the answer is not the JSON the schema asks for (${LlmClient.finish(finish)})${excerpt(other.rawAnswer)}"
