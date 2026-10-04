package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{Origin, RunRequest, RunState}
import com.histmersion.core.GoalPlans.GoalSchedule
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.core.Scheduling.Decision
import com.histmersion.db.{AgentRepo, GoalLinkRepo, QueueRepo}

import java.time.Instant
import javax.sql.DataSource

/** The goal schedules (docs/research-embedded-agents.md §14): every `agents.scheduler.tick-seconds`
  * the due schedules are looked at, and each one either starts its pipeline (a currency check or an
  * acquisition sweep of its goal, origin `scheduler`), is skipped to its next time (its previous run
  * has not settled, or today's or this month's spend reached the cap — checked BEFORE the start), or
  * is deferred to the next tick (`max-concurrent-runs` runs are already queued or running).
  * Notifications are log lines (a skip, a start that was refused) and backlog items (a scheduled run
  * that fails or pauses — see [[Engine]]); no external service.
  *
  * Off unless agents are enabled, `agents.scheduler.enabled` is on, and the schedule itself is
  * enabled. [[tick]] is the whole of one pass, callable directly (tests drive it with a fixed clock). */
final class Scheduler(ds: DataSource, cfg: AgentsConfig, agents: AgentsHandlers, h: com.histmersion.server.Handlers):

  /** The ticking thread; a `var` because it starts after construction and stops in tests. */
  @volatile private var thread: Option[Thread] = None
  @volatile private var stopping = false

  def start(): Unit =
    if cfg.enabled && cfg.scheduler.enabled && thread.isEmpty then
      stopping = false
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agent-scheduler").unstarted(() => loop())
      thread = Some(t)
      t.start()

  def stop(): Unit =
    stopping = true
    thread.foreach(_.interrupt())
    thread = None

  private def loop(): Unit =
    val every = math.max(5, cfg.scheduler.tickSeconds).toLong * 1000L
    try
      while !stopping do
        try { val _ = tick(Instant.now()) }
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent scheduler tick failed: ${e.getMessage}")
        Thread.sleep(every)
    catch case _: InterruptedException => ()

  /** One pass over the due schedules at `now`. Returns what was done to each, for the caller (a test)
    * to inspect. Never throws for one schedule's sake: a refused start is a note and a log line. */
  def tick(now: Instant): List[(GoalSchedule, String)] =
    if !cfg.enabled || !cfg.scheduler.enabled then Nil
    else GoalLinkRepo.due(ds, now).map(s => s -> fire(s, now))

  private def fire(s: GoalSchedule, now: Instant): String =
    val next     = Cron.parse(s.cron).toOption.flatMap(_.next(now))
    val previous = s.lastRun.flatMap(AgentRepo.get(ds, _)).flatMap(r => RunState.parse(r.status))
    val (day, month) = AgentRepo.spendDayMonth(ds)
    Scheduling.decide(previous, day, month, cfg.budgets.dailyUsd, cfg.budgets.monthlyUsd, AgentRepo.active(ds),
      cfg.maxConcurrentRuns) match
      case Decision.Defer(why) =>
        val note = s"deferred: $why"
        GoalLinkRepo.ticked(ds, s.goal, s.kind, note, None, None, advance = false)
        note
      case Decision.Skip(why) =>
        val note = s"skipped: $why"
        GoalLinkRepo.ticked(ds, s.goal, s.kind, note, None, next, advance = true)
        log(s"schedule ${s.kind.wire} of goal ${s.goal}: $note")
        note
      case Decision.Start =>
        val budget = Scheduling.budget(s.budgetUsd, GoalLinkRepo.planAndBudget(ds, s.goal).flatMap(_._2).map(_.toDouble),
          cfg.budgets.runUsd)
        agents.startRunAs(RunRequest(s.kind.pipeline.wire, s.goal, budgetUsd = Some(budget)), Origin.Scheduler, None) match
          case Right(run) =>
            val note = s"started run ${run.id.take(8)}"
            GoalLinkRepo.ticked(ds, s.goal, s.kind, note, Some(run.id), next, advance = true)
            note
          case Left(e) =>
            val note = s"not started: ${e.message}"
            GoalLinkRepo.ticked(ds, s.goal, s.kind, note.take(500), None, next, advance = true)
            val _ = QueueRepo.add(ds, Queue.Kind.Tasks, QueueAdd(
              s"schedule ${s.kind.wire} of goal ${s.goal}: $note".take(1000), topic = Some("agent schedules")))
            note

  /** A log line joins today's entry, as every other one does. */
  private def log(line: String): Unit =
    val _ = h.ops.queueAdd("log", QueueAdd(line))
