package com.histmersion.domain.automation.managers

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.goals.GoalFocusService
import com.histmersion.domain.automation.scheduler.SchedulerService

import com.histmersion.domain.automation.worklists.WorkListControllers

import com.augustnagro.magnum.transact
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.domain.ManagerPolicy.{Next, ReviewSettings, StepFacts}
import com.histmersion.api.Managers.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.{AgentRepo, ManagerRepo}

import java.time.Instant
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import javax.sql.DataSource

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
    agents: AgentsHandlers,
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
  def ensure(k: Kind, id: String, goal: Option[String]): Boolean =
    val auto = agents.autopilot.on
    val made = ManagerRepo.create(ds, id, k, goal, if auto then State.Active else State.Paused, ManagerConfig())
    if made then
      ManagerRepo.event(
        ds,
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
  def running: Boolean = thread.isDefined

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
  private val byItem: Map[String, ManagerKindDefinition] = kinds.flatMap(k => k.items.map(_ -> k)).toMap

  /** One pass at `now`. Returns what was done, one line each (for a test or a log). Never throws for one item's or one
    * manager's sake.
    */

  def tick(now: Instant): List[String] = synchronized {
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
      val managers = ManagerRepo.list(ds).filter(m => State.parse(m.state).exists(_ != State.Stopped))
      managers.foreach { m =>
        guarded(m.id, out)(evaluate(m, now, out))
        guarded(m.id, out)(releaseHolds(m, now, out))
      }
      // each kind's housework (the watch kind's seeding, ingest backlog and acquisition re-check), before any
      // sweep is dispatched (a sweep gets what it leaves)
      kinds.flatMap(_.housework(now, agents.drain.draining)).foreach((name, work) => out ++= guarded(name, out)(work()))
      // the ones that may start runs, refilled, then dispatched round-robin over the global room
      val live = ManagerRepo
        .list(ds)
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
        val _ = ManagerRepo.pruneEvents(ds, mcfg.keepEventsDays)
      out.result()
  }

  private def guarded[A](id: String, out: collection.mutable.Builder[String, List[String]])(f: => A)(using
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

  def settings(m: ManagerInfo): ReviewSettings = ReviewSettings.forKind(kindOf(m), m.config, managerDefaults)

  // ── 1. evaluate ────────────────────────────────────────────────────────────────────────────────

  private def evaluate(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val s = settings(m)
    ManagerRepo.toEvaluate(ds, m.id).foreach {
      case (item, None) =>
        // its run row is gone (deleted by hand): the item goes back to the queue
        if ManagerRepo.requeue(ds, item.id, "its run disappeared; queued again") then
          ManagerRepo.event(
            ds,
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
      run: String,
      notBefore: Instant,
      failures: Int,
      note: String,
      out: collection.mutable.Builder[String, List[String]]
  ): Unit =
    if ManagerRepo.evaluated(
        ds,
        item.id,
        Some(run),
        ItemStatus.Pending,
        Some(notBefore),
        Some(failures),
        item.state,
        Some(s"$note; retry after $notBefore")
      )
    then
      ManagerRepo.event(
        ds,
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
      error: Option[String],
      now: Instant,
      out: collection.mutable.Builder[String, List[String]],
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
      st,
      error,
      kind.facts(this, item, run),
      now
    ) match
      case Next.Done(outcome, state) =>
        if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Done, None, None, state, Some(outcome)) then
          ManagerRepo.event(
            ds,
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
        if ManagerRepo.evaluated(
            ds,
            item.id,
            Some(run),
            ItemStatus.Held,
            None,
            None,
            item.state,
            Some(note),
            Some(hold)
          )
        then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Held, s"${item.target} (${item.kind}): $note")
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
        if ManagerRepo.evaluated(
            ds,
            item.id,
            Some(run),
            if failed then ItemStatus.Failed else ItemStatus.Stopped,
            None,
            Option.when(failed)(item.failures + 1),
            state,
            Some(text)
          )
        then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Stopped, s"${item.target} (${item.kind}): $text")
          out += s"${item.target}: stopped"
      case Next.Again(notBefore, state, note) =>
        if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Pending, notBefore, None, state, Some(note)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note")

  /** The approval of `kind` a run opened: none (nothing passed its checks), open (the item waits for it), or decided
    * already (approved automatically, say).
    */
  def approvalFacts(run: String, kind: ApprovalKind, none: String): StepFacts =
    AgentRepo.approvalsOf(ds, run, Some(kind)).lastOption match
      case None                                          => StepFacts.Finished(none)
      case Some(a) if a.state == ApprovalState.Open.wire =>
        StepFacts.Waiting(a.id, s"approval #${a.id} waits for a person")
      case Some(a) =>
        StepFacts.Finished(s"approval #${a.id} " + ManagerPolicy.decided(a.state, a.result).getOrElse(a.state))

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened.
    */
  def chainReview(
      id: String,
      slug: String,
      why: String,
      out: collection.mutable.Builder[String, List[String]]
  ): Unit =
    val (itemId, isNew) =
      ManagerRepo.upsert(ds, id, ArticleReview, slug, ItemOrigin.Chain, ManagerSupervisor.ChainPriority, reopen = true)
    ManagerRepo.event(
      ds,
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
  def applyPlan(a: ApprovalInfo, out: collection.mutable.Builder[String, List[String]] = List.newBuilder[String]): Int =
    (a.subject, a.articles) match
      case (Some(goal), Some(plan)) if a.state == ApprovalState.Approved.wire =>
        val id = seedId(goal)
        val _ = ensure(Kind.Seed, id, Some(goal))
        val added = plan.articles.count { p =>
          val spec = ArticleSpec(p.title, p.kind, p.filter, p.why)
          ManagerRepo
            .upsert(ds, id, ArticleCompile, p.slug, ItemOrigin.Chain, 0, reopen = false, ItemState(spec = Some(spec)))
            ._2
        }
        if added > 0 then
          ManagerRepo.event(
            ds,
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

  private def releaseHolds(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    ManagerRepo.withStatus(ds, m.id, ItemStatus.Held).foreach { i =>
      i.hold.flatMap(_.untilApproval) match
        case Some(aid) => settleOnApproval(m, i, aid, out)
        case None      =>
          if i.hold
              .exists(ManagerPolicy.released(_, now, ManagerRepo.sourceExists(ds, _))) && ManagerRepo.release(ds, i.id)
          then
            ManagerRepo.event(
              ds,
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
      aid: Long,
      out: collection.mutable.Builder[String, List[String]]
  ): Unit =
    AgentRepo.approval(ds, aid) match
      case None =>
        if ManagerRepo.settle(ds, i.id, ItemStatus.Stopped, s"approval #$aid is gone", List(ItemStatus.Held)) then
          ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Stopped, s"${i.target}: approval #$aid is gone")
      case Some(a) =>
        ManagerPolicy.decided(a.state, a.result).foreach { decided =>
          val ok = a.state == ApprovalState.Approved.wire
          val outcome = s"approval #$aid $decided"
          if ManagerRepo.settle(
              ds,
              i.id,
              if ok then ItemStatus.Done else ItemStatus.Stopped,
              outcome,
              List(ItemStatus.Held)
            )
          then
            ManagerRepo.event(
              ds,
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
  def migrateSchedules(now: Instant): List[String] =
    agents.goals.scheduleGoals().flatMap { g =>
      val id = watchId(g)
      try
        val moved = transact(ds) {
          val rows = agents.goals.takeSchedulesIn(g)
          Option.when(rows.nonEmpty) {
            val existing = ManagerRepo.get(ds, id)
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
  private def fireCron(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Boolean =
    if !cfg.scheduler.enabled then false
    else
      val cron = m.cron.flatMap(c => Cron.parse(c).toOption)
      ManagerPolicy.cronStep(cron, m.nextTickAt.flatMap(ManagerPolicy.parseInstant), now) match
        case ManagerPolicy.CronStep.Idle           => false
        case ManagerPolicy.CronStep.Schedule(next) =>
          val _ = ManagerRepo.setNextTick(ds, m.id, next)
          false
        case ManagerPolicy.CronStep.Fire(next) =>
          val _ = ManagerRepo.setNextTick(ds, m.id, next)
          val what = byKind.get(kindOf(m)).fold(Nil)(_.fire(this, m, now))
          ManagerRepo.event(
            ds,
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
      out: collection.mutable.Builder[String, List[String]]
  ): Unit =
    val byId = ms.map(m => m.id -> m).toMap
    val decision = agents.scheduler.pass(ms, now, agents.drain.block) { (m, item) =>
      if blocks(item.kind, AgentRepo.unsettledOn(ds, item.target)) then Left("another run is writing its target")
      else
        guardedOpt(m.id, out)(dispatch(m, settings(m), item, now, out)) match
          case Some(_) => Right(())
          case None    =>
            if ManagerRepo.item(ds, item.id).exists(_.status == ItemStatus.Pending.wire) then
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
      val pending = math.max(0, ManagerRepo.counts(ds, m.id).pending)
      val base = ManagerPolicy.statusNote(
        State.Active,
        starved,
        if full then 0 else 1,
        pending,
        waits.size,
        ManagerRepo.running(ds, m.id),
        blocked
      )
      val note = waits.headOption.filter(_ => blocked.isEmpty && starved.isEmpty) match
        case Some(w) => s"$base; the scheduler: ${w.code} — ${w.why.take(200)}"
        case None    => base
      ManagerRepo.setStatusNote(ds, m.id, note)
      // a starved (or budget-blocked) manager says so once per change, not every tick
      blocked.orElse(starved.map(x => s"starved: $x")).foreach { b =>
        if !ManagerRepo.lastEventOf(ds, m.id, Feed.Starved).exists(_.text == b) then
          ManagerRepo.event(ds, m.id, None, None, Feed.Starved, b)
      }
    }
    // paused/stopped managers show it too
    ManagerRepo.list(ds).filterNot(m => byId.contains(m.id)).foreach { m =>
      State
        .parse(m.state)
        .foreach(st =>
          ManagerRepo
            .setStatusNote(ds, m.id, ManagerPolicy.statusNote(st, None, 0, m.counts.pending, 0, m.inFlight, None))
        )
    }

  private def guardedOpt[A](id: String, out: collection.mutable.Builder[String, List[String]])(
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
  def settleNow(m: ManagerInfo, item: ManagerItem, status: ItemStatus, why: String): Option[String] =
    if ManagerRepo.settle(ds, item.id, status, why, List(ItemStatus.Pending)) then
      ManagerRepo.event(
        ds,
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
      out: collection.mutable.Builder[String, List[String]]
  ): Option[String] =
    if blocks(item.kind, AgentRepo.unsettledOn(ds, item.target)) then None // another run writes it: wait
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
      what: String,
      out: collection.mutable.Builder[String, List[String]]
  ): Option[String] =
    val roles = pipelineOf(item.kind).roles
    val req =
      req0.copy(modelFor = m.config.models.getOrElse(Nil).filter(o => Role.parse(o.role).exists(roles.contains)))
    agents.prepare(req, None) match
      case Left(e) =>
        val note = s"not started: ${e.message}".take(500)
        if !ManagerRepo.lastEventOf(ds, m.id, Feed.Starved).exists(_.text == note) then
          ManagerRepo.event(ds, m.id, Some(item.id), None, Feed.Starved, note)
        None
      case Right(pr) =>
        val hints = ManagerPolicy.notesFor(ManagerRepo.hints(ds, m.id, live = true), item.id)
        val notes = hints.map(_.text.trim)
        val params = tweak(pr.params).copy(notes = Option.when(notes.nonEmpty)(notes))
        val id =
          try
            Some(transact(ds) {
              val id = agents.jobRuns.createIn(
                AgentRepo.runRow(
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
  def pipelineOf(itemKind: String): Pipeline =
    byItem
      .get(itemKind)
      .orElse(kinds.headOption)
      .getOrElse(throw IllegalStateException("no manager kind is registered"))
      .pipelineOf(itemKind)

  /** [[ManagerPolicy.blocks]] over the unsettled runs on an item's target. */
  def blocks(kind: String, unsettled: Seq[Agents.RunInfo]): Boolean =
    ManagerPolicy.blocks(kind, pipelineOf(kind).wire, unsettled.map(_.pipeline))

  /** The roles a manager of kind `k` runs (what its `models` setting may name). */
  def roles(k: Kind): List[Role] = itemKinds(k).filterNot(_ == OaIngest).map(pipelineOf).flatMap(_.roles).distinct

  // ── what a kind is given (ManagerContext) ─────────────────────────────────────────────────────

  /** An item of a kind no registered kind drives: settled as a finished one-run item. */
  private val Unknown: ManagerKindDefinition = new ManagerKindDefinition:
    val kind: Kind = Kind.Review
    override def items: List[String] = Nil
    def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[String] = Nil
    def facts(c: ManagerContext, item: ManagerItem, run: String): StepFacts = StepFacts.Finished("enriched")
    def dispatch(
        c: ManagerContext,
        m: ManagerInfo,
        s: ReviewSettings,
        item: ManagerItem,
        now: Instant,
        out: collection.mutable.Builder[String, List[String]]
    ): Option[String] = None
    def target(m: ManagerInfo, itemKind: String, raw: String): Either[ApiError, String] =
      Left(ApiError(s"unknown item kind '$itemKind'"))
    def pipelineOf(itemKind: String): Pipeline = ManagerSupervisor.this.pipelineOf(itemKind)
    def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig = ManagerConfig()

  def event(id: String, item: Option[Long], run: Option[String], feed: String, text: String): Unit =
    ManagerRepo.event(ds, id, item, run, feed, text)
  def lastEventOf(id: String, feed: String): Option[ManagerEvent] = ManagerRepo.lastEventOf(ds, id, feed)
  def countEvents(id: String, feed: String, since: Instant): Int = ManagerRepo.countEvents(ds, id, feed, since)
  def upsert(
      id: String,
      kind: String,
      target: String,
      origin: ItemOrigin,
      priority: Int,
      reopen: Boolean,
      state: ItemState = ItemState()
  ): (Long, Boolean) = ManagerRepo.upsert(ds, id, kind, target, origin, priority, reopen, state)
  def item(itemId: Long): Option[ManagerItem] = ManagerRepo.item(ds, itemId)
  def itemFor(id: String, kind: String, target: String): Option[ManagerItem] = ManagerRepo.itemFor(ds, id, kind, target)
  def hasItem(id: String, kind: String, target: String): Boolean = ManagerRepo.hasItem(ds, id, kind, target)
  def restale(itemId: Long, priority: Int, why: String): Boolean = ManagerRepo.restale(ds, itemId, priority, why)
  def pending(id: String): Int = ManagerRepo.counts(ds, id).pending
  def openKeys(id: String): List[(String, String)] = ManagerRepo.openKeys(ds, id)
  def evaluated(
      itemId: Long,
      run: Option[String],
      status: ItemStatus,
      notBefore: Option[Instant],
      failures: Option[Int],
      state: ItemState,
      outcome: Option[String]
  ): Boolean = ManagerRepo.evaluated(ds, itemId, run, status, notBefore, failures, state, outcome)
  def hints(id: String): List[ManagerHint] = ManagerRepo.hints(ds, id, live = true)
  def state(id: String): Option[String] = ManagerRepo.get(ds, id).map(_.state)
  def openMajorArticles(id: String, tags: List[String], goal: Option[String], limit: Int): List[(String, Int)] =
    ManagerRepo.openMajorArticles(ds, id, ArticleReview, tags, goal, limit)
  def unreviewedArticles(id: String, tags: List[String], goal: Option[String], limit: Int): List[String] =
    ManagerRepo.unreviewedArticles(ds, id, ArticleReview, tags, goal, limit)
  def staleCandidates(
      id: String,
      tags: List[String],
      goal: Option[String],
      limit: Int
  ): List[(String, String, Option[Int], Option[Long])] =
    ManagerRepo.staleCandidates(ds, id, ArticleReview, tags, goal, limit)
  def extendCandidates(
      id: String,
      tags: List[String],
      goal: Option[String],
      limit: Int
  ): List[(String, Option[String])] =
    ManagerRepo.extendCandidates(ds, id, tags, goal, limit)
  def articleFacets(slug: String): (List[String], List[String]) = ManagerRepo.articleFacets(ds, slug)

  def progress(run: String): String = AgentRepo.get(ds, run).flatMap(_.progress).filter(_.nonEmpty).getOrElse("")
  def stepOutput(run: String, step: String): Option[String] =
    AgentRepo
      .steps(ds, run, 100000)
      .filter(s => s.name == step && s.status == StepStatus.Done.wire)
      .lastOption
      .flatMap(_.output)
  def approvalsOf(run: String, kind: ApprovalKind): List[ApprovalInfo] = AgentRepo.approvalsOf(ds, run, Some(kind))
  def unsettledOn(target: String): Seq[RunInfo] = AgentRepo.unsettledOn(ds, target)
  def runsFor(p: Pipeline, target: String): Seq[RunInfo] = AgentRepo.forTarget(ds, p, target)
  def slugExists(slug: String): Boolean = AgentRepo.slugExists(ds, slug)
  def parentOf(slug: String): Option[String] = agents.goals.get(slug).flatMap(_.parentSlug)
  def linkGoal(goal: String, kind: GoalPlans.LinkKind, ref: String, note: Option[String]): Unit =
    val _ = agents.goals.link(goal, kind, ref, note)
  def goalLinks(goal: String, kind: GoalPlans.LinkKind): List[String] =
    agents.goals.links(goal).filter(_.kind == kind).map(_.ref)
  def sharedIngestLeft(now: Instant): Option[Int] = agents.autopilot.sharedIngestLeft(now)
  def autopilotSoon(): Unit = agents.autopilot.soon()

  // ── a run settled ──────────────────────────────────────────────────────────────────────────────

  /** [[Engine.onSettled]]: a manager's run settled — an event (a stall named as one), and a tick soon. */
  def settled(r: RunInfo): Unit =
    r.managerId.foreach { mid =>
      val stalled = r.error.exists(_.startsWith(PauseReason.Stalled.wire + ":"))
      val text = s"${r.target}: run ${r.status}" + r.error.filter(_.nonEmpty).fold("")(e => s" — ${e.take(300)}")
      ManagerRepo.event(ds, mid, r.managerItem, Some(r.id), if stalled then Feed.Stall else Feed.Settled, text)
      nudge()
    }

/** What a guarded step answers when it failed. */
final private[automation] case class Zero[A](value: A)
private[automation] object Zero:
  given Zero[Unit] = Zero(())
  given Zero[Boolean] = Zero(false)
  given Zero[List[String]] = Zero(Nil)

object ManagerSupervisor:

  /** The priority a prioritise hint gives when it names none (a person's enqueue is 10). */
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
