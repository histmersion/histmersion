package com.histmersion.domain.automation.managers

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.Transactor

import com.augustnagro.magnum.{connect, transact}

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.goals.GoalFocusService
import com.histmersion.domain.automation.scheduler.SchedulerService

import com.histmersion.domain.automation.worklists.WorkListControllers

import com.augustnagro.magnum.transact
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
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal

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

  /** The ticking thread; a `var` because it starts after construction and stops in tests. */
  @volatile private var thread: Option[Thread] = None
  @volatile private var stopping = false
  private val nudges = LinkedBlockingQueue[String]()

  /** A run of a manager settled: tick soon (coalesced). */
  def nudge(): Unit = { val _ = nudges.offer("settled") }

  /** The library-wide managers exist — `review` and `enrich` — created PAUSED, so deploying never starts spending until
    * a person resumes them. Idempotent.
    */
  def ensureDefaults(): Unit =
    val _ = ensure(Kind.Review, ReviewId, None)
    val _ = ensure(Kind.Enrich, EnrichId, None)

  /** Manager `id` of kind `k` exists (created PAUSED when it did not — active while the autopilot is on, which runs
    * every manager); true = it was created now.
    */
  def ensure(k: Kind, id: com.histmersion.domain.automation.managers.ManagerSupervisor.EnsureId, goal: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.EnsureGoal]): com.histmersion.domain.automation.managers.ManagerSupervisor.EnsureResult =
    val auto = agents.autopilot.on
    val made = agents.managerStore.create(id, k, goal, if auto then State.Active else State.Paused, ManagerConfig())
    if made then
      agents.managerStore.event(
        id,
        None,
        None,
        if auto then Feed.Autopilot else Feed.Paused,
        if auto then s"created active (the autopilot is on): ${ManagerSupervisor.purpose(k)}"
        else s"created paused: resume it to start ${ManagerSupervisor.purpose(k)} (nothing is spent until then)"
      )
    made

  def start(): Unit =
    if cfg.enabled && mcfg.enabled && thread.isEmpty then
      stopping = false
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agent-managers").unstarted(() => loop())
      thread = Some(t)
      t.start()

  /** Whether the ticking thread runs here (agents and managers enabled, the lease held). */
  def running: com.histmersion.domain.automation.managers.ManagerSupervisor.RunningResult = thread.isDefined

  def stop(): Unit =
    stopping = true
    thread.foreach(_.interrupt())
    thread = None

  private def loop(): Unit =
    try
      while !stopping do
        // one tick, one generation: its database and config throughout (a reload moves the next one)
        try { val _ = agents.pins.pinned(tick(Instant.now(agents.clock))) }
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent managers tick failed: ${e.getMessage}", e)
        val _ = nudges.poll(math.max(1, mcfg.tickSeconds).toLong, TimeUnit.SECONDS)
        nudges.clear()
    catch case _: InterruptedException => ()

  /** When the events were last pruned (epoch ms). */
  private val lastPrune = java.util.concurrent.atomic.AtomicLong(0L)

  /** The kinds the supervisor runs, by kind; and the kind that drives each item kind. */
  private val byKind: Map[Kind, ManagerKindDefinition] = kinds.map(k => k.kind -> k).toMap
  private val byItem: Map[com.histmersion.domain.automation.managers.ManagerSupervisor.ByItemPart1, ManagerKindDefinition] = kinds.flatMap(k => k.items.map(_ -> k)).toMap

  /** One pass at `now`. Returns what was done, one line each (for a test or a log). Never throws for one item's or one
    * manager's sake.
    */

  def tick(now: Instant): List[com.histmersion.domain.automation.managers.ManagerSupervisor.TickResult] = synchronized {
    if !cfg.enabled || !mcfg.enabled then Nil
    else
      val out = List.newBuilder[String]
      out ++= migrateSchedules(now)
      // the autopilot first (its new managers are dispatched in this tick), then the approvals its checks
      // decide (a decided plan settles its held item below)
      out ++= guarded("autopilot", out)(agents.autopilot.tick(now))
      // draining: no approval decided by code (a decision wakes a waiting run: new work)
      if !agents.drain.draining then out ++= guarded("auto-approvals", out)(agents.autoApprovals.sweep(now))
      out ++= guarded("digest", out)(agents.digest.tick(now))
      val managers = agents.managerStore.list().filter(m => State.parse(m.state).exists(_ != State.Stopped))
      managers.foreach { m =>
        guarded(m.id, out)(evaluate(m, now, out))
        guarded(m.id, out)(releaseHolds(m, now, out))
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
        val fired = guarded(m.id, out)(fireCron(m, now, out))
        guarded(m.id, out)(byKind.get(kindOf(m)).foreach(_.refill(this, m, now, fired, out)))
      }
      // the autopilot's focus: which goals are active; the others' model-using items are held (after the
      // refill and the crons, so what they just queued waits too)
      out ++= guarded("focus", out)(agents.focus.tick(now))
      dispatchAll(live, now, out)
      out ++= guarded("plan-refresh", out)(agents.scheduler.refreshPlans(now))
      out ++= guarded("drain", out)(agents.drain.tick(now))
      if now.toEpochMilli - lastPrune.get() > 3600000L then
        lastPrune.set(now.toEpochMilli)
        val _ = Transactor(ds).transact(ManagerRepo.pruneEvents(mcfg.keepEventsDays))
      out.result()
  }

  private def guarded[A](id: com.histmersion.domain.automation.managers.ManagerSupervisor.GuardedId, out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.GuardedOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.GuardedOutPart2]])(f: => A)(using
      z: Zero[A]
  ): A =
    try f
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"manager $id: ${e.getClass.getSimpleName}: ${e.getMessage}", e)
        out += s"$id: error ${e.getMessage}"
        z.value

  private def kindOf(m: ManagerInfo): Kind = Kind.parse(m.kind).getOrElse(Kind.Review)

  def settings(m: ManagerInfo): ReviewSettings =
    val k = kindOf(m)
    kinds
      .find(_.kind == k)
      .fold(
        ReviewSettings
          .forKind(k.transformInto[com.histmersion.domain.automation.Manager.Kind], m.config, managerDefaults)
      )(_.settings(m.config, managerDefaults))

  // ── 1. evaluate ────────────────────────────────────────────────────────────────────────────────

  private def evaluate(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluateOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluateOutPart2]]): Unit =
    val s = settings(m)
    Transactor(ds).read(ManagerRepo.toEvaluate(m.id)).foreach {
      case (item, None) =>
        // its run row is gone (deleted by hand): the item goes back to the queue
        if Transactor(ds).transact(ManagerRepo.requeue(item.id, "its run disappeared; queued again")) then
          agents.managerStore.event(
            m.id,
            Some(item.id),
            item.runId,
            Feed.Evaluated,
            s"${item.target}: its run disappeared; pending again"
          )
      case (item, Some((status, error))) =>
        val st = RunState.parse(status).getOrElse(RunState.Failed)
        byItem.get(item.kind) match
          case Some(k) => k.evaluate(this, m, s, item, st, error, now, out)
          case None    => evaluateStep(m, s, item, st, error, now, out, Unknown)
    }

  def retry(
      m: ManagerInfo,
      item: ManagerItem,
      run: com.histmersion.domain.automation.managers.ManagerSupervisor.RetryRun,
      notBefore: Instant,
      failures: com.histmersion.domain.automation.managers.ManagerSupervisor.RetryFailures,
      note: com.histmersion.domain.automation.managers.ManagerSupervisor.RetryNote,
      out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.RetryOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.RetryOutPart2]]
  ): Unit =
    if Transactor(ds).transact(
        ManagerRepo.evaluated(
          item.id,
          Some(run),
          ItemStatus.Pending,
          Some(notBefore),
          Some(failures),
          item.state,
          Some(s"$note; retry after $notBefore")
        )
      )
    then
      agents.managerStore.event(
        m.id,
        Some(item.id),
        Some(run),
        Feed.Evaluated,
        s"${item.target}: $note; retry after $notBefore"
      )
      out += s"${item.target}: retry"

  /** A one-run item (infer-filter, extend, enrich, propose, compile) after its run settled. */
  def evaluateStep(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluateStepError],
      now: Instant,
      out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluateStepOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluateStepOutPart2]],
      kind: ManagerKindDefinition
  ): Unit =
    val run = item.runId.get
    ManagerPolicy.evaluateStep(
      s,
      item.kind,
      item.target,
      item.failures,
      item.state,
      run,
      st.transformInto[com.histmersion.domain.automation.Run.State],
      error,
      kind.facts(this, item, run),
      now
    ) match
      case Next.Done(outcome, state) =>
        if Transactor(ds).transact(
            ManagerRepo.evaluated(item.id, Some(run), ItemStatus.Done, None, None, state, Some(outcome))
          )
        then
          agents.managerStore.event(
            m.id,
            Some(item.id),
            Some(run),
            Feed.Evaluated,
            s"${item.target} (${item.kind}): $outcome"
          )
          out += s"${item.target}: done"
          kind.afterDone(this, m, item, Some(run), out)
      case Next.Wait(approval, note) =>
        val hold = Hold(reason = note, untilApproval = Some(approval))
        if Transactor(ds).transact(
            ManagerRepo.evaluated(item.id, Some(run), ItemStatus.Held, None, None, item.state, Some(note), Some(hold))
          )
        then
          agents.managerStore.event(m.id, Some(item.id), Some(run), Feed.Held, s"${item.target} (${item.kind}): $note")
          out += s"${item.target}: waits for approval $approval"
      case Next.Retry(notBefore, failures, note) => retry(m, item, run, notBefore, failures, note, out)
      case Next.Stop(outcome, task, state)       =>
        val failed = st == RunState.Failed
        val filed = task.flatMap(t =>
          workLists
            .add(
              Queue.Kind.Tasks.label,
              QueueAdd(t, topic = Some("agent manager"), goal = m.goal, job = Some(run))
            )
            .toOption
            .map(_.id)
        )
        val text = outcome + filed.fold("")(id => s" (task #$id)")
        if Transactor(ds).transact(
            ManagerRepo.evaluated(
              item.id,
              Some(run),
              if failed then ItemStatus.Failed else ItemStatus.Stopped,
              None,
              Option.when(failed)(item.failures + 1),
              state,
              Some(text)
            )
          )
        then
          agents.managerStore.event(
            m.id,
            Some(item.id),
            Some(run),
            Feed.Stopped,
            s"${item.target} (${item.kind}): $text"
          )
          out += s"${item.target}: stopped"
      case Next.Again(notBefore, state, note) =>
        if Transactor(ds).transact(
            ManagerRepo.evaluated(item.id, Some(run), ItemStatus.Pending, notBefore, None, state, Some(note))
          )
        then agents.managerStore.event(m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note")

  /** The approval of `kind` a run opened: none (nothing passed its checks), open (the item waits for it), or decided
    * already (approved automatically, say).
    */
  def approvalFacts(run: com.histmersion.domain.automation.managers.ManagerSupervisor.ApprovalFactsRun, kind: com.histmersion.domain.automation.Approval.Kind, none: com.histmersion.domain.automation.managers.ManagerSupervisor.ApprovalFactsNone): StepFacts =
    agents.approvalStore
      .approvalsOf(run, Some(kind.transformInto[com.histmersion.api.automation.Approval.Kind]))
      .lastOption match
      case None                                          => StepFacts.Finished(none)
      case Some(a) if a.state == ApprovalState.Open.wire =>
        StepFacts.Waiting(a.id, s"approval #${a.id} waits for a person")
      case Some(a) =>
        StepFacts.Finished(s"approval #${a.id} " + ManagerPolicy.decided(a.state, a.result).getOrElse(a.state))

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened.
    */
  def chainReview(
      id: com.histmersion.domain.automation.managers.ManagerSupervisor.ChainReviewId,
      slug: com.histmersion.domain.automation.managers.ManagerSupervisor.ChainReviewSlug,
      why: com.histmersion.domain.automation.managers.ManagerSupervisor.ChainReviewWhy,
      out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.ChainReviewOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.ChainReviewOutPart2]]
  ): Unit =
    val (itemId, isNew) =
      agents.managerStore.upsert(
        id,
        ArticleReview,
        slug,
        ItemOrigin.Chain,
        ManagerSupervisor.ChainPriority,
        reopen = true
      )
    agents.managerStore.event(
      id,
      Some(itemId),
      None,
      Feed.Evaluated,
      s"$slug ${if isNew then "queued" else "queued again"} for review ($why, priority ${ManagerSupervisor.ChainPriority})"
    )
    out += s"$slug: review chained"

  /** An approved `article-plan`: one compile item per article in its goal's seeding manager (created paused if it does
    * not exist), in the plan's order. Idempotent: an article planned before keeps its item. Returns how many were
    * added.
    */
  def applyPlan(a: ApprovalInfo, out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.ApplyPlanOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.ApplyPlanOutPart2]] = List.newBuilder[String]): com.histmersion.domain.automation.managers.ManagerSupervisor.ApplyPlanResult =
    (a.subject, a.articles) match
      case (Some(goal), Some(plan)) if a.state == ApprovalState.Approved.wire =>
        val id = seedId(goal)
        val _ = ensure(Kind.Seed, id, Some(goal))
        val added = plan.articles.count { p =>
          val spec = ArticleSpec(p.title, p.kind, p.filter, p.why)
          agents.managerStore
            .upsert(id, ArticleCompile, p.slug, ItemOrigin.Chain, 0, reopen = false, ItemState(spec = Some(spec)))
            ._2
        }
        if added > 0 then
          agents.managerStore.event(
            id,
            None,
            None,
            Feed.Evaluated,
            s"plan #${a.id} approved: ${Plural.count(added, "article")} to compile (${plan.articles.map(_.slug).mkString(", ")})"
              .take(2000)
          )
          out += s"$id: $added to compile"
          nudge()
        added
      case _ => 0

  // ── 2. holds ───────────────────────────────────────────────────────────────────────────────────

  private def releaseHolds(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.ReleaseHoldsOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.ReleaseHoldsOutPart2]]): Unit =
    connect(ds)(ManagerRepo.withStatus(m.id, ItemStatus.Held)).foreach { i =>
      i.hold.flatMap(_.untilApproval) match
        case Some(aid) => settleOnApproval(m, i, aid, out)
        case None      =>
          if i.hold
              .exists(
                ManagerPolicy.released(_, now, r => Transactor(ds).read(ManagerRepo.sourceExists(r)))
              ) && transact(ds)(
              ManagerRepo.release(i.id)
            )
          then
            agents.managerStore.event(
              m.id,
              Some(i.id),
              None,
              Feed.Held,
              s"${i.target}: hold released (" +
                i.hold
                  .flatMap(h => h.untilSource.map(s => s"$s exists").orElse(h.until.map(u => s"$u passed")))
                  .getOrElse("") + ")"
            )
            out += s"${i.target}: released"
    }

  /** An item held for approval `aid`: once a person decided it, the item is settled with the decision (approved: done —
    * an approved plan becomes compile items; rejected or failed: stopped).
    */
  private def settleOnApproval(
      m: ManagerInfo,
      i: ManagerItem,
      aid: com.histmersion.domain.automation.managers.ManagerSupervisor.SettleOnApprovalAid,
      out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.SettleOnApprovalOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.SettleOnApprovalOutPart2]]
  ): Unit =
    agents.approvalStore.approval(aid) match
      case None =>
        if transact(ds)(ManagerRepo.settle(i.id, ItemStatus.Stopped, s"approval #$aid is gone", List(ItemStatus.Held)))
        then agents.managerStore.event(m.id, Some(i.id), None, Feed.Stopped, s"${i.target}: approval #$aid is gone")
      case Some(a) =>
        ManagerPolicy.decided(a.state, a.result).foreach { decided =>
          val ok = a.state == ApprovalState.Approved.wire
          val outcome = s"approval #$aid $decided"
          if transact(ds)(
              ManagerRepo
                .settle(i.id, if ok then ItemStatus.Done else ItemStatus.Stopped, outcome, List(ItemStatus.Held))
            )
          then
            agents.managerStore.event(
              m.id,
              Some(i.id),
              i.runId,
              Feed.Evaluated,
              s"${i.target} (${i.kind}): $outcome".take(2000)
            )
            out += s"${i.target}: approval decided"
            if ok then byItem.get(i.kind).foreach(_.afterDone(this, m, i, i.runId, out))
        }

  // ── 0. schedules → watchers ────────────────────────────────────────────────────────────────────

  /** Every `goal_schedules` row left becomes (part of) its goal's watcher `watch:<goal>` — created PAUSED, its cron,
    * sweeps and per-run budget from the schedules ([[WatchSchedules.migrate]]; an existing watcher keeps its own
    * settings) — and the rows go: one transaction per goal (the rows are taken out with DELETE … RETURNING, so two
    * servers cannot both migrate them). Logged as the watcher's `migrated` event and a worklog line. Idempotent: run at
    * every start and every tick (a bundle import still writes rows), it finds nothing once they are moved. Returns one
    * line per goal migrated.
    */
  def migrateSchedules(now: Instant): List[com.histmersion.domain.automation.managers.ManagerSupervisor.MigrateSchedulesResult] =
    agents.goals.scheduleGoals().flatMap { g =>
      val id = watchId(g)
      try
        val moved = transact(ds) {
          val rows = agents.goals.takeSchedulesIn(g)
          Option.when(rows.nonEmpty) {
            val existing = agents.managerStore.get(id)
            val plan = WatchSchedules.migrate(
              existing.map(m => WatchSchedules.Existing(m.config, m.cron, m.nextTickAt)),
              rows,
              now
            )
            val next = plan.nextTick.flatMap(ManagerPolicy.parseInstant)
            existing match
              case None    => ManagerRepo.createIn(id, Kind.Watch, Some(g), State.Paused, plan.config, plan.cron, next)
              case Some(_) => ManagerRepo.setConfigIn(id, plan.config, plan.cron, next)
            val text = s"${Plural.count(rows.size, "goal schedule")} moved into this watcher" +
              (if existing.isEmpty then " (created paused: resume it for them to fire)" else "") +
              (if plan.notes.isEmpty then "" else s": ${plan.notes.mkString("; ")}")
            ManagerRepo.eventIn(id, None, None, Feed.Migrated, text)
            text
          }
        }
        moved.toList.map { text =>
          val _ = workLists.add("log", QueueAdd(s"goal $g: $text (watcher $id)".take(1000), goal = Some(g)))
          scribe.info(s"goal $g: $text")
          s"$id: migrated"
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
  private def fireCron(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.FireCronOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.FireCronOutPart2]]): com.histmersion.domain.automation.managers.ManagerSupervisor.FireCronResult =
    if !cfg.scheduler.enabled then false
    else
      val cron = m.cron.flatMap(c => Cron.parse(c).toOption)
      ManagerPolicy.cronStep(cron, m.nextTickAt.flatMap(ManagerPolicy.parseInstant), now) match
        case ManagerPolicy.CronStep.Idle           => false
        case ManagerPolicy.CronStep.Schedule(next) =>
          val _ = Transactor(ds).transact(ManagerRepo.setNextTick(m.id, next))
          false
        case ManagerPolicy.CronStep.Fire(next) =>
          val _ = Transactor(ds).transact(ManagerRepo.setNextTick(m.id, next))
          val what = byKind.get(kindOf(m)).fold(Nil)(_.fire(this, m, now))
          agents.managerStore.event(
            m.id,
            None,
            None,
            Feed.Cron,
            s"cron `${m.cron.getOrElse("")}` fired" +
              (if what.isEmpty then "" else s": ${what.mkString(", ")}") + next.fold("; it fires no more")(t =>
                s"; next $t"
              )
          )
          out += s"${m.id}: cron fired"
          true

  // ── 4. dispatch ────────────────────────────────────────────────────────────────────────────────

  /** The central scheduler's pass ([[SchedulerService]]): every active manager's due items are candidates, one decision
    * admits them across every job type within the AI allowance (the runs at once, the plan pools, the budgets and daily
    * caps, a manager's own saved `maxRuns`), starting each through [[dispatch]] — one that cannot start now (another
    * run writes its target) gives way to the next. Then each manager's status line, with the scheduler's reason when
    * its items wait, and a `starved`/budget event once per change.
    */
  private def dispatchAll(
      ms: List[ManagerInfo],
      now: Instant,
      out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchAllOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchAllOutPart2]]
  ): Unit =
    val byId = ms.map(m => m.id -> m).toMap
    val decision = agents.scheduler.pass(ms, now, agents.drain.block) { (m, item) =>
      if blocks(item.kind, agents.runs.unsettledOn(item.target)) then Left("another run is writing its target")
      else
        guardedOpt(m.id, out)(dispatch(m, settings(m), item, now, out)) match
          case Some(_) => Right(())
          case None    =>
            if agents.managerStore.item(item.id).exists(_.status == ItemStatus.Pending.wire) then
              Left("it could not start now (its manager's events say why)")
            else Left("settled without a run")
    }
    ms.foreach { m =>
      val waits =
        decision.waiting.filter(_._1.c.managerId == m.id).map(_._2).filterNot(_.why == "settled without a run")
      val blocked = waits
        .find(w => w.code == SchedulerPolicy.Code.Blocked && w.why.startsWith("budget"))
        .map(_.why)
        .orElse(waits.find(_.code == "budget").map(_.why))
      val starved = waits
        .find(w => w.code == SchedulerPolicy.Code.Blocked && w.why.startsWith("starved: "))
        .map(_.why.stripPrefix("starved: "))
      val full = waits.exists(w =>
        List(SchedulerPolicy.Code.Capacity, SchedulerPolicy.Code.Pool, SchedulerPolicy.Code.Manager).contains(w.code)
      )
      val pending = math.max(0, connect(ds)(ManagerRepo.counts(m.id)).pending)
      val base = ManagerPolicy.statusNote(
        com.histmersion.domain.automation.Manager.State.Active,
        starved,
        if full then 0 else 1,
        pending,
        waits.size,
        agents.managerStore.running(m.id),
        blocked
      )
      val note = waits.headOption.filter(_ => blocked.isEmpty && starved.isEmpty) match
        case Some(w) => s"$base; the scheduler: ${w.code} — ${w.why.take(200)}"
        case None    => base
      Transactor(ds).transact(ManagerRepo.setStatusNote(m.id, note))
      // a starved (or budget-blocked) manager says so once per change, not every tick
      blocked.orElse(starved.map(x => s"starved: $x")).foreach { b =>
        if !agents.managerStore.lastEventOf(m.id, Feed.Starved).exists(_.text == b) then
          agents.managerStore.event(m.id, None, None, Feed.Starved, b)
      }
    }
    // paused/stopped managers show it too
    agents.managerStore.list().filterNot(m => byId.contains(m.id)).foreach { m =>
      State
        .parse(m.state)
        .foreach(st =>
          Transactor(ds).transact(
            ManagerRepo
              .setStatusNote(
                m.id,
                ManagerPolicy.statusNote(
                  st.transformInto[com.histmersion.domain.automation.Manager.State],
                  None,
                  0,
                  m.counts.pending,
                  0,
                  m.inFlight,
                  None
                )
              )
          )
        )
    }

  private def guardedOpt[A](id: com.histmersion.domain.automation.managers.ManagerSupervisor.GuardedOptId, out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.GuardedOptOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.GuardedOptOutPart2]])(
      f: => Option[A]
  ): Option[A] =
    try f
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"manager $id: dispatch failed: ${e.getMessage}", e)
        out += s"$id: dispatch error ${e.getMessage}"
        None

  final private class Lost extends RuntimeException("the item moved on")

  /** Settle a pending item without a run (its target is gone, or its work is done already). */
  def settleNow(m: ManagerInfo, item: ManagerItem, status: ItemStatus, why: com.histmersion.domain.automation.managers.ManagerSupervisor.SettleNowWhy): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.SettleNowResult] =
    if transact(ds)(ManagerRepo.settle(item.id, status, why, List(ItemStatus.Pending))) then
      agents.managerStore.event(
        m.id,
        Some(item.id),
        None,
        if status == ItemStatus.Done then Feed.Evaluated else Feed.Stopped,
        s"${item.target} (${item.kind}): $why"
      )
    None

  /** Start the next run of one item; Some(manager id) when it started. Its kind decides how. */
  private def dispatch(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchOutPart2]]
  ): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchResult] =
    if blocks(item.kind, agents.runs.unsettledOn(item.target)) then None // another run writes it: wait
    else
      byItem.get(item.kind) match
        case Some(k) => k.dispatch(this, m, s, item, now, out)
        case None    =>
          settleNow(m, item, ItemStatus.Cancelled, s"'${item.kind}' items are not driven by a ${m.kind} manager")

  /** Create, mark and submit one run: ONE transaction (the run row, the item's compare-and-set, the hints it reached,
    * the event), the submit after the commit. The manager's model overrides go to the roles the pipeline uses; `tweak`
    * sets the options the request does not carry.
    */
  def start(
      m: ManagerInfo,
      item: ManagerItem,
      req0: RunRequest,
      tweak: RunParams => RunParams,
      state: ItemState,
      what: com.histmersion.domain.automation.managers.ManagerSupervisor.StartWhat,
      out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.StartOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.StartOutPart2]]
  ): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.StartResult] =
    val roles = pipelineOf(item.kind).roles
    val req =
      req0
        .modify(_.modelFor)
        .setTo(m.config.models.getOrElse(Nil).filter(o => Role.parse(o.role).exists(roles.contains)))
    agents.prepare(req, None) match
      case Left(e) =>
        val note = s"not started: ${e.message}".take(500)
        if !agents.managerStore.lastEventOf(m.id, Feed.Starved).exists(_.text == note) then
          agents.managerStore.event(m.id, Some(item.id), None, Feed.Starved, note)
        None
      case Right(pr) =>
        val hints = ManagerPolicy.notesFor(Transactor(ds).read(ManagerRepo.hints(m.id, live = true)), item.id)
        val notes = hints.map(_.text.trim)
        val params = tweak(pr.params).copy(notes = Option.when(notes.nonEmpty)(notes))
        val id =
          try
            Some(transact(ds) {
              val id = agents.jobRuns.createIn(
                RunService.row(
                  pr.pipeline,
                  pr.target,
                  pr.model,
                  BigDecimal(pr.usd),
                  pr.tokens,
                  params,
                  None,
                  pr.goal,
                  None,
                  Origin.Manager,
                  pr.dryRun,
                  Some(m.id),
                  Some(item.id)
                )
              )
              if !ManagerRepo.dispatchIn(item.id, id, state) then throw Lost()
              ManagerRepo.usedIn(hints, id)
              ManagerRepo.eventIn(
                m.id,
                Some(item.id),
                Some(id),
                Feed.Started,
                s"${item.target}: run ${item.attempts + 1} ($what)" + (if notes.nonEmpty then
                                                                         s" with ${Plural.count(notes.size, "note")}"
                                                                       else "")
              )
              id
            })
          catch case _: Lost => None
        id.map { runId =>
          agents.engine.submit(runId)
          out += s"${item.target}: started ${runId.take(8)}"
          m.id
        }

  // ── the item kinds' pipelines ──────────────────────────────────────────────────────────────────

  /** The pipeline an item kind runs, as the kind that drives it says (an item kind none drives: as the first registered
    * kind says).
    */
  def pipelineOf(itemKind: com.histmersion.domain.automation.managers.ManagerSupervisor.PipelineOfItemKind): Pipeline =
    byItem
      .get(itemKind)
      .orElse(kinds.headOption)
      .getOrElse(throw IllegalStateException("no manager kind is registered"))
      .pipelineOf(itemKind)

  /** [[ManagerPolicy.blocks]] over the unsettled runs on an item's target. */
  def blocks(kind: com.histmersion.domain.automation.managers.ManagerSupervisor.BlocksKind, unsettled: Seq[Agents.RunInfo]): com.histmersion.domain.automation.managers.ManagerSupervisor.BlocksResult =
    ManagerPolicy.blocks(kind, pipelineOf(kind).wire, unsettled.map(_.pipeline))

  /** The roles a manager of kind `k` runs (what its `models` setting may name). */
  def roles(k: Kind): List[Role] = itemKinds(k).filterNot(_ == OaIngest).map(pipelineOf).flatMap(_.roles).distinct

  // ── what a kind is given (ManagerContext) ─────────────────────────────────────────────────────

  /** An item of a kind no registered kind drives: settled as a finished one-run item. */
  private val Unknown: ManagerKindDefinition = new ManagerKindDefinition:
    val kind: Kind = Kind.Review
    override def items: List[String] = Nil
    def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[com.histmersion.domain.automation.managers.ManagerSupervisor.FireResult] = Nil
    def facts(c: ManagerContext, item: ManagerItem, run: com.histmersion.domain.automation.managers.ManagerSupervisor.FactsRun): StepFacts = StepFacts.Finished("enriched")
    def dispatch(
        c: ManagerContext,
        m: ManagerInfo,
        s: ReviewSettings,
        item: ManagerItem,
        now: Instant,
        out: collection.mutable.Builder[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchOutPart1, List[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchOutPart2]]
    ): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.DispatchResult] = None
    def target(m: ManagerInfo, itemKind: com.histmersion.domain.automation.managers.ManagerSupervisor.TargetItemKind, raw: com.histmersion.domain.automation.managers.ManagerSupervisor.TargetRaw): Either[Api.Error, com.histmersion.domain.automation.managers.ManagerSupervisor.TargetResultPart2] =
      Left(Api.Error(s"unknown item kind '$itemKind'"))
    def pipelineOf(itemKind: com.histmersion.domain.automation.managers.ManagerSupervisor.PipelineOfItemKind): Pipeline = ManagerSupervisor.this.pipelineOf(itemKind)
    def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig = ManagerConfig()

  def event(id: com.histmersion.domain.automation.managers.ManagerSupervisor.EventId, item: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.EventItem], run: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.EventRun], feed: com.histmersion.domain.automation.managers.ManagerSupervisor.EventFeed, text: com.histmersion.domain.automation.managers.ManagerSupervisor.EventText): Unit =
    agents.managerStore.event(id, item, run, feed, text)
  def lastEventOf(id: com.histmersion.domain.automation.managers.ManagerSupervisor.LastEventOfId, feed: com.histmersion.domain.automation.managers.ManagerSupervisor.LastEventOfFeed): Option[ManagerEvent] = agents.managerStore.lastEventOf(id, feed)
  def countEvents(id: com.histmersion.domain.automation.managers.ManagerSupervisor.CountEventsId, feed: com.histmersion.domain.automation.managers.ManagerSupervisor.CountEventsFeed, since: Instant): com.histmersion.domain.automation.managers.ManagerSupervisor.CountEventsResult =
    Transactor(ds).read(ManagerRepo.countEvents(id, feed, since))
  def upsert(
      id: com.histmersion.domain.automation.managers.ManagerSupervisor.UpsertId,
      kind: com.histmersion.domain.automation.managers.ManagerSupervisor.UpsertKind,
      target: com.histmersion.domain.automation.managers.ManagerSupervisor.UpsertTarget,
      origin: ItemOrigin,
      priority: com.histmersion.domain.automation.managers.ManagerSupervisor.UpsertPriority,
      reopen: com.histmersion.domain.automation.managers.ManagerSupervisor.UpsertReopen,
      state: ItemState = ItemState()
  ): (com.histmersion.domain.automation.managers.ManagerSupervisor.UpsertResultPart1, com.histmersion.domain.automation.managers.ManagerSupervisor.UpsertResultPart2) = agents.managerStore.upsert(id, kind, target, origin, priority, reopen, state)
  def item(itemId: com.histmersion.domain.automation.managers.ManagerSupervisor.ItemItemId): Option[ManagerItem] = agents.managerStore.item(itemId)
  def itemFor(id: com.histmersion.domain.automation.managers.ManagerSupervisor.ItemForId, kind: com.histmersion.domain.automation.managers.ManagerSupervisor.ItemForKind, target: com.histmersion.domain.automation.managers.ManagerSupervisor.ItemForTarget): Option[ManagerItem] =
    agents.managerStore.itemFor(id, kind, target)
  def hasItem(id: com.histmersion.domain.automation.managers.ManagerSupervisor.HasItemId, kind: com.histmersion.domain.automation.managers.ManagerSupervisor.HasItemKind, target: com.histmersion.domain.automation.managers.ManagerSupervisor.HasItemTarget): com.histmersion.domain.automation.managers.ManagerSupervisor.HasItemResult =
    Transactor(ds).read(ManagerRepo.hasItem(id, kind, target))
  def restale(itemId: com.histmersion.domain.automation.managers.ManagerSupervisor.RestaleItemId, priority: com.histmersion.domain.automation.managers.ManagerSupervisor.RestalePriority, why: com.histmersion.domain.automation.managers.ManagerSupervisor.RestaleWhy): com.histmersion.domain.automation.managers.ManagerSupervisor.RestaleResult =
    Transactor(ds).transact(ManagerRepo.restale(itemId, priority, why))
  def pending(id: com.histmersion.domain.automation.managers.ManagerSupervisor.PendingId): com.histmersion.domain.automation.managers.ManagerSupervisor.PendingResult = connect(ds)(ManagerRepo.counts(id)).pending
  def openKeys(id: com.histmersion.domain.automation.managers.ManagerSupervisor.OpenKeysId): List[(com.histmersion.domain.automation.managers.ManagerSupervisor.OpenKeysResultPart1, com.histmersion.domain.automation.managers.ManagerSupervisor.OpenKeysResultPart2)] = agents.managerStore.openKeys(id)
  def evaluated(
      itemId: com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluatedItemId,
      run: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluatedRun],
      status: ItemStatus,
      notBefore: Option[Instant],
      failures: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluatedFailures],
      state: ItemState,
      outcome: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluatedOutcome]
  ): com.histmersion.domain.automation.managers.ManagerSupervisor.EvaluatedResult = Transactor(ds).transact(ManagerRepo.evaluated(itemId, run, status, notBefore, failures, state, outcome))
  def hints(id: com.histmersion.domain.automation.managers.ManagerSupervisor.HintsId): List[ManagerHint] = Transactor(ds).read(ManagerRepo.hints(id, live = true))
  def state(id: com.histmersion.domain.automation.managers.ManagerSupervisor.StateId): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.StateResult] = agents.managerStore.get(id).map(_.state)
  def openMajorArticles(id: com.histmersion.domain.automation.managers.ManagerSupervisor.OpenMajorArticlesId, tags: List[com.histmersion.domain.automation.managers.ManagerSupervisor.OpenMajorArticlesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.OpenMajorArticlesGoal], limit: com.histmersion.domain.automation.managers.ManagerSupervisor.OpenMajorArticlesLimit): List[(com.histmersion.domain.automation.managers.ManagerSupervisor.OpenMajorArticlesResultPart1, com.histmersion.domain.automation.managers.ManagerSupervisor.OpenMajorArticlesResultPart2)] =
    Transactor(ds).read(ManagerRepo.openMajorArticles(id, ArticleReview, tags, goal, limit))
  def unreviewedArticles(id: com.histmersion.domain.automation.managers.ManagerSupervisor.UnreviewedArticlesId, tags: List[com.histmersion.domain.automation.managers.ManagerSupervisor.UnreviewedArticlesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.UnreviewedArticlesGoal], limit: com.histmersion.domain.automation.managers.ManagerSupervisor.UnreviewedArticlesLimit): List[com.histmersion.domain.automation.managers.ManagerSupervisor.UnreviewedArticlesResult] =
    Transactor(ds).read(ManagerRepo.unreviewedArticles(id, ArticleReview, tags, goal, limit))
  def staleCandidates(
      id: com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesId,
      tags: List[com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesTags],
      goal: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesGoal],
      limit: com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesLimit
  ): List[(com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesResultPart1, com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesResultPart2, Option[com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesResultPart3], Option[com.histmersion.domain.automation.managers.ManagerSupervisor.StaleCandidatesResultPart4])] =
    Transactor(ds).read(ManagerRepo.staleCandidates(id, ArticleReview, tags, goal, limit))
  def extendCandidates(
      id: com.histmersion.domain.automation.managers.ManagerSupervisor.ExtendCandidatesId,
      tags: List[com.histmersion.domain.automation.managers.ManagerSupervisor.ExtendCandidatesTags],
      goal: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.ExtendCandidatesGoal],
      limit: com.histmersion.domain.automation.managers.ManagerSupervisor.ExtendCandidatesLimit
  ): List[(com.histmersion.domain.automation.managers.ManagerSupervisor.ExtendCandidatesResultPart1, Option[com.histmersion.domain.automation.managers.ManagerSupervisor.ExtendCandidatesResultPart2])] =
    Transactor(ds).read(ManagerRepo.extendCandidates(id, tags, goal, limit))
  def articleFacets(slug: com.histmersion.domain.automation.managers.ManagerSupervisor.ArticleFacetsSlug): (List[com.histmersion.domain.automation.managers.ManagerSupervisor.ArticleFacetsResultPart1], List[com.histmersion.domain.automation.managers.ManagerSupervisor.ArticleFacetsResultPart2]) = Transactor(ds).read(ManagerRepo.articleFacets(slug))

  def progress(run: com.histmersion.domain.automation.managers.ManagerSupervisor.ProgressRun): com.histmersion.domain.automation.managers.ManagerSupervisor.ProgressResult = agents.runs.get(run).flatMap(_.progress).filter(_.nonEmpty).getOrElse("")
  def stepOutput(run: com.histmersion.domain.automation.managers.ManagerSupervisor.StepOutputRun, step: com.histmersion.domain.automation.managers.ManagerSupervisor.StepOutputStep): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.StepOutputResult] =
    agents.runs
      .steps(run, 100000)
      .filter(s => s.name == step && s.status == StepStatus.Done.wire)
      .lastOption
      .flatMap(_.output)
  def approvalsOf(run: com.histmersion.domain.automation.managers.ManagerSupervisor.ApprovalsOfRun, kind: com.histmersion.domain.automation.Approval.Kind): List[ApprovalInfo] =
    agents.approvalStore.approvalsOf(run, Some(kind.transformInto[com.histmersion.api.automation.Approval.Kind]))
  def unsettledOn(target: com.histmersion.domain.automation.managers.ManagerSupervisor.UnsettledOnTarget): Seq[RunInfo] = agents.runs.unsettledOn(target)
  def runsFor(p: Pipeline, target: com.histmersion.domain.automation.managers.ManagerSupervisor.RunsForTarget): Seq[RunInfo] = agents.runs.forTarget(p, target)
  def slugExists(slug: com.histmersion.domain.automation.managers.ManagerSupervisor.SlugExistsSlug): com.histmersion.domain.automation.managers.ManagerSupervisor.SlugExistsResult = agents.host.articleExists(slug)
  def parentOf(slug: com.histmersion.domain.automation.managers.ManagerSupervisor.ParentOfSlug): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.ParentOfResult] = agents.goals.get(slug).flatMap(_.parentSlug)
  def linkGoal(goal: com.histmersion.domain.automation.managers.ManagerSupervisor.LinkGoalGoal, kind: Goal.Plans.LinkKind, ref: com.histmersion.domain.automation.managers.ManagerSupervisor.LinkGoalRef, note: Option[com.histmersion.domain.automation.managers.ManagerSupervisor.LinkGoalNote]): Unit =
    val _ = agents.goals.link(goal, kind.transformInto[com.histmersion.domain.automation.Goal.LinkKind], ref, note)
  def goalLinks(goal: com.histmersion.domain.automation.managers.ManagerSupervisor.GoalLinksGoal, kind: Goal.Plans.LinkKind): List[com.histmersion.domain.automation.managers.ManagerSupervisor.GoalLinksResult] =
    agents.goals.links(goal).filter(_.kind == kind).map(_.ref)
  def sharedIngestLeft(now: Instant): Option[com.histmersion.domain.automation.managers.ManagerSupervisor.SharedIngestLeftResult] = agents.autopilot.sharedIngestLeft(now)
  def autopilotSoon(): Unit = agents.autopilot.soon()

  // ── a run settled ──────────────────────────────────────────────────────────────────────────────

  /** [[Engine.onSettled]]: a manager's run settled — an event (a stall named as one), and a tick soon. */
  def settled(r: RunInfo): Unit =
    r.managerId.foreach { mid =>
      val stalled = r.error.exists(_.startsWith(PauseReason.Stalled.wire + ":"))
      val text = s"${r.target}: run ${r.status}" + r.error.filter(_.nonEmpty).fold("")(e => s" — ${e.take(300)}")
      agents.managerStore.event(mid, r.managerItem, Some(r.id), if stalled then Feed.Stall else Feed.Settled, text)
      nudge()
    }

object ManagerSupervisor:

  /** The priority a prioritise hint gives when it names none (a person's enqueue is 10). */
  type EnsureResult = EnsureResult.T
  object EnsureResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnsureId = EnsureId.T
  object EnsureId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnsureGoal = EnsureGoal.T
  object EnsureGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningResult = RunningResult.T
  object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByItemPart1 = ByItemPart1.T
  object ByItemPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TickResult = TickResult.T
  object TickResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedId = GuardedId.T
  object GuardedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedOutPart1 = GuardedOutPart1.T
  object GuardedOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedOutPart2 = GuardedOutPart2.T
  object GuardedOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateOutPart1 = EvaluateOutPart1.T
  object EvaluateOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateOutPart2 = EvaluateOutPart2.T
  object EvaluateOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryRun = RetryRun.T
  object RetryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryFailures = RetryFailures.T
  object RetryFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryNote = RetryNote.T
  object RetryNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryOutPart1 = RetryOutPart1.T
  object RetryOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryOutPart2 = RetryOutPart2.T
  object RetryOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepError = EvaluateStepError.T
  object EvaluateStepError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepOutPart1 = EvaluateStepOutPart1.T
  object EvaluateStepOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepOutPart2 = EvaluateStepOutPart2.T
  object EvaluateStepOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFactsRun = ApprovalFactsRun.T
  object ApprovalFactsRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFactsNone = ApprovalFactsNone.T
  object ApprovalFactsNone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewId = ChainReviewId.T
  object ChainReviewId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewSlug = ChainReviewSlug.T
  object ChainReviewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewWhy = ChainReviewWhy.T
  object ChainReviewWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewOutPart1 = ChainReviewOutPart1.T
  object ChainReviewOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewOutPart2 = ChainReviewOutPart2.T
  object ChainReviewOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPlanResult = ApplyPlanResult.T
  object ApplyPlanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPlanOutPart1 = ApplyPlanOutPart1.T
  object ApplyPlanOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPlanOutPart2 = ApplyPlanOutPart2.T
  object ApplyPlanOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseHoldsOutPart1 = ReleaseHoldsOutPart1.T
  object ReleaseHoldsOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseHoldsOutPart2 = ReleaseHoldsOutPart2.T
  object ReleaseHoldsOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleOnApprovalAid = SettleOnApprovalAid.T
  object SettleOnApprovalAid extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleOnApprovalOutPart1 = SettleOnApprovalOutPart1.T
  object SettleOnApprovalOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleOnApprovalOutPart2 = SettleOnApprovalOutPart2.T
  object SettleOnApprovalOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MigrateSchedulesResult = MigrateSchedulesResult.T
  object MigrateSchedulesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FireCronResult = FireCronResult.T
  object FireCronResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FireCronOutPart1 = FireCronOutPart1.T
  object FireCronOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FireCronOutPart2 = FireCronOutPart2.T
  object FireCronOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchAllOutPart1 = DispatchAllOutPart1.T
  object DispatchAllOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchAllOutPart2 = DispatchAllOutPart2.T
  object DispatchAllOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedOptId = GuardedOptId.T
  object GuardedOptId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedOptOutPart1 = GuardedOptOutPart1.T
  object GuardedOptOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedOptOutPart2 = GuardedOptOutPart2.T
  object GuardedOptOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleNowResult = SettleNowResult.T
  object SettleNowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleNowWhy = SettleNowWhy.T
  object SettleNowWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchResult = DispatchResult.T
  object DispatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchOutPart1 = DispatchOutPart1.T
  object DispatchOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchOutPart2 = DispatchOutPart2.T
  object DispatchOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartResult = StartResult.T
  object StartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartWhat = StartWhat.T
  object StartWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOutPart1 = StartOutPart1.T
  object StartOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOutPart2 = StartOutPart2.T
  object StartOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PipelineOfItemKind = PipelineOfItemKind.T
  object PipelineOfItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksResult = BlocksResult.T
  object BlocksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksKind = BlocksKind.T
  object BlocksKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FireResult = FireResult.T
  object FireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FactsRun = FactsRun.T
  object FactsRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetResultPart2 = TargetResultPart2.T
  object TargetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetItemKind = TargetItemKind.T
  object TargetItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetRaw = TargetRaw.T
  object TargetRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventId = EventId.T
  object EventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventItem = EventItem.T
  object EventItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventRun = EventRun.T
  object EventRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventFeed = EventFeed.T
  object EventFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventText = EventText.T
  object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfId = LastEventOfId.T
  object LastEventOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfFeed = LastEventOfFeed.T
  object LastEventOfFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsResult = CountEventsResult.T
  object CountEventsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsId = CountEventsId.T
  object CountEventsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsFeed = CountEventsFeed.T
  object CountEventsFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResultPart1 = UpsertResultPart1.T
  object UpsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResultPart2 = UpsertResultPart2.T
  object UpsertResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertId = UpsertId.T
  object UpsertId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertKind = UpsertKind.T
  object UpsertKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertTarget = UpsertTarget.T
  object UpsertTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertPriority = UpsertPriority.T
  object UpsertPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertReopen = UpsertReopen.T
  object UpsertReopen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemItemId = ItemItemId.T
  object ItemItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForId = ItemForId.T
  object ItemForId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForKind = ItemForKind.T
  object ItemForKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForTarget = ItemForTarget.T
  object ItemForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemResult = HasItemResult.T
  object HasItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemId = HasItemId.T
  object HasItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemKind = HasItemKind.T
  object HasItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemTarget = HasItemTarget.T
  object HasItemTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleResult = RestaleResult.T
  object RestaleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleItemId = RestaleItemId.T
  object RestaleItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestalePriority = RestalePriority.T
  object RestalePriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleWhy = RestaleWhy.T
  object RestaleWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResult = PendingResult.T
  object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingId = PendingId.T
  object PendingId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart1 = OpenKeysResultPart1.T
  object OpenKeysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart2 = OpenKeysResultPart2.T
  object OpenKeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysId = OpenKeysId.T
  object OpenKeysId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedResult = EvaluatedResult.T
  object EvaluatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedItemId = EvaluatedItemId.T
  object EvaluatedItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedRun = EvaluatedRun.T
  object EvaluatedRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedFailures = EvaluatedFailures.T
  object EvaluatedFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedOutcome = EvaluatedOutcome.T
  object EvaluatedOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsId = HintsId.T
  object HintsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateResult = StateResult.T
  object StateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateId = StateId.T
  object StateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart1 = OpenMajorArticlesResultPart1.T
  object OpenMajorArticlesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart2 = OpenMajorArticlesResultPart2.T
  object OpenMajorArticlesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesId = OpenMajorArticlesId.T
  object OpenMajorArticlesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesTags = OpenMajorArticlesTags.T
  object OpenMajorArticlesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesGoal = OpenMajorArticlesGoal.T
  object OpenMajorArticlesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesLimit = OpenMajorArticlesLimit.T
  object OpenMajorArticlesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesResult = UnreviewedArticlesResult.T
  object UnreviewedArticlesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesId = UnreviewedArticlesId.T
  object UnreviewedArticlesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesTags = UnreviewedArticlesTags.T
  object UnreviewedArticlesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesGoal = UnreviewedArticlesGoal.T
  object UnreviewedArticlesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesLimit = UnreviewedArticlesLimit.T
  object UnreviewedArticlesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart1 = StaleCandidatesResultPart1.T
  object StaleCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart2 = StaleCandidatesResultPart2.T
  object StaleCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart3 = StaleCandidatesResultPart3.T
  object StaleCandidatesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart4 = StaleCandidatesResultPart4.T
  object StaleCandidatesResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesId = StaleCandidatesId.T
  object StaleCandidatesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesTags = StaleCandidatesTags.T
  object StaleCandidatesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesGoal = StaleCandidatesGoal.T
  object StaleCandidatesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesLimit = StaleCandidatesLimit.T
  object StaleCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesResultPart1 = ExtendCandidatesResultPart1.T
  object ExtendCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesResultPart2 = ExtendCandidatesResultPart2.T
  object ExtendCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesId = ExtendCandidatesId.T
  object ExtendCandidatesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesTags = ExtendCandidatesTags.T
  object ExtendCandidatesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesGoal = ExtendCandidatesGoal.T
  object ExtendCandidatesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesLimit = ExtendCandidatesLimit.T
  object ExtendCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart1 = ArticleFacetsResultPart1.T
  object ArticleFacetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart2 = ArticleFacetsResultPart2.T
  object ArticleFacetsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsSlug = ArticleFacetsSlug.T
  object ArticleFacetsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressResult = ProgressResult.T
  object ProgressResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressRun = ProgressRun.T
  object ProgressRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputResult = StepOutputResult.T
  object StepOutputResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputRun = StepOutputRun.T
  object StepOutputRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputStep = StepOutputStep.T
  object StepOutputStep extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsOfRun = ApprovalsOfRun.T
  object ApprovalsOfRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnsettledOnTarget = UnsettledOnTarget.T
  object UnsettledOnTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsForTarget = RunsForTarget.T
  object RunsForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugExistsResult = SlugExistsResult.T
  object SlugExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugExistsSlug = SlugExistsSlug.T
  object SlugExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentOfResult = ParentOfResult.T
  object ParentOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentOfSlug = ParentOfSlug.T
  object ParentOfSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoalGoal = LinkGoalGoal.T
  object LinkGoalGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoalRef = LinkGoalRef.T
  object LinkGoalRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoalNote = LinkGoalNote.T
  object LinkGoalNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalLinksResult = GoalLinksResult.T
  object GoalLinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalLinksGoal = GoalLinksGoal.T
  object GoalLinksGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SharedIngestLeftResult = SharedIngestLeftResult.T
  object SharedIngestLeftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PurposeResult = PurposeResult.T
  object PurposeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val HintPriority = 5

  /** A person's enqueue (the Review button, `agent manager … enqueue`). */
  val PersonPriority = 10

  /** A review a compile or an extension chained (§4: origin chain, priority 2). */
  val ChainPriority = 2

  /** Stale candidates read per article the selection has room for (most are not due yet). */
  val StaleLookahead = 5

  /** What a manager of kind `k` does, for its created-paused event. */
  def purpose(k: Kind): com.histmersion.domain.automation.managers.ManagerSupervisor.PurposeResult = k match
    case Kind.Review => "reviewing"
    case Kind.Enrich => "enriching"
    case Kind.Seed   => "planning and compiling articles"
    case Kind.Watch  => "watching"
