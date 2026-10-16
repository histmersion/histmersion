package com.histmersion.server.agents

import com.augustnagro.magnum.transact
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.ManagerPolicy.{Candidate, Next, ReviewFacts, ReviewSettings}
import com.histmersion.core.Managers.*
import com.histmersion.core.Queue.{QueueAdd, QueuePatch}
import com.histmersion.db.{AgentRepo, ManagerRepo, QueueRepo, ReviewLedgerRepo}

import java.time.Instant
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import javax.sql.DataSource

/** The process managers' supervisor (docs/research-process-managers.md §3.1): a daemon thread that
  * ticks every `agents.managers.tick-seconds`, and at once when a manager's run settles
  * ([[Engine.onSettled]] → [[nudge]]). One [[tick]] — callable directly, with a fixed clock, by a test:
  *
  *  1. evaluate the items whose run is terminal (keyed by the run: never twice);
  *  2. release the holds whose date passed or whose awaited source exists;
  *  3. refill an active manager below its low water (the selection);
  *  4. dispatch by capacity — the global `agents.max-concurrent-runs` (every queued or running run
  *     counts, a person's too), the manager's `maxRuns`/`maxOpen`, its pools' calls in flight — never on
  *     a target another unsettled run is writing, never when a role's chain is starved or a day's or
  *     month's spend reached its cap;
  *  5. the status line, and an event for every change.
  *
  * A dispatch is ONE transaction — the run row, the item's compare-and-set to active, the hints it
  * reached, the event — and the run is submitted only after the commit (a crash in between leaves a
  * queued run, which the engine's restart re-enqueues). */
final class ManagerSupervisor(ds: DataSource, cfg: AgentsConfig, agents: AgentsHandlers, h: com.histmersion.server.Handlers):

  private val mcfg = cfg.managers

  /** The ticking thread; a `var` because it starts after construction and stops in tests. */
  @volatile private var thread: Option[Thread] = None
  @volatile private var stopping = false
  private val nudges = LinkedBlockingQueue[String]()

  /** A run of a manager settled: tick soon (coalesced). */
  def nudge(): Unit = { val _ = nudges.offer("settled") }

  /** The library-wide review manager exists — created PAUSED, so deploying never starts spending until
    * a person resumes it. Idempotent. */
  def ensureDefaults(): Unit =
    if ManagerRepo.create(ds, ReviewId, Kind.Review, None, State.Paused, ManagerConfig()) then
      ManagerRepo.event(ds, ReviewId, None, None, Feed.Paused,
        "created paused: resume it to start reviewing (nothing is spent until then)")

  def start(): Unit =
    if cfg.enabled && mcfg.enabled && thread.isEmpty then
      stopping = false
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agent-managers").unstarted(() => loop())
      thread = Some(t)
      t.start()

  def stop(): Unit =
    stopping = true
    thread.foreach(_.interrupt())
    thread = None

  private def loop(): Unit =
    val every = math.max(1, mcfg.tickSeconds).toLong
    try
      while !stopping do
        try { val _ = tick(Instant.now()) }
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent managers tick failed: ${e.getMessage}", e)
        val _ = nudges.poll(every, TimeUnit.SECONDS)
        nudges.clear()
    catch case _: InterruptedException => ()

  /** When the events were last pruned (epoch ms). */
  private val lastPrune = java.util.concurrent.atomic.AtomicLong(0L)

  /** One pass at `now`. Returns what was done, one line each (for a test or a log). Never throws for
    * one item's or one manager's sake. */
  def tick(now: Instant): List[String] = synchronized {
    if !cfg.enabled || !mcfg.enabled then Nil
    else
      val out = List.newBuilder[String]
      val managers = ManagerRepo.list(ds).filter(m => State.parse(m.state).exists(_ != State.Stopped))
      managers.foreach { m =>
        guarded(m.id, out)(evaluate(m, now, out))
        guarded(m.id, out)(releaseHolds(m, now, out))
      }
      // the ones that may start runs, refilled, then dispatched round-robin over the global room
      val live = ManagerRepo.list(ds).filter(m => State.parse(m.state).contains(State.Active) && Kind.parse(m.kind).contains(Kind.Review))
      live.foreach(m => guarded(m.id, out)(refill(m, now, out)))
      dispatchAll(live, now, out)
      if now.toEpochMilli - lastPrune.get() > 3600000L then
        lastPrune.set(now.toEpochMilli)
        val _ = ManagerRepo.pruneEvents(ds, mcfg.keepEventsDays)
      out.result()
  }

  private def guarded(id: String, out: collection.mutable.Builder[String, List[String]])(f: => Unit): Unit =
    try f
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"manager $id: ${e.getClass.getSimpleName}: ${e.getMessage}", e)
        out += s"$id: error ${e.getMessage}"

  def settings(m: ManagerInfo): ReviewSettings = ReviewSettings.of(m.config, mcfg.review, mcfg)

  // ── 1. evaluate ────────────────────────────────────────────────────────────────────────────────

  private def evaluate(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val s = settings(m)
    ManagerRepo.toEvaluate(ds, m.id).foreach {
      case (item, None) =>
        // its run row is gone (deleted by hand): the item goes back to the queue
        if ManagerRepo.requeue(ds, item.id, "its run disappeared; queued again") then
          ManagerRepo.event(ds, m.id, Some(item.id), item.runId, Feed.Evaluated, s"${item.target}: its run disappeared; pending again")
      case (item, Some((status, error))) =>
        val run   = item.runId.get
        val st    = RunState.parse(status).getOrElse(RunState.Failed)
        val ledger = ReviewLedgerRepo.get(ds, item.target).toOption.flatten
        val facts = Option.when(st == RunState.Done)(ledger.map(ReviewFacts.of(_, run)).getOrElse(ReviewFacts(0, 0, 0, omissionChurn = false)))
        val task  = ledger.flatMap(ReviewLedger.taskText(_, run))
        ManagerPolicy.evaluateReview(s, item.target, item.attempts, item.failures, item.state, run, st, error, facts, task, now) match
          case Next.Done(outcome, state) =>
            if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Done, None, None, state, Some(outcome)) then
              ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $outcome")
              out += s"${item.target}: done"
          case Next.Again(notBefore, state, note) =>
            if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Pending, notBefore, None, state, Some(note)) then
              ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note")
              out += s"${item.target}: again"
          case Next.Retry(notBefore, failures, note) =>
            if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Pending, Some(notBefore), Some(failures), item.state,
                Some(s"$note; retry after $notBefore")) then
              ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note; retry after $notBefore")
              out += s"${item.target}: retry"
          case Next.Stop(outcome, task, state) =>
            val failed = st == RunState.Failed
            val status = if failed then ItemStatus.Failed else ItemStatus.Stopped
            val filed  = task.flatMap(t => fileTask(item.target, ledger, t))
            val text   = outcome + filed.fold("")(id => s" (task #$id)")
            if ManagerRepo.evaluated(ds, item.id, Some(run), status, None, Option.when(failed)(item.failures + 1), state, Some(text)) then
              ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Stopped, s"${item.target}: $text")
              out += s"${item.target}: stopped"
    }

  /** The stop's backlog item: the article's own review task (the ledger's, while it is open) is
    * rewritten with the reason, else a new one is added. Returns its id. */
  private def fileTask(slug: String, ledger: Option[ReviewLedger.Ledger], text: String): Option[Int] =
    val open = ledger.flatMap(_.task).flatMap(QueueRepo.get(ds, _))
      .filter(i => i.list == Queue.Kind.Tasks.label && i.status == Queue.Open)
      // the article's collapsed item the ledger lost track of (#934)
      .orElse(QueueRepo.list(ds, Queue.Kind.Tasks, all = false).filter(i => ReviewLedger.articleTask(slug, i.text))
        .sortBy(_.id).headOption)
    open match
      case Some(item) => h.ops.queuePatch(item.id, QueuePatch(text = Some(text))).toOption.map(_.id)
      case None       => h.ops.queueAdd(Queue.Kind.Tasks.label, QueueAdd(text, topic = Some("agent review"))).toOption.map(_.id)

  // ── 2. holds ───────────────────────────────────────────────────────────────────────────────────

  private def releaseHolds(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    ManagerRepo.withStatus(ds, m.id, ItemStatus.Held).foreach { i =>
      if i.hold.exists(ManagerPolicy.released(_, now, ManagerRepo.sourceExists(ds, _))) && ManagerRepo.release(ds, i.id) then
        ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Held, s"${i.target}: hold released (" +
          i.hold.flatMap(h => h.untilSource.map(s => s"$s exists").orElse(h.until.map(u => s"$u passed"))).getOrElse("") + ")")
        out += s"${i.target}: released"
    }

  // ── 3. refill ──────────────────────────────────────────────────────────────────────────────────

  /** Below the low water: the selection — articles with open MAJORs (most first), then never-reviewed
    * ones — added as items, with the standing prioritise hints applied. */
  private def refill(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val s = settings(m)
    val pending = ManagerRepo.counts(ds, m.id).pending
    if pending < s.lowWater && s.refillBatch > 0 then
      val tags = m.config.includeTags.getOrElse(Nil)
      val goal = m.config.includeGoal.filter(_.trim.nonEmpty)
      val majors = ManagerRepo.openMajorArticles(ds, m.id, ArticleReview, tags, goal, s.refillBatch)
      val fresh = if majors.size >= s.refillBatch then Nil
        else ManagerRepo.unreviewedArticles(ds, m.id, ArticleReview, tags, goal, s.refillBatch - majors.size)
      val hints = ManagerRepo.hints(ds, m.id, live = true).filter(_.effect.exists(_.action == HintEffect.Prioritise))
      val added = (majors.map((slug, n) => (slug, 1, s"$n open MAJOR(s)")) ++ fresh.map(slug => (slug, 0, "never reviewed"))).flatMap {
        (slug, base, why) =>
          val prio = boosted(slug, base, hints)
          val (id, isNew) = ManagerRepo.upsert(ds, m.id, ArticleReview, slug, ItemOrigin.Selection, prio, reopen = false)
          Option.when(isNew)(s"$slug ($why${if prio != base then s", priority $prio by a hint" else ""})")
      }
      if added.nonEmpty then
        ManagerRepo.event(ds, m.id, None, None, Feed.Evaluated, s"selected ${Plural.count(added.size, "article")}: ${added.mkString(", ")}".take(2000))
        out += s"${m.id}: selected ${added.size}"

  /** A target's priority under the standing prioritise hints that match it. */
  private def boosted(slug: String, base: Int, hints: List[ManagerHint]): Int =
    if hints.isEmpty then base
    else
      val (tags, goals) = ManagerRepo.articleFacets(ds, slug)
      hints.flatMap(_.effect).filter(e => ManagerPolicy.matches(e, slug, tags, Nil, goals))
        .map(_.priority.getOrElse(ManagerSupervisor.HintPriority)).foldLeft(base)(math.max)

  // ── 4. dispatch ────────────────────────────────────────────────────────────────────────────────

  /** Why nothing may start for any manager now (a spend cap reached), if so. */
  private def budgetBlock(): Option[String] =
    val (day, month) = AgentRepo.spendDayMonth(ds)
    if day >= BigDecimal(cfg.budgets.dailyUsd) then Some(s"budget: today's agent spend reached agents.budgets.daily-usd (${cfg.budgets.dailyUsd})")
    else if month >= BigDecimal(cfg.budgets.monthlyUsd) then Some(s"budget: this month's agent spend reached agents.budgets.monthly-usd (${cfg.budgets.monthlyUsd})")
    else None

  /** Whether every role of review-article has a usable model NOW (picked without spending: no plan
    * reading is taken), and the calls the pools of the picked models take at once. Left = why not. */
  def pools(m: ManagerInfo, now: Instant): Either[String, Option[Int]] =
    val over = m.config.models.getOrElse(Nil)
    val picks = Pipeline.ReviewArticle.roles.map { r =>
      val ref = over.find(o => Role.parse(o.role).contains(r)).map(_.model)
      r -> agents.picker.pick(r, ref, now.toEpochMilli, refresh = None)
    }
    picks.collectFirst { case (r, Left(f)) => (r, f) } match
      case Some((r, PickFailure.Exhausted(_, at, why))) =>
        Left(s"${r.wire} chain ${if why == PauseReason.PlanQuota then "below its plan floor" else "out of allowance"}" +
          at.fold("")(t => s"; frees ~$t"))
      case Some((r, PickFailure.Unavailable(msg))) => Left(s"${r.wire}: ${msg.take(200)}")
      case _ =>
        val plans = picks.collect { case (_, Right(p)) => p.billing }
        val pools = plans.collect { case Billing.Plan(p) => p }.distinct
        if pools.nonEmpty && plans.forall(_.isInstanceOf[Billing.Plan]) then
          Right(Some(pools.map {
            case PlanQuota.PoolId.ClaudePlan  => cfg.quota.claudePlan.maxCallsInFlight
            case PlanQuota.PoolId.ChatgptPlan => cfg.quota.chatgptPlan.maxCallsInFlight
          }.sum))
        else Right(None)

  private def dispatchAll(ms: List[ManagerInfo], now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val budget = budgetBlock()
    val room   = math.max(0, cfg.maxConcurrentRuns - AgentRepo.active(ds))
    val plans = ms.map { m =>
      val s       = settings(m)
      val pending = ManagerRepo.pending(ds, m.id)
      val due     = ManagerPolicy.order(pending.map(i => Candidate(i.id, i.target, i.priority,
        i.notBefore.flatMap(ManagerPolicy.parseInstant)))).filter(ManagerPolicy.due(_, now))
      val starved = if due.isEmpty || budget.isDefined then Right(None) else pools(m, now)
      val cap = starved match
        case Left(_) => 0
        case Right(poolCap) =>
          ManagerPolicy.capacity(cfg.maxConcurrentRuns, AgentRepo.active(ds), s.maxRuns, ManagerRepo.running(ds, m.id),
            s.maxOpen, ManagerRepo.inFlight(ds, m.id), poolCap)
      (m, s, pending, due, starved.left.toOption, if budget.isDefined then 0 else cap)
    }
    // round-robin over the managers, one start each per turn; an item that cannot start now (another
    // run writes its article) gives way to the next one of the same manager
    def tryStart(m: ManagerInfo, s: ReviewSettings, pending: List[ManagerItem], cs: List[Candidate]): (Boolean, List[Candidate]) =
      cs match
        case Nil => (false, Nil)
        case c :: rest =>
          pending.find(_.id == c.id).flatMap(i => guardedOpt(m.id, out)(dispatch(m, s, i, now, out))) match
            case Some(_) => (true, rest)
            case None    => tryStart(m, s, pending, rest)
    @annotation.tailrec
    def go(qs: List[(ManagerInfo, ReviewSettings, List[ManagerItem], Int, List[Candidate])], left: Int, acc: List[String]): List[String] =
      if left <= 0 || !qs.exists((_, _, _, cap, cs) => cap > 0 && cs.nonEmpty) then acc
      else
        val (next, used, st) = qs.foldLeft((Vector.empty[(ManagerInfo, ReviewSettings, List[ManagerItem], Int, List[Candidate])], 0, acc)) {
          case ((nq, used, st), q @ (m, s, pend, cap, cs)) =>
            if cap <= 0 || cs.isEmpty || used >= left then (nq :+ q, used, st)
            else tryStart(m, s, pend, cs) match
              case (true, rest) => (nq :+ (m, s, pend, cap - 1, rest), used + 1, st :+ m.id)
              case (false, _)   => (nq :+ (m, s, pend, cap, Nil), used, st)
        }
        go(next.toList, left - used, st)
    val started = go(plans.map((m, s, pending, due, _, cap) => (m, s, pending, cap, due)), room, Nil)
    plans.foreach { (m, _, pending, due, starved, cap) =>
      val mine = started.count(_ == m.id)
      val note = ManagerPolicy.statusNote(State.Active, starved, math.max(0, cap - mine), pending.size - mine,
        due.size - mine, ManagerRepo.inFlight(ds, m.id), budget)
      ManagerRepo.setStatusNote(ds, m.id, note)
      // a starved (or budget-blocked) manager says so once per change, not every tick
      val block = budget.orElse(starved.map(s => s"starved: $s"))
      block.foreach { b =>
        if !ManagerRepo.lastEventOf(ds, m.id, Feed.Starved).exists(_.text == b) then
          ManagerRepo.event(ds, m.id, None, None, Feed.Starved, b)
      }
    }
    // paused/stopped managers show it too
    ManagerRepo.list(ds).filterNot(m => ms.exists(_.id == m.id)).foreach { m =>
      State.parse(m.state).foreach(st => ManagerRepo.setStatusNote(ds, m.id,
        ManagerPolicy.statusNote(st, None, 0, m.counts.pending, 0, m.inFlight, None)))
    }

  private def guardedOpt[A](id: String, out: collection.mutable.Builder[String, List[String]])(f: => Option[A]): Option[A] =
    try f
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"manager $id: dispatch failed: ${e.getMessage}", e)
        out += s"$id: dispatch error ${e.getMessage}"
        None

  private final class Lost extends RuntimeException("the item moved on")

  /** Start the next run of one item; Some(manager id) when it started. */
  private def dispatch(m: ManagerInfo, s: ReviewSettings, item: ManagerItem, now: Instant,
      out: collection.mutable.Builder[String, List[String]]): Option[String] =
    h.getArticle(item.target) match
      case Left(_) =>
        if ManagerRepo.settle(ds, item.id, ItemStatus.Cancelled, "the article no longer exists", List(ItemStatus.Pending)) then
          ManagerRepo.event(ds, m.id, Some(item.id), None, Feed.Stopped, s"${item.target}: the article no longer exists")
        None
      case Right(_) if AgentRepo.unsettledOn(ds, item.target).nonEmpty =>
        None // another run (any origin) is writing it: wait for it to settle
      case Right(_) =>
        val req = RunRequest(Pipeline.ReviewArticle.wire, item.target, budgetUsd = m.config.budgetUsd, fix = Some(true),
          rounds = Some(math.min(s.rounds, cfg.review.roundsCap)), modelFor = m.config.models.getOrElse(Nil))
        agents.prepare(req, None) match
          case Left(e) =>
            val note = s"not started: ${e.message}".take(500)
            if !ManagerRepo.lastEventOf(ds, m.id, Feed.Starved).exists(_.text == note) then
              ManagerRepo.event(ds, m.id, Some(item.id), None, Feed.Starved, note)
            None
          case Right(pr) =>
            val hints = ManagerPolicy.notesFor(ManagerRepo.hints(ds, m.id, live = true), item.id)
            val notes = hints.map(_.text.trim)
            val params = pr.params.copy(notes = Option.when(notes.nonEmpty)(notes))
            val before = ReviewLedgerRepo.get(ds, item.target).toOption.flatten.map(_.openMajors.size).getOrElse(0)
            val id =
              try
                Some(transact(ds) {
                  val id = AgentRepo.createIn(pr.pipeline, pr.target, pr.model, BigDecimal(pr.usd), pr.tokens, params, None,
                    pr.goal, None, Origin.Manager, pr.dryRun, Some(m.id), Some(item.id))
                  if !ManagerRepo.dispatchIn(item.id, id, item.state.copy(before = Some(before))) then throw Lost()
                  ManagerRepo.usedIn(hints, id)
                  ManagerRepo.eventIn(m.id, Some(item.id), Some(id), Feed.Started,
                    s"${item.target}: run ${item.attempts + 1} (review-article fix, ${params.rounds.getOrElse(s.rounds)} rounds; " +
                      s"$before open MAJOR(s))" + (if notes.nonEmpty then s" with ${Plural.count(notes.size, "note")}" else ""))
                  id
                })
              catch case _: Lost => None
            id.map { runId =>
              agents.engine.submit(runId)
              out += s"${item.target}: started ${runId.take(8)}"
              m.id
            }

  // ── a run settled ──────────────────────────────────────────────────────────────────────────────

  /** [[Engine.onSettled]]: a manager's run settled — an event (a stall named as one), and a tick soon. */
  def settled(r: RunInfo): Unit =
    r.managerId.foreach { mid =>
      val stalled = r.error.exists(_.startsWith(PauseReason.Stalled.wire + ":"))
      val text = s"${r.target}: run ${r.status}" + r.error.filter(_.nonEmpty).fold("")(e => s" — ${e.take(300)}")
      ManagerRepo.event(ds, mid, r.managerItem, Some(r.id), if stalled then Feed.Stall else Feed.Settled, text)
      nudge()
    }

object ManagerSupervisor:
  /** The priority a prioritise hint gives when it names none (a person's enqueue is 10). */
  val HintPriority = 5
  /** A person's enqueue (the Review button, `agent manager … enqueue`). */
  val PersonPriority = 10
