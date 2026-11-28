package com.histmersion
package domain.automation.managers
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.Transactor

import com.augustnagro.magnum.connect

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.goals.GoalFocusService
import com.histmersion.domain.automation.scheduler.SchedulerService

import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.ManagerPolicy.Next
import com.histmersion.domain.ManagerPolicy.ReviewSettings
import com.histmersion.domain.ManagerPolicy.StepFacts
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.QueueAdd

import java.time.Instant
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import javax.sql.DataSource
import com.histmersion.api.automation.{Agents, Queue}
import com.histmersion.domain.automation.agents.Halt.Stopped
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.managers.ManagerContext.TickLine
import com.histmersion.domain.automation.{
  Approval as DomainApproval,
  Goal as DomainGoal,
  Pipeline as DomainPipeline,
  Run as DomainRun
}
import scala.collection.mutable.Builder
import com.histmersion.domain.automation.Manager
import com.histmersion.api.automation.Run
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.util.jobs.Job
import com.histmersion.util.jobs.Quiescent
import com.histmersion.api.automation.Approval
import com.histmersion.domain.research.Slug
import com.histmersion.domain.ManagerPolicy
import com.histmersion.domain.automation.goals.FocusRepo
import com.histmersion.domain.automation.goals.GoalRow
import com.histmersion.util.jobs.JobRow
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.goals.BGoalLinkRow
import com.histmersion.api.automation.Managers
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The process managers' supervisor (docs/research-process-managers.md §3): a daemon thread that ticks every
  * `agents.managers.tick-seconds`, and at once when a manager's run settles ([[Engine.onSettled]] → [[nudge]]). One
  * [[tick]] — callable directly, with a fixed clock, by a test:
  *
  *   1. evaluate the items whose run is terminal (keyed by the run: never twice);
  *   2. release the holds whose date passed or whose awaited source exists, and settle the items held for an approval a
  *      person decided;
  *   3. fire the crons that are due (a watcher's weekly sweeps, a review manager's refill, a seeding manager's next
  *      proposal) and refill an active review manager below its low water (the selection, with an infer-filter item
  *      before the review of an article that records no filter, and the stale articles last);
  *   4. the CENTRAL SCHEDULER's pass ([[SchedulerService]], #3933): every manager's due items are candidates of one
  *      decision across every job type, by goal priority, job type, aging and the strategy
  *      (`agents.scheduler.strategy`), within the global `agents.max-concurrent-runs` (every queued or running run
  *      counts, a person's too — a run waiting for a person never does), the plan pools' calls in flight, the budgets
  *      and daily caps, a manager's own saved `maxRuns` — never on a target another unsettled run is writing, never an
  *      article's review while its infer-filter item is open, never when a role's chain is starved;
  *   5. the status line, and an event for every change.
  *
  * Between 3 and 4 the autopilot's FOCUS ([[GoalFocusService.tick]]) runs only to release the holds it set before the
  * central scheduler existed: it no longer holds anything.
  *
  * Between 2 and 3 each kind's HOUSEWORK runs ([[ManagerKindDefinition.housework]]): the watch kind's ingest backlog
  * drains and the acquisition list's legal re-check runs — before any new sweep, which gets only what they leave of the
  * allowance.
  *
  * What an item of a kind does — the selection that fills a queue, the run an item starts, what a finished run left,
  * what follows it — is its [[ManagerKindDefinition]]'s, contributed by the domains (`review`, `enrich`, `seed:<goal>`,
  * `watch:<goal>`); the supervisor owns the loop, the holds, the retries, the cron's timing and the settling of
  * approvals, and gives the kinds a [[ManagerContext]].
  *
  * A dispatch is ONE transaction — the run row, the item's compare-and-set to active, the hints it reached, the event —
  * and the run is submitted only after the commit (a crash in between leaves a queued run, which the engine's restart
  * re-enqueues).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class ManagerSupervisor(
    ds: DataSource,
    config: => AgentsConfig,
    agents: AgentsControllers,
    workLists: WorkListControllers,
    kinds: List[ManagerKindDefinition]
) extends ManagerContext:

  def cfg: AgentsConfig = config
  private def mcfg = cfg.managers
  private def managerDefaults = ManagerDefaultsViews.defaults(cfg.managers)

  private given Zero[List[TickLine]] = Zero(Nil)

  /** The ticking thread; a `var` because it starts after construction and stops in tests. */
  @volatile private var thread: Option[Thread] = None
  @volatile private var stopping = false
  private val nudges = LinkedBlockingQueue[String]()

  /** A run of a manager settled: tick soon (coalesced). */
  def nudge(): Unit = { val _ = nudges.offer("settled") }

  /** An event of a manager's feed, its text as assembled. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def recordEvent(
      id: ManagerInfo.Id,
      item: Option[ManagerItem.Id],
      run: Option[DomainRun.Id],
      feed: String,
      text: String
  )(using WritePermit): Unit =
    agents.managerStore.event(id, item, run, ManagerEvent.Kind(feed), ManagerEvent.Text(text))

  /** A run id read back from the database. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def runIdOf(r: RunInfo.Id): DomainRun.Id = DomainRun.Id.assume(r.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def itemKindOf(i: ManagerItem): ManagerContext.ItemKind = ManagerContext.ItemKind(i.kind.value)
  private def itemTargetOf(i: ManagerItem): ManagerContext.ItemTarget =
    i.target.transformInto[ManagerContext.ItemTarget]

  /** The library-wide managers exist — `review` and `enrich` — created PAUSED, so deploying never starts spending until
    * a person resumes them. Idempotent.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ensureDefaults()(using WritePermit)(using Creator.New): Unit =
    Unused(ensure(Kind.Review, ManagerLight.Id(ReviewId), None), Unused.Why.Idempotent)
    Unused(ensure(Kind.Enrich, ManagerLight.Id(EnrichId), None), Unused.Why.Idempotent)

  /** Manager `id` of kind `k` exists (created PAUSED when it did not — active while the autopilot is on, which runs
    * every manager); true = it was created now.
    */
  def ensure(k: Kind, id: ManagerLight.Id, goal: Option[DomainGoal.Slug])(using
      WritePermit
  )(using Creator.New): Boolean =
    val auto = agents.autopilot.on
    val infoId = id.transformInto[ManagerInfo.Id]
    val made = agents.managerStore.create(infoId, k, goal, if auto then State.Active else State.Paused, ManagerConfig())
    if made then
      recordEvent(
        infoId,
        None,
        None,
        if auto then Feed.Autopilot else Feed.Paused,
        if auto then s"created active (the autopilot is on): ${ManagerSupervisor.purpose(k)}"
        else s"created paused: resume it to start ${ManagerSupervisor.purpose(k)} (nothing is spent until then)"
      )
    made

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def start()(using Creator.New): Unit =
    if cfg.enabled.value && mcfg.enabled.value && thread.isEmpty then
      stopping = false
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agent-managers").unstarted(() => loop())
      thread = Some(t)
      t.start()

  /** Whether the ticking thread runs here (agents and managers enabled, the lease held). */
  def running: Boolean = thread.isDefined

  def stop(): Unit =
    stopping = true
    thread.foreach(_.interrupt())
    thread = None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def loop()(using Creator.New): Unit =
    try
      while !stopping do
        hold.synchronized(while holding do hold.wait()) // a restore holds the managers here
        // one tick, one generation: its database and config throughout (a reload moves the next one); one unit of
        // work, fenced off by a restore that happens during it
        ticking.set(true)
        try { val _ = agents.workers.unit(agents.pins.pinned(tick(Instant.now(agents.clock)))) }
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent managers tick failed: ${e.getMessage}", e)
        finally ticking.set(false)
        val _ = nudges.poll(math.max(1, mcfg.tickSeconds.value).toLong, TimeUnit.SECONDS)
        nudges.clear()
    catch case _: InterruptedException => ()

  // ── a restore ──────────────────────────────────────────────────────────────────────────────────

  /** A tick of the loop runs now. */
  private val ticking = java.util.concurrent.atomic.AtomicBoolean(false)

  /** Held while a restore runs: the loop ticks no more. A `var` guarded by `hold`'s monitor, because the loop must
    * BLOCK on it rather than spin.
    */
  private val hold = Object()
  private var holding = false

  /** The managers' loop, as a restore holds it: the tick in flight is waited for, and no other starts. */
  val quiescent: Quiescent = new Quiescent:
    val name: Quiescent.Name = Quiescent.Name("the process managers' loop")
    def quiesce(): Unit = hold.synchronized { holding = true }
    def busy: Vector[Quiescent.Busy] =
      if ticking.get() then Vector(Quiescent.Busy("a tick of the managers")) else Vector.empty

    /** Nothing is kept between ticks but the nudges, which spoke of runs the restore replaced. */
    def restored()(using WritePermit)(using Creator.New): Unit = nudges.clear()
    def resume(): Unit = hold.synchronized { holding = false; hold.notifyAll() }

  /** When the events were last pruned (epoch ms). */
  private val lastPrune = java.util.concurrent.atomic.AtomicLong(0L)

  /** The kinds the supervisor runs, by kind; and the kind that drives each item kind. */
  private val byKind: Map[Kind, ManagerKindDefinition] = kinds.map(k => k.kind -> k).toMap
  private val byItem: Map[ManagerContext.ItemKind, ManagerKindDefinition] =
    kinds.flatMap(k => k.items.map(_ -> k)).toMap

  /** One pass at `now`. Returns what was done, one line each (for a test or a log). Never throws for one item's or one
    * manager's sake.
    */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def tick(now: Instant)(using WritePermit)(using Creator.New): List[TickLine] = synchronized {
    if !cfg.enabled.value || !mcfg.enabled.value then Nil
    else
      val out = List.newBuilder[TickLine]
      out ++= migrateSchedules(now)
      // the autopilot first (its new managers are dispatched in this tick), then the approvals its checks
      // decide (a decided plan settles its held item below)
      out ++= guarded(ManagerLight.Id("autopilot"), out)(agents.autopilot.tick(now)).map(TickLine(_))
      // draining: no approval decided by code (a decision wakes a waiting run: new work)
      if !agents.drain.draining then
        out ++= guarded(ManagerLight.Id("auto-approvals"), out)(agents.autoApprovals.sweep(now)).map(TickLine(_))
      out ++= guarded(ManagerLight.Id("digest"), out)(agents.digest.tick(now)).map(TickLine(_))
      val managers = agents.managerStore.list().filter(m => State.parse(m.state).exists(_ != State.Stopped))
      managers.foreach { m =>
        val mid = m.id.transformInto[ManagerLight.Id]
        guarded(mid, out)(evaluate(m, now, out))
        guarded(mid, out)(releaseHolds(m, now, out))
      }
      // each kind's housework (the watch kind's seeding, ingest backlog and acquisition re-check), before any
      // sweep is dispatched (a sweep gets what it leaves)
      kinds.flatMap(_.housework(now, agents.drain.draining)).foreach((name, work) => out ++= guarded(name, out)(work()))
      // the ones that may start runs, refilled, then dispatched round-robin over the global room
      val live = agents.managerStore
        .list()
        .filter(m =>
          State.parse(m.state).contains(State.Active) &&
            Kind.parse(m.kind).exists(byKind.contains)
        )
      live.foreach { m =>
        val mid = m.id.transformInto[ManagerLight.Id]
        val fired = guarded(mid, out)(fireCron(m, now, out))
        guarded(mid, out)(
          byKind
            .get(kindOf(m))
            .foreach(
              _.refill(
                this,
                m,
                now,
                (if fired then ManagerKindDefinition.RefillForce.Force else ManagerKindDefinition.RefillForce.Safe),
                out
              )
            )
        )
      }
      // the autopilot's focus: which goals are active; the others' model-using items are held (after the
      // refill and the crons, so what they just queued waits too)
      out ++= guarded(ManagerLight.Id("focus"), out)(agents.focus.tick(now)).map(TickLine(_))
      dispatchAll(live, now, out)
      out ++= guarded(ManagerLight.Id("plan-refresh"), out)(agents.scheduler.refreshPlans(now)).map(TickLine(_))
      out ++= guarded(ManagerLight.Id("drain"), out)(agents.drain.tick(now)).map(TickLine(_))
      if now.toEpochMilli - lastPrune.get() > 3600000L then
        lastPrune.set(now.toEpochMilli)
        Transactor(ds).write(
          Unused(ManagerRepo.pruneEvents(CodexRetention.KeepDays(mcfg.keepEventsDays.value)), Unused.Why.Reported)
        )
      out.result()
  }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def guarded[A](id: ManagerLight.Id, out: Builder[TickLine, List[TickLine]])(f: => A)(using
      z: Zero[A]
  ): A =
    try f
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"manager $id: ${e.getClass.getSimpleName}: ${e.getMessage}", e)
        out += TickLine(s"$id: error ${e.getMessage}")
        z.value

  private def kindOf(m: ManagerInfo): Kind = Kind.parse(m.kind).getOrElse(Kind.Review)

  def settings(m: ManagerInfo): ReviewSettings =
    val k = kindOf(m)
    kinds
      .find(_.kind == k)
      .fold(
        ReviewSettings
          .forKind(k.transformInto[Manager.Kind], m.config, managerDefaults)
      )(_.settings(m.config, managerDefaults))

  // ── 1. evaluate ────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def evaluate(m: ManagerInfo, now: Instant, out: Builder[TickLine, List[TickLine]])(using
      WritePermit
  )(using Creator.New): Unit =
    val s = settings(m)
    Transactor(ds).read(ManagerRepo.toEvaluate(m.id)).foreach {
      case (item, None) =>
        // its run row is gone (deleted by hand): the item goes back to the queue
        if Transactor(ds).write(
            ManagerRepo.requeue(item.id, ManagerRepo.ItemRow.Outcome("its run disappeared; queued again"))
          )
        then
          recordEvent(
            m.id,
            Some(item.id),
            item.runId.map(runIdOf),
            Feed.Evaluated,
            s"${item.target}: its run disappeared; pending again"
          )
      case (item, Some((status, error))) =>
        val st = RunState.parse(status).getOrElse(RunState.Failed)
        val problem = error
        byItem.get(itemKindOf(item)) match
          case Some(k) => k.evaluate(this, m, s, item, st, problem, now, out)
          case None    => evaluateStep(m, s, item, st, problem, now, out, Unknown)
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def retry(
      m: ManagerInfo,
      item: ManagerItem,
      run: DomainRun.Id,
      notBefore: Instant,
      failures: ManagerContext.FailureCount,
      note: ManagerContext.RetryNote,
      out: Builder[TickLine, List[TickLine]]
  )(using WritePermit): Unit =
    if Transactor(ds).write(
        ManagerRepo.evaluated(
          item.id,
          Some(run.transformInto[RunInfo.Id]),
          ItemStatus.Pending,
          Some(notBefore),
          Some(failures.transformInto[ManagerItem.Failures]),
          item.state,
          Some(ManagerItem.Outcome(s"$note; retry after $notBefore"))
        )
      )
    then
      recordEvent(
        m.id,
        Some(item.id),
        Some(run),
        Feed.Evaluated,
        s"${item.target}: $note; retry after $notBefore"
      )
      out += TickLine(s"${item.target}: retry")

  /** A one-run item (infer-filter, extend, enrich, propose, compile) after its run settled. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def evaluateStep(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[Problem],
      now: Instant,
      out: Builder[TickLine, List[TickLine]],
      kind: ManagerKindDefinition
  )(using WritePermit)(using Creator.New): Unit =
    val runInfoId = item.runId.get
    val run = runIdOf(runInfoId)
    ManagerPolicy.evaluateStep(
      s,
      ManagerPolicy.StepLabel(item.kind.value),
      itemTargetOf(item),
      item.failures.transformInto[Next.Retry.Failures],
      item.state,
      run,
      st.transformInto[domain.automation.Run.State],
      error,
      kind.facts(this, item, run),
      now
    ) match
      case Next.Done(outcome, state) =>
        if Transactor(ds).write(
            ManagerRepo.evaluated(
              item.id,
              Some(runInfoId),
              ItemStatus.Done,
              None,
              None,
              state,
              Some(outcome.transformInto[ManagerItem.Outcome])
            )
          )
        then
          recordEvent(
            m.id,
            Some(item.id),
            Some(run),
            Feed.Evaluated,
            s"${item.target} (${item.kind}): $outcome"
          )
          out += TickLine(s"${item.target}: done")
          kind.afterDone(this, m, item, Some(run), out)
      case Next.Wait(approval, note) =>
        val hold = Hold(
          reason = Managers.Rationale(note.value),
          untilApproval = Some(Hold.UntilApproval(approval.value))
        )
        if Transactor(ds).write(
            ManagerRepo.evaluated(
              item.id,
              Some(runInfoId),
              ItemStatus.Held,
              None,
              None,
              item.state,
              Some(ManagerItem.Outcome(note.value)),
              Some(hold)
            )
          )
        then
          recordEvent(m.id, Some(item.id), Some(run), Feed.Held, s"${item.target} (${item.kind}): $note")
          out += TickLine(s"${item.target}: waits for approval $approval")
      case Next.Retry(notBefore, failures, note) => retry(m, item, run, notBefore, failures, note, out)
      case Next.Stop(outcome, task, state)       =>
        val failed = st == RunState.Failed
        val filed = task.flatMap(t =>
          workLists
            .record(
              WorkListControllers.ListName(Queue.Kind.Tasks.label.value),
              QueueAdd(
                Queue.QueueItem.Text(t.value),
                topic = Some(Queue.QueueItem.Topic("agent manager")),
                goal = m.goal,
                job = Some(run.transformInto[SpatialHierarchyResult.Job.Id])
              ),
              kind.creator
            )
            .toOption
            .map(_.id)
        )
        val text = outcome.value + filed.fold("")(id => s" (task #$id)")
        if Transactor(ds).write(
            ManagerRepo.evaluated(
              item.id,
              Some(runInfoId),
              if failed then ItemStatus.Failed else ItemStatus.Stopped,
              None,
              Option.when(failed)(ManagerItem.Failures(item.failures.value + 1)),
              state,
              Some(ManagerItem.Outcome(text))
            )
          )
        then
          recordEvent(
            m.id,
            Some(item.id),
            Some(run),
            Feed.Stopped,
            s"${item.target} (${item.kind}): $text"
          )
          out += TickLine(s"${item.target}: stopped")
      case Next.Again(notBefore, state, note) =>
        if Transactor(ds).write(
            ManagerRepo.evaluated(
              item.id,
              Some(runInfoId),
              ItemStatus.Pending,
              notBefore,
              None,
              state,
              Some(ManagerItem.Outcome(note.value))
            )
          )
        then recordEvent(m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note")

  /** How a decided approval ended, as the outcome text (None: still open). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def decidedAs(a: ApprovalInfo): Option[String] =
    ManagerPolicy
      .decided(a.state.transformInto[DomainApproval.State.Wire], a.result.map(r => DomainApproval.Outcome(r.value)))
      .map(_.value)

  /** The approval of `kind` a run opened: none (nothing passed its checks), open (the item waits for it), or decided
    * already (approved automatically, say).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def approvalFacts(run: DomainRun.Id, kind: DomainApproval.Kind, none: Item.Outcome): StepFacts =
    agents.approvalStore
      .approvalsOf(run.transformInto[Job.Id], Some(kind.transformInto[Approval.Kind]))
      .lastOption match
      case None                                          => StepFacts.Finished(none)
      case Some(a) if a.state == ApprovalState.Open.wire =>
        StepFacts.Waiting(
          ManagerPolicy.ApprovalId(a.id.value),
          Item.Note(s"approval #${a.id} waits for a person")
        )
      case Some(a) =>
        StepFacts.Finished(Item.Outcome(s"approval #${a.id} " + decidedAs(a).getOrElse(a.state.value)))

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def chainReview(
      id: ManagerLight.Id,
      slug: Slug,
      why: Problem,
      out: Builder[TickLine, List[TickLine]]
  )(using WritePermit): Unit =
    val infoId = id.transformInto[ManagerInfo.Id]
    val (itemId, isNew) =
      agents.managerStore.upsert(
        infoId,
        ManagerItem.Kind(ArticleReview),
        ManagerItem.Target(slug.value),
        ItemOrigin.Chain,
        ManagerItem.Priority(ManagerSupervisor.ChainPriority),
        reopen = true
      )
    recordEvent(
      infoId,
      Some(itemId),
      None,
      Feed.Evaluated,
      s"$slug ${if isNew then "queued" else "queued again"} for review (${why.sentence}, priority ${ManagerSupervisor.ChainPriority})"
    )
    out += TickLine(s"$slug: review chained")

  /** An approved `article-plan`: one compile item per article in its goal's seeding manager (created paused if it does
    * not exist), in the plan's order. Idempotent: an article planned before keeps its item. Returns how many were
    * added.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def applyPlan(a: ApprovalInfo, out: Builder[TickLine, List[TickLine]] = List.newBuilder[TickLine])(using
      WritePermit
  )(using Creator.New): RowCount =
    (a.subject, a.articles) match
      case (Some(goal), Some(plan)) if a.state == ApprovalState.Approved.wire =>
        val id = seedId(Goal.Ref(goal.value))
        Unused(
          ensure(Kind.Seed, id.transformInto[ManagerLight.Id], Some(DomainGoal.Slug.assume(goal.value))),
          Unused.Why.Idempotent
        )
        val added = plan.articles.count { p =>
          val spec = ArticleSpec(p.title, p.kind, p.filter, Managers.Rationale(s"${p.why}"))
          agents.managerStore
            .upsert(
              id,
              ManagerItem.Kind(ArticleCompile),
              ManagerItem.Target(p.slug.value),
              ItemOrigin.Chain,
              ManagerItem.Priority(0),
              reopen = false,
              ItemState(spec = Some(spec))
            )
            ._2
        }
        if added > 0 then
          recordEvent(
            id,
            None,
            None,
            Feed.Evaluated,
            s"plan #${a.id} approved: ${Plural.count(Plural.CountN(added.toLong), Plural.CountSingular("article"))} to compile (${plan.articles.map(_.slug).mkString(", ")})"
              .take(2000)
          )
          out += TickLine(s"$id: $added to compile")
          nudge()
        RowCount(added)
      case _ => RowCount(0)

  // ── 2. holds ───────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def releaseHolds(m: ManagerInfo, now: Instant, out: Builder[TickLine, List[TickLine]])(using
      WritePermit
  )(using Creator.New): Unit =
    connect(ds)(ManagerRepo.withStatus(m.id, ItemStatus.Held)).foreach { i =>
      i.hold.flatMap(_.untilApproval) match
        case Some(aid) => settleOnApproval(m, i, aid, out)
        case None      =>
          if i.hold
              .exists(
                ManagerPolicy.released(_, now, r => Transactor(ds).read(ManagerRepo.sourceExists(r)))
              ) && Transactor(ds).write(
              ManagerRepo.release(i.id)
            )
          then
            recordEvent(
              m.id,
              Some(i.id),
              None,
              Feed.Held,
              s"${i.target}: hold released (" +
                i.hold
                  .flatMap(h => h.untilSource.map(s => s"$s exists").orElse(h.until.map(u => s"$u passed")))
                  .getOrElse("") + ")"
            )
            out += TickLine(s"${i.target}: released")
    }

  /** An item held for approval `aid`: once a person decided it, the item is settled with the decision (approved: done —
    * an approved plan becomes compile items; rejected or failed: stopped).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def settleOnApproval(
      m: ManagerInfo,
      i: ManagerItem,
      aid: Hold.UntilApproval,
      out: Builder[TickLine, List[TickLine]]
  )(using WritePermit)(using Creator.New): Unit =
    agents.approvalStore.approval(DomainApproval.Id(aid.value)) match
      case None =>
        if Transactor(ds).write(
            ManagerRepo
              .settle(i.id, ItemStatus.Stopped, ManagerItem.Outcome(s"approval #$aid is gone"), List(ItemStatus.Held))
          )
        then recordEvent(m.id, Some(i.id), None, Feed.Stopped, s"${i.target}: approval #$aid is gone")
      case Some(a) =>
        decidedAs(a).foreach { decided =>
          val ok = a.state == ApprovalState.Approved.wire
          val outcome = s"approval #$aid $decided"
          if Transactor(ds).write(
              ManagerRepo
                .settle(
                  i.id,
                  if ok then ItemStatus.Done else ItemStatus.Stopped,
                  ManagerItem.Outcome(outcome),
                  List(ItemStatus.Held)
                )
            )
          then
            val run = i.runId.map(runIdOf)
            recordEvent(
              m.id,
              Some(i.id),
              run,
              Feed.Evaluated,
              s"${i.target} (${i.kind}): $outcome".take(2000)
            )
            out += TickLine(s"${i.target}: approval decided")
            if ok then byItem.get(itemKindOf(i)).foreach(_.afterDone(this, m, i, run, out))
        }

  // ── 0. schedules → watchers ────────────────────────────────────────────────────────────────────

  /** Every `goal_schedules` row left becomes (part of) its goal's watcher `watch:<goal>` — created PAUSED, its cron,
    * sweeps and per-run budget from the schedules ([[WatchSchedules.migrate]]; an existing watcher keeps its own
    * settings) — and the rows go: one transaction per goal (the rows are taken out with DELETE … RETURNING, so two
    * servers cannot both migrate them). Logged as the watcher's `migrated` event and a worklog line. Idempotent: run at
    * every start and every tick (a bundle import still writes rows), it finds nothing once they are moved. Returns one
    * line per goal migrated.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def migrateSchedules(now: Instant)(using WritePermit)(using Creator.New): List[TickLine] =
    agents.goals.scheduleGoals().flatMap { g =>
      val id = watchId(Goal.Ref(g.value))
      val apiGoal = g.transformInto[Goal.Slug]
      try
        val moved = Transactor(ds).write {
          val rows = agents.goals.takeSchedulesIn(g)
          Option.when(rows.nonEmpty) {
            val existing = agents.managerStore.get(id)
            val plan = WatchSchedules.migrate(
              existing.map(m =>
                WatchSchedules.Existing(
                  m.config,
                  m.cron.map(c => Cron.Text(c.value)),
                  m.nextTickAt.map(t => ManagerPolicy.InstantText(t.value))
                )
              ),
              rows,
              now
            )
            val next = plan.nextTick.flatMap(ManagerPolicy.parseInstant)
            val cron = plan.cron.map(c => ManagerInfo.Cron(c.value))
            existing match
              case None =>
                Unused(
                  ManagerRepo.createIn(id, Kind.Watch, Some(apiGoal), State.Paused, plan.config, cron, next),
                  Unused.Why.Held
                )
              case Some(_) => Unused(ManagerRepo.setConfigIn(id, plan.config, cron, next), Unused.Why.Held)
            val text =
              s"${Plural.count(Plural.CountN(rows.size.toLong), Plural.CountSingular("goal schedule"))} moved into this watcher" +
                (if existing.isEmpty then " (created paused: resume it for them to fire)" else "") +
                (if plan.notes.isEmpty then "" else s": ${plan.notes.mkString("; ")}")
            ManagerRepo.eventIn(id, None, None, ManagerEvent.Kind(Feed.Migrated), ManagerEvent.Text(text))
            text
          }
        }
        moved.toList.map { text =>
          workLists.logLine(
            QueueAdd(Queue.QueueItem.Text(s"goal $g: $text (watcher $id)".take(1000)), goal = Some(apiGoal)),
            InTreeWorkflow.Managers.creator
          )
          scribe.info(s"goal $g: $text")
          TickLine(s"$id: migrated")
        }
      catch
        case e: InterruptedException => throw e
        case e: Exception            =>
          scribe.warn(s"goal $g: its schedules were not moved into $id: ${e.getMessage}", e)
          Nil
    }

  // ── 3. the cron, then the refill ───────────────────────────────────────────────────────────────

  /** An active manager's cron (only while `agents.scheduler.enabled`): due → fire it — a watcher's sweeps (re-opened
    * when settled; one still open is left to finish), a review manager's selection whatever its low water, a seeding
    * manager's next proposal — and move on to its next firing; never scheduled yet → record its first firing. A firing
    * missed while paused fires once. True = it fired.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fireCron(m: ManagerInfo, now: Instant, out: Builder[TickLine, List[TickLine]])(using
      WritePermit
  ): Boolean =
    if !cfg.scheduler.enabled.value then false
    else
      val cron = m.cron.flatMap(c => Cron.parse(Cron.Text(c.value)).toOption)
      ManagerPolicy.cronStep(
        cron,
        m.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value))),
        now
      ) match
        case ManagerPolicy.CronStep.Idle           => false
        case ManagerPolicy.CronStep.Schedule(next) =>
          Transactor(ds).write(Unused(ManagerRepo.setNextTick(m.id, next), Unused.Why.Idempotent))
          false
        case ManagerPolicy.CronStep.Fire(next) =>
          Transactor(ds).write(Unused(ManagerRepo.setNextTick(m.id, next), Unused.Why.Idempotent))
          val what = byKind.get(kindOf(m)).fold(Nil)(_.fire(this, m, now))
          recordEvent(
            m.id,
            None,
            None,
            Feed.Cron,
            s"cron `${m.cron.fold("")(_.value)}` fired" +
              (if what.isEmpty then "" else s": ${what.mkString(", ")}") + next.fold("; it fires no more")(t =>
                s"; next $t"
              )
          )
          out += TickLine(s"${m.id}: cron fired")
          true

  // ── 4. dispatch ────────────────────────────────────────────────────────────────────────────────

  /** The central scheduler's pass ([[SchedulerService]]): every active manager's due items are candidates, one decision
    * admits them across every job type within the AI allowance (the runs at once, the plan pools, the budgets and daily
    * caps, a manager's own saved `maxRuns`), starting each through [[dispatch]] — one that cannot start now (another
    * run writes its target) gives way to the next. Then each manager's status line, with the scheduler's reason when
    * its items wait, and a `starved`/budget event once per change.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def dispatchAll(
      ms: List[ManagerInfo],
      now: Instant,
      out: Builder[TickLine, List[TickLine]]
  )(using WritePermit)(using Creator.New): Unit =
    val byId = ms.map(m => m.id -> m).toMap
    val decision = agents.scheduler.pass(ms, now, agents.drain.block) { (m, item) =>
      if blocks(itemKindOf(item), unsettledOn(itemTargetOf(item))) then
        Left(SchedulerPolicy.Block.Refusal.TargetWritten)
      else
        guardedOpt(m.id.transformInto[ManagerLight.Id], out)(dispatch(m, settings(m), item, now, out)) match
          case Some(_) => Right(())
          case None    =>
            if agents.managerStore.item(item.id).exists(_.status == ItemStatus.Pending.wire) then
              Left(SchedulerPolicy.Block.Refusal.NotStartable)
            else Left(SchedulerPolicy.Block.Refusal.Settled)
    }
    ms.foreach { m =>
      val waits =
        decision.waiting
          .filter(_._1.c.managerId.value == m.id.value)
          .map(_._2)
          .filterNot(_.why == SchedulerPolicy.Wait.Why.Busy(SchedulerPolicy.Block.Refusal.Settled))
      val blocked = waits.map(_.why).find(_.isBudget)
      val starved = waits.filter(_.code == SchedulerPolicy.Code.Blocked).map(_.why).find(_.isStarved)
      val full = waits.exists(w =>
        List(SchedulerPolicy.Code.Capacity, SchedulerPolicy.Code.Pool, SchedulerPolicy.Code.Manager).contains(w.code)
      )
      val pending = math.max(0, connect(ds)(ManagerRepo.counts(m.id)).pending.value)
      val held = blocked.orElse(starved)
      val base = ManagerPolicy.statusNote(
        Manager.State.Active,
        held,
        if full then ManagerPolicy.Capacity(0) else ManagerPolicy.Capacity(1),
        ManagerPolicy.PendingCount(pending),
        ManagerPolicy.DueCount(waits.size),
        ManagerPolicy.InFlightCount(agents.managerStore.running(m.id).value)
      )
      val note = waits.headOption.filter(_ => held.isEmpty) match
        case Some(w) => base.appended(s"; the scheduler: ${w.code} — ${w.why.render.take(200)}")
        case None    => base
      Transactor(ds).write(Unused(ManagerRepo.setStatusNote(m.id, note), Unused.Why.Idempotent))
      // a starved (or budget-blocked) manager says so once per change, not every tick
      held.map(_.render).foreach { b =>
        if !agents.managerStore.lastEventOf(m.id, ManagerEvent.Kind(Feed.Starved)).exists(_.text.value == b) then
          recordEvent(m.id, None, None, Feed.Starved, b)
      }
    }
    // paused/stopped managers show it too
    agents.managerStore.list().filterNot(m => byId.contains(m.id)).foreach { m =>
      State
        .parse(m.state)
        .foreach(st =>
          Transactor(ds).write(
            Unused(
              ManagerRepo.setStatusNote(
                m.id,
                ManagerPolicy.statusNote(
                  st.transformInto[Manager.State],
                  None,
                  ManagerPolicy.Capacity(0),
                  ManagerPolicy.PendingCount(m.counts.pending.value),
                  ManagerPolicy.DueCount(0),
                  ManagerPolicy.InFlightCount(m.inFlight.value)
                )
              ),
              Unused.Why.Idempotent
            )
          )
        )
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def guardedOpt[A](id: ManagerLight.Id, out: Builder[TickLine, List[TickLine]])(
      f: => Option[A]
  ): Option[A] =
    try f
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"manager $id: dispatch failed: ${e.getMessage}", e)
        out += TickLine(s"$id: dispatch error ${e.getMessage}")
        None

  final private class Lost extends RuntimeException("the item moved on")

  /** Settle a pending item without a run (its target is gone, or its work is done already). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def settleNow(
      m: ManagerInfo,
      item: ManagerItem,
      status: ItemStatus,
      why: ManagerContext.SettleNowWhy
  )(using WritePermit): Option[ManagerLight.Id] =
    if Transactor(ds).write(
        ManagerRepo.settle(item.id, status, ManagerItem.Outcome(why.value), List(ItemStatus.Pending))
      )
    then
      recordEvent(
        m.id,
        Some(item.id),
        None,
        if status == ItemStatus.Done then Feed.Evaluated else Feed.Stopped,
        s"${item.target} (${item.kind}): $why"
      )
    None

  /** Start the next run of one item; Some(manager id) when it started. Its kind decides how. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def dispatch(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[TickLine, List[TickLine]]
  )(using WritePermit)(using Creator.New): Option[ManagerLight.Id] =
    if blocks(itemKindOf(item), unsettledOn(itemTargetOf(item))) then None // another run writes it: wait
    else
      byItem.get(itemKindOf(item)) match
        case Some(k) => k.dispatch(this, m, s, item, now, out)
        case None    =>
          settleNow(
            m,
            item,
            ItemStatus.Cancelled,
            Item.Outcome(s"'${item.kind}' items are not driven by a ${m.kind} manager")
          )

  /** Create, mark and submit one run: ONE transaction (the run row, the item's compare-and-set, the hints it reached,
    * the event), the submit after the commit. The manager's model overrides go to the roles the pipeline uses; `tweak`
    * sets the options the request does not carry.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def start(
      m: ManagerInfo,
      item: ManagerItem,
      req0: RunRequest,
      tweak: RunParams => RunParams,
      state: ItemState,
      what: ManagerContext.StartWhat,
      out: Builder[TickLine, List[TickLine]]
  )(using WritePermit)(using Creator.New): Option[ManagerLight.Id] =
    val roles = pipelineOf(itemKindOf(item)).roles
    val req =
      req0
        .modify(_.modelFor)
        .setTo(m.config.models.getOrElse(Nil).filter(o => Role.parse(o.role).exists(roles.contains)))
    agents.prepare(req, None) match
      case Left(e) =>
        val note = s"not started: ${e.problems.sentences.mkString("; ")}".take(500)
        if !agents.managerStore.lastEventOf(m.id, ManagerEvent.Kind(Feed.Starved)).exists(_.text.value == note) then
          recordEvent(m.id, Some(item.id), None, Feed.Starved, note)
        None
      case Right(pr) =>
        val hints = ManagerPolicy.notesFor(
          Transactor(ds).read(ManagerRepo.hints(m.id, live = true)),
          Item.Id(item.id.value)
        )
        val notes = hints.map(h => RunParams.Notes(h.text.value.trim))
        val params = tweak(pr.params).copy(notes = Option.when(notes.nonEmpty)(notes))
        val id =
          try
            Some(Transactor(ds).write {
              val id = agents.jobRuns.createIn(
                RunService.row(
                  pr.pipeline,
                  pr.target,
                  pr.model,
                  BigDecimal(pr.usd.value),
                  pr.tokens,
                  params,
                  None,
                  pr.goal,
                  None,
                  Origin.Manager,
                  pr.dryRun,
                  Some(m.id.transformInto[ManagerLight.Id]),
                  Some(item.id.transformInto[FocusRepo.WorkRow.Id])
                )
              )
              if !ManagerRepo.dispatchIn(item.id, id, state) then throw Lost()
              ManagerRepo.usedIn(hints, id)
              ManagerRepo.eventIn(
                m.id,
                Some(item.id),
                Some(id.transformInto[RunInfo.Id]),
                ManagerEvent.Kind(Feed.Started),
                ManagerEvent.Text(
                  s"${item.target}: run ${item.attempts.value + 1} ($what)" + (if notes.nonEmpty then
                                                                                 s" with ${Plural.count(Plural.CountN(notes.size.toLong), Plural.CountSingular("note"))}"
                                                                               else "")
                )
              )
              id
            })
          catch case _: Lost => None
        id.map { runId =>
          agents.engine.submit(runId)
          out += TickLine(s"${item.target}: started ${runId.value.take(8)}")
          m.id.transformInto[ManagerLight.Id]
        }

  // ── the item kinds' pipelines ──────────────────────────────────────────────────────────────────

  /** The pipeline an item kind runs, as the kind that drives it says (an item kind none drives: as the first registered
    * kind says).
    */
  def pipelineOf(itemKind: ManagerContext.ItemKind): Pipeline =
    byItem
      .get(itemKind)
      .orElse(kinds.headOption)
      .getOrElse(throw IllegalStateException("no manager kind is registered"))
      .pipelineOf(itemKind)

  /** [[ManagerPolicy.blocks]] over the unsettled runs on an item's target. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def blocks(kind: ManagerContext.ItemKind, unsettled: Seq[Agents.RunInfo]): Boolean =
    ManagerPolicy.blocks(
      kind,
      pipelineOf(kind).wire.transformInto[DomainPipeline.Wire],
      unsettled.map(r => DomainPipeline.Wire(r.pipeline.value))
    )

  /** The roles a manager of kind `k` runs (what its `models` setting may name). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def roles(k: Kind): List[Role] =
    itemKinds(k)
      .filterNot(_.value == OaIngest)
      .map(i => pipelineOf(ManagerContext.ItemKind(i.value)))
      .flatMap(_.roles)
      .distinct

  // ── what a kind is given (ManagerContext) ─────────────────────────────────────────────────────

  /** An item of a kind no registered kind drives: settled as a finished one-run item. */
  private val Unknown: ManagerKindDefinition = new ManagerKindDefinition:
    val kind: Kind = Kind.Review
    val creator: domain.Creator.New = InTreeWorkflow.Managers.creator
    override def items: List[ManagerContext.ItemKind] = Nil
    def fire(c: ManagerContext, m: ManagerInfo, now: Instant)(using
        WritePermit
    ): List[ManagerKindDefinition.FirePhrase] = Nil
    def facts(c: ManagerContext, item: ManagerItem, run: DomainRun.Id): StepFacts =
      StepFacts.Finished(Item.Outcome("enriched"))
    def dispatch(
        c: ManagerContext,
        m: ManagerInfo,
        s: ReviewSettings,
        item: ManagerItem,
        now: Instant,
        out: Builder[TickLine, List[TickLine]]
    )(using WritePermit)(using Creator.New): Option[ManagerLight.Id] = None
    def target(
        m: ManagerInfo,
        itemKind: ManagerContext.ItemKind,
        raw: ManagerItem.Target
    ): Either[Api.Error, ManagerContext.ItemTarget] =
      Left(
        Api.Error(
          Problem.Invalid.Unknown
            .here(Problem.TypeName.of(ManagerContext.ItemKind), Problem.Raw.of(itemKind), Vector.empty)
        )
      )
    def pipelineOf(itemKind: ManagerContext.ItemKind): Pipeline = ManagerSupervisor.this.pipelineOf(itemKind)
    def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig = ManagerConfig()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def event(
      id: ManagerLight.Id,
      item: Option[ManagerContext.ItemId],
      run: Option[DomainRun.Id],
      feed: ManagerContext.EventFeed,
      text: ManagerContext.EventText
  )(using WritePermit): Unit =
    recordEvent(id.transformInto[ManagerInfo.Id], item.map(i => ManagerItem.Id(i.value)), run, feed.value, text.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def lastEventOf(id: ManagerLight.Id, feed: ManagerContext.EventFeed): Option[ManagerEvent] =
    agents.managerStore.lastEventOf(id.transformInto[ManagerInfo.Id], ManagerEvent.Kind(feed.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def countEvents(id: ManagerLight.Id, feed: ManagerContext.EventFeed, since: Instant): RowCount =
    Transactor(ds).read(ManagerRepo.countEvents(id.transformInto[ManagerInfo.Id], ManagerEvent.Kind(feed.value), since))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def upsert(
      id: ManagerLight.Id,
      kind: ManagerContext.ItemKind,
      target: ManagerContext.ItemTarget,
      origin: ItemOrigin,
      priority: ManagerContext.Priority,
      reopen: ManagerContext.UpsertReopen,
      state: ItemState = ItemState()
  )(using WritePermit): (ManagerContext.ItemId, Boolean) =
    val (itemId, isNew) = agents.managerStore.upsert(
      id.transformInto[ManagerInfo.Id],
      ManagerItem.Kind(kind.value),
      target.transformInto[ManagerItem.Target],
      origin,
      priority.transformInto[ManagerItem.Priority],
      reopen.upsertReopen,
      state
    )
    (ManagerContext.ItemId(itemId.value), isNew)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def item(itemId: ManagerContext.ItemId): Option[ManagerItem] = agents.managerStore.item(ManagerItem.Id(itemId.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def itemFor(
      id: ManagerLight.Id,
      kind: ManagerContext.ItemKind,
      target: ManagerContext.ItemTarget
  ): Option[ManagerItem] =
    agents.managerStore.itemFor(
      id.transformInto[ManagerInfo.Id],
      ManagerItem.Kind(kind.value),
      target.transformInto[ManagerItem.Target]
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hasItem(id: ManagerLight.Id, kind: ManagerContext.ItemKind, target: ManagerContext.ItemTarget): Boolean =
    Transactor(ds).read(
      ManagerRepo.hasItem(
        id.transformInto[ManagerInfo.Id],
        ManagerItem.Kind(kind.value),
        target.transformInto[ManagerItem.Target]
      )
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def restale(
      itemId: ManagerContext.ItemId,
      priority: ManagerContext.Priority,
      why: ManagerItem.Outcome
  )(using WritePermit): Boolean =
    Transactor(ds).write(
      ManagerRepo.restale(
        ManagerItem.Id(itemId.value),
        priority.transformInto[ManagerItem.Priority],
        why
      )
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pending(id: ManagerLight.Id): RowCount =
    RowCount(connect(ds)(ManagerRepo.counts(id.transformInto[ManagerInfo.Id])).pending.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def openKeys(id: ManagerLight.Id): List[(ManagerContext.ItemKind, ManagerContext.ItemTarget)] =
    agents.managerStore
      .openKeys(id.transformInto[ManagerInfo.Id])
      .map((k, t) => (ManagerContext.ItemKind(k.value), t.transformInto[ManagerContext.ItemTarget]))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def evaluated(
      itemId: ManagerContext.ItemId,
      run: Option[DomainRun.Id],
      status: ItemStatus,
      notBefore: Option[Instant],
      failures: Option[ManagerContext.FailureCount],
      state: ItemState,
      outcome: Option[ManagerContext.EvaluatedOutcome]
  )(using WritePermit): Boolean =
    Transactor(ds).write(
      ManagerRepo.evaluated(
        ManagerItem.Id(itemId.value),
        run.map(_.transformInto[RunInfo.Id]),
        status,
        notBefore,
        failures.map(_.transformInto[ManagerItem.Failures]),
        state,
        outcome.map(o => ManagerItem.Outcome(o.value))
      )
    )
  def hints(id: ManagerLight.Id): List[ManagerHint] =
    Transactor(ds).read(ManagerRepo.hints(id.transformInto[ManagerInfo.Id], live = true))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def state(id: ManagerLight.Id): Option[ManagerLight.State] =
    agents.managerStore.get(id.transformInto[ManagerInfo.Id]).map(m => ManagerLight.State(m.state.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def openMajorArticles(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[DomainGoal.Slug],
      limit: ManagerContext.Limit
  ): List[(Slug, RowCount)] =
    Transactor(ds).read(
      ManagerRepo.openMajorArticles(
        id.transformInto[ManagerInfo.Id],
        ManagerItem.Kind(ArticleReview),
        tags.map(t => EnrichSource.Tag(t.value)),
        goal,
        PageSize.assume(limit.value)
      )
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unreviewedArticles(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[DomainGoal.Slug],
      limit: ManagerContext.Limit
  ): List[Slug] =
    Transactor(ds).read(
      ManagerRepo.unreviewedArticles(
        id.transformInto[ManagerInfo.Id],
        ManagerItem.Kind(ArticleReview),
        tags.map(t => EnrichSource.Tag(t.value)),
        goal,
        PageSize.assume(limit.value)
      )
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def staleCandidates(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[DomainGoal.Slug],
      limit: ManagerContext.Limit
  ): List[(Slug, ManagerRepo.ProgressText, Option[ItemState.FilterHits], Option[ManagerContext.ItemId])] =
    Transactor(ds)
      .read(
        ManagerRepo.staleCandidates(
          id.transformInto[ManagerInfo.Id],
          ManagerItem.Kind(ArticleReview),
          tags.map(t => EnrichSource.Tag(t.value)),
          goal,
          PageSize.assume(limit.value)
        )
      )
      .map((slug, progress, hits, itemId) => (slug, progress, hits, itemId.map(i => ManagerContext.ItemId(i.value))))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def extendCandidates(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[DomainGoal.Slug],
      limit: ManagerContext.Limit
  ): List[(Slug, Option[ManagerRepo.ProgressText])] =
    Transactor(ds).read(
      ManagerRepo.extendCandidates(
        id.transformInto[ManagerInfo.Id],
        tags.map(t => EnrichSource.Tag(t.value)),
        goal,
        PageSize.assume(limit.value)
      )
    )
  def articleFacets(slug: Slug): (List[EnrichSource.Tag], List[DomainGoal.Slug]) =
    Transactor(ds).read(ManagerRepo.articleFacets(slug))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def progress(run: DomainRun.Id): JobRow.Progress =
    JobRow.Progress(agents.runs.get(run).flatMap(_.progress).map(_.value).filter(_.nonEmpty).getOrElse(""))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stepOutput(run: DomainRun.Id, step: AgentRepo.StepRecord.Name): Option[AgentRepo.StepRecord.Output] =
    agents.runs
      .steps(run.transformInto[Job.Id], AgentRepo.OutputChars(100000))
      .filter(s => s.name.value == step.value && s.status.value == StepStatus.Done.wire.value)
      .lastOption
      .flatMap(_.output)
      .map(o => AgentRepo.StepRecord.Output(o.value))
  def approvalsOf(run: DomainRun.Id, kind: DomainApproval.Kind): List[ApprovalInfo] =
    agents.approvalStore.approvalsOf(run.transformInto[Job.Id], Some(kind.transformInto[Approval.Kind]))
  def unsettledOn(target: ManagerContext.ItemTarget): Seq[RunInfo] =
    agents.runs.unsettledOn(target.transformInto[AgentsControllers.Target])
  def runsFor(p: Pipeline, target: ManagerContext.ItemTarget): Seq[RunInfo] =
    agents.runs.forTarget(p, target.transformInto[AgentsControllers.Target])
  def slugExists(slug: Slug): Boolean = agents.host.articleExists(slug)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parentOf(slug: DomainGoal.Slug): Option[DomainGoal.Slug] =
    agents.goals.get(slug).flatMap(_.parentSlug.map(g => DomainGoal.Slug.assume(g.value)))
  def linkGoal(
      goal: DomainGoal.Slug,
      kind: Goal.Plans.LinkKind,
      ref: BGoalLinkRow.Ref,
      note: Option[BGoalLinkRow.Note]
  )(using WritePermit)(using Creator.New): Unit =
    agents.goals.linkIfThere(
      goal,
      kind.transformInto[domain.automation.Goal.LinkKind],
      ref.transformInto[Goal.Plans.GoalLink.Ref],
      note.map(_.transformInto[Goal.Plans.GoalLink.Note])
    )
  def goalLinks(goal: DomainGoal.Slug, kind: Goal.Plans.LinkKind): List[BGoalLinkRow.Ref] =
    agents.goals.links(goal).filter(_.kind == kind).map(_.ref.transformInto[BGoalLinkRow.Ref])
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sharedIngestLeft(now: Instant): Option[RowCount] =
    agents.autopilot.sharedIngestLeft(now).map(l => RowCount(l.value))
  def autopilotSoon()(using WritePermit): Unit = agents.autopilot.soon()

  // ── a run settled ──────────────────────────────────────────────────────────────────────────────

  /** [[Engine.onSettled]]: a manager's run settled — an event (a stall named as one), and a tick soon. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def settled(r: RunInfo)(using WritePermit): Unit =
    r.managerId.foreach { mid =>
      val stalled = r.error.exists(e => Stopped.of(e).pausedOn(PauseReason.Stalled))
      val text =
        s"${r.target}: run ${r.status}" + r.error
          .filter(_.sentence.nonEmpty)
          .fold("")(e => s" — ${e.sentence.take(300)}")
      recordEvent(mid, r.managerItem, Some(runIdOf(r.id)), if stalled then Feed.Stall else Feed.Settled, text)
      nudge()
    }

object ManagerSupervisor:

  val HintPriority = 5

  /** A person's enqueue (the Review button, `agent manager … enqueue`). */
  val PersonPriority = 10

  /** A review a compile or an extension chained (§4: origin chain, priority 2). */
  val ChainPriority = 2

  /** Stale candidates read per article the selection has room for (most are not due yet). */
  val StaleLookahead = 5

  /** What a manager of kind `k` does, for its created-paused event. */
  def purpose(k: Kind): String = k match
    case Kind.Review => "reviewing"
    case Kind.Enrich => "enriching"
    case Kind.Seed   => "planning and compiling articles"
    case Kind.Watch  => "watching"
