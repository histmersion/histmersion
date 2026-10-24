package com.histmersion.server.agents

import com.augustnagro.magnum.transact
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.ManagerPolicy.{Candidate, Next, ReviewFacts, ReviewSettings, StepFacts}
import com.histmersion.core.Managers.*
import com.histmersion.core.Queue.{QueueAdd, QueuePatch}
import com.histmersion.db.{AgentRepo, GoalLinkRepo, ManagerRepo, QueueRepo, ReviewLedgerRepo}

import java.time.Instant
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import javax.sql.DataSource

/** The process managers' supervisor (docs/research-process-managers.md §3): a daemon thread that
  * ticks every `agents.managers.tick-seconds`, and at once when a manager's run settles
  * ([[Engine.onSettled]] → [[nudge]]). One [[tick]] — callable directly, with a fixed clock, by a test:
  *
  *  1. evaluate the items whose run is terminal (keyed by the run: never twice);
  *  2. release the holds whose date passed or whose awaited source exists, and settle the items held
  *     for an approval a person decided;
  *  3. fire the crons that are due (a watcher's weekly sweeps, a review manager's refill, a seeding
  *     manager's next proposal) and refill an active review manager below its low water (the selection,
  *     with an infer-filter item before the review of an article that records no filter, and the stale
  *     articles last);
  *  4. the CENTRAL SCHEDULER's pass ([[SchedulerService]], #3933): every manager's due items are
  *     candidates of one decision across every job type, by goal priority, job type, aging and the strategy
  *     (`agents.scheduler.strategy`), within the global `agents.max-concurrent-runs` (every queued or
  *     running run counts, a person's too — a run waiting for a person never does), the plan pools' calls in
  *     flight, the budgets and daily caps, a manager's own saved `maxRuns` — never on a target another
  *     unsettled run is writing, never an article's review while its infer-filter item is open, never when a
  *     role's chain is starved;
  *  5. the status line, and an event for every change.
  *
  * Between 3 and 4 the autopilot's FOCUS ([[GoalFocusService.tick]]) runs only to release the holds it set
  * before the central scheduler existed: it no longer holds anything.
  *
  * Between 2 and 3 the watchers' INGEST BACKLOG drains ([[OaBacklog.drain]]): a few of the legal
  * open-access works that waited for a day's allowance are ingested — before any new sweep, which gets only
  * what the backlog leaves of the allowance.
  *
  * Four kinds run: `review` (library-wide: article-review, infer-filter and article-extend items),
  * `enrich` (library-wide: source-enrich items — what `agents.auto-enrich` feeds), `seed:<goal>`
  * (propose-articles, then one article-compile item per article a person approved; a compiled article
  * is queued for the review manager) and `watch:<goal>` (phase 3: an acquisition-sweep and a
  * currency-check item, re-opened by its cron; its sweep ingests legal open-access picks when it opted
  * in, within its daily cap).
  *
  * A dispatch is ONE transaction — the run row, the item's compare-and-set to active, the hints it
  * reached, the event — and the run is submitted only after the commit (a crash in between leaves a
  * queued run, which the engine's restart re-enqueues). */
final class ManagerSupervisor(ds: DataSource, cfg: => AgentsConfig, agents: AgentsHandlers, h: com.histmersion.server.Handlers):

  private def mcfg = cfg.managers

  /** The ticking thread; a `var` because it starts after construction and stops in tests. */
  @volatile private var thread: Option[Thread] = None
  @volatile private var stopping = false
  private val nudges = LinkedBlockingQueue[String]()

  /** A run of a manager settled: tick soon (coalesced). */
  def nudge(): Unit = { val _ = nudges.offer("settled") }

  /** The library-wide managers exist — `review` and `enrich` — created PAUSED, so deploying never starts
    * spending until a person resumes them. Idempotent. */
  def ensureDefaults(): Unit =
    val _ = ensure(Kind.Review, ReviewId, None)
    val _ = ensure(Kind.Enrich, EnrichId, None)

  /** Manager `id` of kind `k` exists (created PAUSED when it did not — active while the autopilot is on,
    * which runs every manager); true = it was created now. */
  def ensure(k: Kind, id: String, goal: Option[String]): Boolean =
    val auto = agents.autopilot.on
    val made = ManagerRepo.create(ds, id, k, goal, if auto then State.Active else State.Paused, ManagerConfig())
    if made then
      ManagerRepo.event(ds, id, None, None, if auto then Feed.Autopilot else Feed.Paused,
        if auto then s"created active (the autopilot is on): ${ManagerSupervisor.purpose(k)}"
        else s"created paused: resume it to start ${ManagerSupervisor.purpose(k)} (nothing is spent until then)")
    made

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
    try
      while !stopping do
        // one tick, one generation: its database and config throughout (a reload moves the next one)
        try { val _ = agents.pins.pinned(tick(Instant.now())) }
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent managers tick failed: ${e.getMessage}", e)
        val _ = nudges.poll(math.max(1, mcfg.tickSeconds).toLong, TimeUnit.SECONDS)
        nudges.clear()
    catch case _: InterruptedException => ()

  /** When the events were last pruned (epoch ms). */
  private val lastPrune = java.util.concurrent.atomic.AtomicLong(0L)

  /** When each review manager last looked for stale articles (epoch ms); in memory — a restart looks again. */
  private val lastStale = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()

  /** The kinds the supervisor runs (every kind since phase 3). */
  private val Running = Set(Kind.Review, Kind.Enrich, Kind.Seed, Kind.Watch)

  /** One pass at `now`. Returns what was done, one line each (for a test or a log). Never throws for
    * one item's or one manager's sake. */
  def tick(now: Instant): List[String] = synchronized {
    if !cfg.enabled || !mcfg.enabled then Nil
    else
      val out = List.newBuilder[String]
      out ++= migrateSchedules(now)
      // the autopilot first (its new managers are dispatched in this tick), then the approvals its checks
      // decide (a decided plan settles its held item below)
      out ++= guarded("autopilot", out)(agents.autopilot.tick(now))
      out ++= guarded("auto-approvals", out)(agents.autoApprovals.sweep(now))
      val managers = ManagerRepo.list(ds).filter(m => State.parse(m.state).exists(_ != State.Stopped))
      managers.foreach { m =>
        guarded(m.id, out)(evaluate(m, now, out))
        guarded(m.id, out)(releaseHolds(m, now, out))
      }
      // the watchers' ingest backlog, before any sweep is dispatched (a sweep gets what it leaves)
      out ++= guarded("ingest-backlog", out)(agents.oaBacklog.drain(now))
      // the ones that may start runs, refilled, then dispatched round-robin over the global room
      val live = ManagerRepo.list(ds).filter(m => State.parse(m.state).contains(State.Active) &&
        Kind.parse(m.kind).exists(Running))
      live.foreach { m =>
        val fired = guarded(m.id, out)(fireCron(m, now, out))
        guarded(m.id, out)(refill(m, now, out, force = fired))
      }
      // the autopilot's focus: which goals are active; the others' model-using items are held (after the
      // refill and the crons, so what they just queued waits too)
      out ++= guarded("focus", out)(agents.focus.tick(now))
      dispatchAll(live, now, out)
      if now.toEpochMilli - lastPrune.get() > 3600000L then
        lastPrune.set(now.toEpochMilli)
        val _ = ManagerRepo.pruneEvents(ds, mcfg.keepEventsDays)
      out.result()
  }

  private def guarded[A](id: String, out: collection.mutable.Builder[String, List[String]])(f: => A)(using z: Zero[A]): A =
    try f
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"manager $id: ${e.getClass.getSimpleName}: ${e.getMessage}", e)
        out += s"$id: error ${e.getMessage}"
        z.value

  private def kindOf(m: ManagerInfo): Kind = Kind.parse(m.kind).getOrElse(Kind.Review)

  def settings(m: ManagerInfo): ReviewSettings = ReviewSettings.forKind(kindOf(m), m.config, mcfg)

  // ── 1. evaluate ────────────────────────────────────────────────────────────────────────────────

  private def evaluate(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val s = settings(m)
    ManagerRepo.toEvaluate(ds, m.id).foreach {
      case (item, None) =>
        // its run row is gone (deleted by hand): the item goes back to the queue
        if ManagerRepo.requeue(ds, item.id, "its run disappeared; queued again") then
          ManagerRepo.event(ds, m.id, Some(item.id), item.runId, Feed.Evaluated, s"${item.target}: its run disappeared; pending again")
      case (item, Some((status, error))) =>
        val st = RunState.parse(status).getOrElse(RunState.Failed)
        if item.kind == ArticleReview then evaluateReview(m, s, item, st, error, now, out)
        else evaluateStep(m, s, item, st, error, now, out)
    }

  private def evaluateReview(m: ManagerInfo, s: ReviewSettings, item: ManagerItem, st: RunState, error: Option[String],
      now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val run    = item.runId.get
    val ledger = ReviewLedgerRepo.get(ds, item.target).toOption.flatten
    val facts = Option.when(st == RunState.Done) {
      val pairs = h.openCritiques(item.target)
      ledger.map(ReviewFacts.of(_, pairs)).getOrElse(ReviewFacts(0, 0, 0, onlyMissingCritiques = false, pairs))
    }
    val task  = ledger.flatMap(ReviewLedger.taskText(_, run))
    ManagerPolicy.evaluateReview(s, item.target, item.attempts, item.failures, item.state, run, st, error, facts, task, now) match
      case Next.Done(outcome, state) =>
        // what its filter returns now: more later makes it stale (phase 2)
        val withHits = state.copy(filterHits = h.articleFilterTotal(item.target))
        if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Done, None, None, withHits, Some(outcome)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $outcome")
          out += s"${item.target}: done"
      case Next.Again(notBefore, state, note) =>
        if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Pending, notBefore, None, state, Some(note)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note")
          out += s"${item.target}: again"
      case Next.Retry(notBefore, failures, note) => retry(m, item, run, notBefore, failures, note, out)
      case Next.Stop(outcome, task, state) =>
        val failed = st == RunState.Failed
        val status = if failed then ItemStatus.Failed else ItemStatus.Stopped
        val filed  = task.flatMap(t => fileTask(item.target, ledger, t))
        val text   = outcome + filed.fold("")(id => s" (task #$id)")
        if ManagerRepo.evaluated(ds, item.id, Some(run), status, None, Option.when(failed)(item.failures + 1), state, Some(text)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Stopped, s"${item.target}: $text")
          out += s"${item.target}: stopped"
      case Next.Wait(_, _) => () // a review leaves no approval to wait for

  private def retry(m: ManagerInfo, item: ManagerItem, run: String, notBefore: Instant, failures: Int, note: String,
      out: collection.mutable.Builder[String, List[String]]): Unit =
    if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Pending, Some(notBefore), Some(failures), item.state,
        Some(s"$note; retry after $notBefore")) then
      ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note; retry after $notBefore")
      out += s"${item.target}: retry"

  /** A one-run item (infer-filter, extend, enrich, propose, compile) after its run settled. */
  private def evaluateStep(m: ManagerInfo, s: ReviewSettings, item: ManagerItem, st: RunState, error: Option[String],
      now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val run = item.runId.get
    ManagerPolicy.evaluateStep(s, item.kind, item.target, item.failures, item.state, run, st, error, stepFacts(item, run), now) match
      case Next.Done(outcome, state) =>
        if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Done, None, None, state, Some(outcome)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target} (${item.kind}): $outcome")
          out += s"${item.target}: done"
          afterDone(m, item, Some(run), out)
      case Next.Wait(approval, note) =>
        val hold = Hold(reason = note, untilApproval = Some(approval))
        if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Held, None, None, item.state, Some(note), Some(hold)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Held, s"${item.target} (${item.kind}): $note")
          out += s"${item.target}: waits for approval $approval"
      case Next.Retry(notBefore, failures, note) => retry(m, item, run, notBefore, failures, note, out)
      case Next.Stop(outcome, task, state) =>
        val failed = st == RunState.Failed
        val filed  = task.flatMap(t => h.ops.queueAdd(Queue.Kind.Tasks.label, QueueAdd(t, topic = Some("agent manager"))).toOption.map(_.id))
        val text   = outcome + filed.fold("")(id => s" (task #$id)")
        if ManagerRepo.evaluated(ds, item.id, Some(run), if failed then ItemStatus.Failed else ItemStatus.Stopped, None,
            Option.when(failed)(item.failures + 1), state, Some(text)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Stopped, s"${item.target} (${item.kind}): $text")
          out += s"${item.target}: stopped"
      case Next.Again(notBefore, state, note) =>
        if ManagerRepo.evaluated(ds, item.id, Some(run), ItemStatus.Pending, notBefore, None, state, Some(note)) then
          ManagerRepo.event(ds, m.id, Some(item.id), Some(run), Feed.Evaluated, s"${item.target}: $note")

  /** What a DONE run of a one-run item left. */
  private def stepFacts(item: ManagerItem, run: String): StepFacts =
    lazy val progress = AgentRepo.get(ds, run).flatMap(_.progress).filter(_.nonEmpty).getOrElse("")
    item.kind match
      case InferFilter     => approvalFacts(run, ApprovalKind.ArticleFilter, s"no filter proposed — $progress")
      case ProposeArticles => approvalFacts(run, ApprovalKind.ArticlePlan, s"no article proposed — $progress")
      case ArticleExtend =>
        written(run) match
          case Some(w) if w.written => StepFacts.Finished(s"extended (${w.why})")
          case Some(w)              => StepFacts.Finished(s"nothing appended: ${w.why}")
          case None                 => StepFacts.Finished(s"nothing to extend — $progress")
      case ArticleCompile =>
        written(run) match
          case Some(w) if w.written => StepFacts.Finished(s"compiled (${w.why})")
          case Some(w) =>
            // a draft the autopilot's checks rejected is compiled again (a new draft may pass them)
            AgentRepo.approvalsOf(ds, run, Some(ApprovalKind.ArticlePublish)).lastOption
              .filter(a => a.state == ApprovalState.Rejected.wire && a.note.exists(_.startsWith(AutopilotPolicy.NotePrefix))) match
              case Some(a) => StepFacts.Redo(s"draft rejected by the autopilot's checks (approval #${a.id}: " +
                                a.note.getOrElse("").stripPrefix(AutopilotPolicy.NotePrefix).trim.take(300) + ")")
              case None    => StepFacts.NotDone(s"stopped: no article written — ${w.why}", None)
          case None                 => StepFacts.NotDone(s"stopped: nothing compiled — $progress", None)
      case AcquisitionSweep | CurrencyCheck =>
        StepFacts.Finished(closeLine(run).getOrElse(s"${item.kind} done — $progress").take(1000))
      case PlanGoalItem =>
        // a blocking approval: the run ends only once its plan was decided
        AgentRepo.approvalsOf(ds, run, Some(ApprovalKind.GoalPlan)).lastOption match
          case None    => StepFacts.Finished(s"nothing planned — $progress")
          case Some(a) => StepFacts.Finished(s"plan #${a.id} " + ManagerPolicy.decided(a.state, a.result.orElse(a.note)).getOrElse(a.state))
      case _ => StepFacts.Finished("enriched")

  /** The approval of `kind` a run opened: none (nothing passed its checks), open (the item waits for
    * it), or decided already (approved automatically, say). */
  private def approvalFacts(run: String, kind: ApprovalKind, none: String): StepFacts =
    AgentRepo.approvalsOf(ds, run, Some(kind)).lastOption match
      case None => StepFacts.Finished(none)
      case Some(a) if a.state == ApprovalState.Open.wire => StepFacts.Waiting(a.id, s"approval #${a.id} waits for a person")
      case Some(a) => StepFacts.Finished(s"approval #${a.id} " + ManagerPolicy.decided(a.state, a.result).getOrElse(a.state))

  /** The worklog line a sweep's `close` step wrote (what it found, queued, ingested). */
  private def closeLine(run: String): Option[String] =
    AgentRepo.steps(ds, run, 100000).filter(s => s.name == "close" && s.status == StepStatus.Done.wire).lastOption
      .flatMap(_.output).flatMap(o => io.circe.parser.decode[String](o).toOption)

  /** What an acquisition sweep picked (its `rank` step's checked answer). */
  private def picked(run: String): List[AcquisitionSweepPipeline.Picked] =
    AgentRepo.steps(ds, run, 100000).filter(s => s.name == "rank" && s.status == StepStatus.Done.wire).lastOption
      .flatMap(_.output).flatMap(o => io.circe.parser.decode[List[AcquisitionSweepPipeline.Picked]](o).toOption).getOrElse(Nil)

  /** What a compile-article run's write step did (None: it never got there). */
  private def written(run: String): Option[CompileArticlePipeline.Written] =
    AgentRepo.steps(ds, run, 100000).filter(s => s.name == "write" && s.status == StepStatus.Done.wire).lastOption
      .flatMap(_.output).flatMap(o => io.circe.parser.decode[CompileArticlePipeline.Written](o).toOption)

  /** What follows a finished item: an extended article is reviewed again; a compiled one is linked to its
    * research area and queued for the review manager; an approved plan becomes compile items. */
  private def afterDone(m: ManagerInfo, item: ManagerItem, run: Option[String],
      out: collection.mutable.Builder[String, List[String]]): Unit =
    item.kind match
      case ArticleExtend if run.flatMap(written).exists(_.written) =>
        chainReview(m.id, item.target, s"extended by run ${run.fold("")(_.take(8))}", out)
      case ArticleCompile if h.getArticle(item.target).isRight =>
        m.goal.foreach(g => GoalLinkRepo.link(ds, g, GoalPlans.LinkKind.Article, item.target, Some(s"compiled by ${m.id}")))
        val _ = ensure(Kind.Review, ReviewId, None)
        chainReview(ReviewId, item.target, s"compiled by ${m.id}", out)
        ManagerRepo.event(ds, m.id, Some(item.id), run, Feed.Evaluated, s"${item.target}: queued for the review manager")
      case ProposeArticles =>
        run.toList.flatMap(r => AgentRepo.approvalsOf(ds, r, Some(ApprovalKind.ArticlePlan)))
          .filter(_.state == ApprovalState.Approved.wire).foreach(a => applyPlan(a, out): Unit)
      case PlanGoalItem =>
        // an approved plan may have created sub-goals: the autopilot gives them their managers next tick
        if run.toList.flatMap(r => AgentRepo.approvalsOf(ds, r, Some(ApprovalKind.GoalPlan))).exists(_.state == ApprovalState.Approved.wire)
        then agents.autopilot.soon()
      case AcquisitionSweep | CurrencyCheck =>
        // what the sweep found, as the watcher's own event (the run's worklog line, and the works it picked)
        run.foreach { r =>
          val found = if item.kind == AcquisitionSweep then picked(r) else Nil
          val works = if found.isEmpty then "" else
            s"; picked: ${found.map(p => s"${p.candidate.title} (https://doi.org/${p.candidate.doi}${if p.candidate.isOa then ", open access" else ""})").mkString("; ")}"
          ManagerRepo.event(ds, m.id, Some(item.id), Some(r), Feed.Swept,
            (closeLine(r).getOrElse(s"${item.kind} of ${item.target} done") + works).take(2000))
        }
      case _ => ()

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one
    * re-opened. */
  private def chainReview(id: String, slug: String, why: String, out: collection.mutable.Builder[String, List[String]]): Unit =
    val (itemId, isNew) = ManagerRepo.upsert(ds, id, ArticleReview, slug, ItemOrigin.Chain, ManagerSupervisor.ChainPriority, reopen = true)
    ManagerRepo.event(ds, id, Some(itemId), None, Feed.Evaluated,
      s"$slug ${if isNew then "queued" else "queued again"} for review ($why, priority ${ManagerSupervisor.ChainPriority})")
    out += s"$slug: review chained"

  /** An approved `article-plan`: one compile item per article in its goal's seeding manager (created
    * paused if it does not exist), in the plan's order. Idempotent: an article planned before keeps its
    * item. Returns how many were added. */
  def applyPlan(a: ApprovalInfo, out: collection.mutable.Builder[String, List[String]] = List.newBuilder[String]): Int =
    (a.subject, a.articles) match
      case (Some(goal), Some(plan)) if a.state == ApprovalState.Approved.wire =>
        val id = seedId(goal)
        val _  = ensure(Kind.Seed, id, Some(goal))
        val added = plan.articles.count { p =>
          val spec = ArticleSpec(p.title, p.kind, p.filter, p.why)
          ManagerRepo.upsert(ds, id, ArticleCompile, p.slug, ItemOrigin.Chain, 0, reopen = false, ItemState(spec = Some(spec)))._2
        }
        if added > 0 then
          ManagerRepo.event(ds, id, None, None, Feed.Evaluated,
            s"plan #${a.id} approved: ${Plural.count(added, "article")} to compile (${plan.articles.map(_.slug).mkString(", ")})".take(2000))
          out += s"$id: $added to compile"
          nudge()
        added
      case _ => 0

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
      i.hold.flatMap(_.untilApproval) match
        case Some(aid) => settleOnApproval(m, i, aid, out)
        case None =>
          if i.hold.exists(ManagerPolicy.released(_, now, ManagerRepo.sourceExists(ds, _))) && ManagerRepo.release(ds, i.id) then
            ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Held, s"${i.target}: hold released (" +
              i.hold.flatMap(h => h.untilSource.map(s => s"$s exists").orElse(h.until.map(u => s"$u passed"))).getOrElse("") + ")")
            out += s"${i.target}: released"
    }

  /** An item held for approval `aid`: once a person decided it, the item is settled with the decision
    * (approved: done — an approved plan becomes compile items; rejected or failed: stopped). */
  private def settleOnApproval(m: ManagerInfo, i: ManagerItem, aid: Long, out: collection.mutable.Builder[String, List[String]]): Unit =
    AgentRepo.approval(ds, aid) match
      case None =>
        if ManagerRepo.settle(ds, i.id, ItemStatus.Stopped, s"approval #$aid is gone", List(ItemStatus.Held)) then
          ManagerRepo.event(ds, m.id, Some(i.id), None, Feed.Stopped, s"${i.target}: approval #$aid is gone")
      case Some(a) =>
        ManagerPolicy.decided(a.state, a.result).foreach { decided =>
          val ok = a.state == ApprovalState.Approved.wire
          val outcome = s"approval #$aid $decided"
          if ManagerRepo.settle(ds, i.id, if ok then ItemStatus.Done else ItemStatus.Stopped, outcome, List(ItemStatus.Held)) then
            ManagerRepo.event(ds, m.id, Some(i.id), i.runId, Feed.Evaluated, s"${i.target} (${i.kind}): $outcome".take(2000))
            out += s"${i.target}: approval decided"
            if ok then afterDone(m, i, i.runId, out)
        }

  // ── 0. schedules → watchers ────────────────────────────────────────────────────────────────────

  /** Every `goal_schedules` row left becomes (part of) its goal's watcher `watch:<goal>` — created
    * PAUSED, its cron, sweeps and per-run budget from the schedules ([[WatchSchedules.migrate]]; an
    * existing watcher keeps its own settings) — and the rows go: one transaction per goal (the rows are
    * taken out with DELETE … RETURNING, so two servers cannot both migrate them). Logged as the watcher's
    * `migrated` event and a worklog line. Idempotent: run at every start and every tick (a bundle import
    * still writes rows), it finds nothing once they are moved. Returns one line per goal migrated. */
  def migrateSchedules(now: Instant): List[String] =
    GoalLinkRepo.scheduleGoals(ds).flatMap { g =>
      val id = watchId(g)
      try
        val moved = transact(ds) {
          val rows = GoalLinkRepo.takeSchedulesIn(g)
          Option.when(rows.nonEmpty) {
            val existing = ManagerRepo.get(ds, id)
            val plan = WatchSchedules.migrate(existing.map(m => WatchSchedules.Existing(m.config, m.cron, m.nextTickAt)), rows, now)
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
          val _ = h.ops.queueAdd("log", QueueAdd(s"goal $g: $text (watcher $id)".take(1000)))
          scribe.info(s"goal $g: $text")
          s"$id: migrated"
        }
      catch
        case e: InterruptedException => throw e
        case e: Exception =>
          scribe.warn(s"goal $g: its schedules were not moved into $id: ${e.getMessage}", e)
          Nil
    }

  // ── 3. the cron, then the refill ───────────────────────────────────────────────────────────────

  /** An active manager's cron (only while `agents.scheduler.enabled`): due → fire it — a watcher's sweeps
    * (re-opened when settled; one still open is left to finish), a review manager's selection whatever
    * its low water, a seeding manager's next proposal — and move on to its next firing; never scheduled
    * yet → record its first firing. A firing missed while paused fires once. True = it fired. */
  private def fireCron(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Boolean =
    if !cfg.scheduler.enabled then false
    else
      val cron = m.cron.flatMap(c => Cron.parse(c).toOption)
      ManagerPolicy.cronStep(cron, m.nextTickAt.flatMap(ManagerPolicy.parseInstant), now) match
        case ManagerPolicy.CronStep.Idle => false
        case ManagerPolicy.CronStep.Schedule(next) =>
          val _ = ManagerRepo.setNextTick(ds, m.id, next)
          false
        case ManagerPolicy.CronStep.Fire(next) =>
          val _ = ManagerRepo.setNextTick(ds, m.id, next)
          val what = kindOf(m) match
            case Kind.Watch =>
              m.goal.toList.flatMap { g =>
                Sweep.of(m.config).map { sw =>
                  val open = ManagerRepo.itemFor(ds, m.id, sw.itemKind, g).exists(i => !ItemStatus.parse(i.status).exists(_.isFinal))
                  if open then s"${sw.itemKind} still open"
                  else
                    val _ = ManagerRepo.upsert(ds, m.id, sw.itemKind, g, ItemOrigin.Cron, 0, reopen = true)
                    s"${sw.itemKind} due"
                }
              }
            case Kind.Seed =>
              m.goal.toList.map { g =>
                val (_, isNew) = ManagerRepo.upsert(ds, m.id, ProposeArticles, g, ItemOrigin.Cron, 0, reopen = true)
                s"$ProposeArticles ${if isNew then "added" else "due"}"
              }
            case Kind.Review => List("selection refilled")
            case Kind.Enrich => Nil
          ManagerRepo.event(ds, m.id, None, None, Feed.Cron, s"cron `${m.cron.getOrElse("")}` fired" +
            (if what.isEmpty then "" else s": ${what.mkString(", ")}") + next.fold("; it fires no more")(t => s"; next $t"))
          out += s"${m.id}: cron fired"
          true

  // ── refill ─────────────────────────────────────────────────────────────────────────────────────

  /** Below the low water (the review manager only; the others are fed by people, ingests and plans):
    * the selection — articles with open MAJORs (most first), never-reviewed ones, then stale ones — with
    * an infer-filter item before the review of an article that records no filter, and the standing
    * prioritise hints applied. */
  private def refill(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]],
      force: Boolean): Unit =
    if kindOf(m) == Kind.Review then
      val s = settings(m)
      val pending = ManagerRepo.counts(ds, m.id).pending
      if (force || pending < s.lowWater) && s.refillBatch > 0 then
        val tags = m.config.includeTags.getOrElse(Nil)
        val goal = m.config.includeGoal.filter(_.trim.nonEmpty)
        val majors = ManagerRepo.openMajorArticles(ds, m.id, ArticleReview, tags, goal, s.refillBatch)
        val fresh = if majors.size >= s.refillBatch then Nil
          else ManagerRepo.unreviewedArticles(ds, m.id, ArticleReview, tags, goal, s.refillBatch - majors.size)
        val room = s.refillBatch - majors.size - fresh.size
        val stale = if room > 0 then staleArticles(m, s, tags, goal, room, now) else Nil
        val picks = ManagerPolicy.select(majors, fresh, stale.map((slug, why, _) => (slug, why)), s.refillBatch, s.inferFilter,
          slug => com.histmersion.db.ArticleRepo.load(ds, slug).flatMap(_.filter).isDefined,
          slug => ManagerRepo.hasItem(ds, m.id, InferFilter, slug))
        val hints = ManagerRepo.hints(ds, m.id, live = true).filter(_.effect.exists(_.action == HintEffect.Prioritise))
        val added = picks.flatMap { p =>
          val prio = boosted(p.target, p.priority, hints)
          val boost = if prio != p.priority then s", priority $prio by a hint" else ""
          stale.find((slug, _, item) => p.kind == ArticleReview && slug == p.target && item.isDefined).flatMap(_._3) match
            case Some(itemId) =>
              Option.when(ManagerRepo.restale(ds, itemId, prio, p.why))(s"${p.target} (${p.why}$boost)")
            case None =>
              val (_, isNew) = ManagerRepo.upsert(ds, m.id, p.kind, p.target, ItemOrigin.Selection, prio, reopen = false)
              val label = if p.kind == ArticleReview then p.target else s"${p.target} [${p.kind}]"
              Option.when(isNew)(s"$label (${p.why}$boost)")
        }
        if added.nonEmpty then
          ManagerRepo.event(ds, m.id, None, None, Feed.Evaluated, s"selected ${Plural.count(added.size, "item")}: ${added.mkString(", ")}".take(2000))
          out += s"${m.id}: selected ${added.size}"
      extensions(m, now, out)

  /** When each review manager last looked for articles to extend (epoch ms); in memory. */
  private val lastExtend = java.util.concurrent.ConcurrentHashMap[String, java.lang.Long]()

  /** New sources reaching the articles they belong to (the autopilot): at most every `stale-scan-minutes`,
    * an article whose filter returns at least `extendMinNew` sources it neither cites nor gives a reason
    * for — and that was not extended in `extend-cooldown-days` — gets an `article-extend` item; the
    * extension's review follows as a chained item. `extendMinNew` 0 = never. */
  private def extensions(m: ManagerInfo, now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val min = m.config.extendMinNew.getOrElse(mcfg.review.extendMinNew)
    val last = Option(lastExtend.get(m.id)).map(_.longValue)
    val every = math.max(1, mcfg.review.staleScanMinutes).toLong * 60000L
    if min > 0 && last.forall(l => now.toEpochMilli - l >= every) then
      lastExtend.put(m.id, now.toEpochMilli)
      val s = settings(m)
      val tags = m.config.includeTags.getOrElse(Nil)
      val goal = m.config.includeGoal.filter(_.trim.nonEmpty)
      val due = ManagerRepo.extendCandidates(ds, m.id, tags, goal, math.max(1, s.refillBatch) * ManagerSupervisor.StaleLookahead)
        .filter((slug, _) => AgentRepo.unsettledOn(ds, slug).isEmpty)
        .flatMap { (slug, at) =>
          h.newFilterSources(slug).flatMap(n => AutopilotPolicy.extendDue(n, min, at.flatMap(ManagerPolicy.parseInstant),
            mcfg.review.extendCooldownDays, now)).map(slug -> _)
        }.take(math.max(1, s.refillBatch))
      val added = due.flatMap { (slug, why) =>
        val (id, isNew) = ManagerRepo.upsert(ds, m.id, ArticleExtend, slug, ItemOrigin.Selection, 0, reopen = true)
        val item = ManagerRepo.item(ds, id)
        Option.when(isNew || item.exists(_.status == ItemStatus.Pending.wire))(s"$slug ($why)")
      }
      if added.nonEmpty then
        ManagerRepo.event(ds, m.id, None, None, Feed.Evaluated,
          s"to extend with new sources: ${added.mkString(", ")}".take(2000))
        out += s"${m.id}: ${added.size} to extend"

  /** Converged articles that are due again (at most every `stale-scan-minutes` per manager): (slug, why,
    * its done item). Their filter is counted only for the ones whose count was recorded. */
  private def staleArticles(m: ManagerInfo, s: ReviewSettings, tags: List[String], goal: Option[String], room: Int,
      now: Instant): List[(String, String, Option[Long])] =
    val last = Option(lastStale.get(m.id)).map(_.longValue).getOrElse(Long.MinValue)
    val every = math.max(1, mcfg.review.staleScanMinutes).toLong * 60000L
    if s.staleAfterDays <= 0 || (last != Long.MinValue && now.toEpochMilli - last < every) then Nil
    else
      lastStale.put(m.id, now.toEpochMilli)
      ManagerRepo.staleCandidates(ds, m.id, ArticleReview, tags, goal, room * ManagerSupervisor.StaleLookahead).flatMap {
        (slug, at, hits, item) =>
          ManagerPolicy.parseInstant(at).flatMap(t =>
            ManagerPolicy.staleWhy(t, now, s.staleAfterDays, hits, hits.flatMap(_ => h.articleFilterTotal(slug))))
            .map(why => (slug, why, item))
      }.take(room)

  /** A target's priority under the standing prioritise hints that match it. */
  private def boosted(slug: String, base: Int, hints: List[ManagerHint]): Int =
    if hints.isEmpty then base
    else
      val (tags, goals) = ManagerRepo.articleFacets(ds, slug)
      hints.flatMap(_.effect).filter(e => ManagerPolicy.matches(e, slug, tags, Nil, goals))
        .map(_.priority.getOrElse(ManagerSupervisor.HintPriority)).foldLeft(base)(math.max)

  // ── 4. dispatch ────────────────────────────────────────────────────────────────────────────────

  /** The central scheduler's pass ([[SchedulerService]]): every active manager's due items are candidates,
    * one decision admits them across every job type within the AI allowance (the runs at once, the plan
    * pools, the budgets and daily caps, a manager's own saved `maxRuns`), starting each through [[dispatch]]
    * — one that cannot start now (another run writes its target) gives way to the next. Then each manager's
    * status line, with the scheduler's reason when its items wait, and a `starved`/budget event once per
    * change. */
  private def dispatchAll(ms: List[ManagerInfo], now: Instant, out: collection.mutable.Builder[String, List[String]]): Unit =
    val byId = ms.map(m => m.id -> m).toMap
    val decision = agents.scheduler.pass(ms, now, None) { (m, item) =>
      if ManagerSupervisor.blocks(item.kind, AgentRepo.unsettledOn(ds, item.target)) then Left("another run is writing its target")
      else guardedOpt(m.id, out)(dispatch(m, settings(m), item, now, out)) match
        case Some(_) => Right(())
        case None =>
          if ManagerRepo.item(ds, item.id).exists(_.status == ItemStatus.Pending.wire) then
            Left("it could not start now (its manager's events say why)")
          else Left("settled without a run")
    }
    ms.foreach { m =>
      val waits   = decision.waiting.filter(_._1.c.managerId == m.id).map(_._2).filterNot(_.why == "settled without a run")
      val blocked = waits.find(w => w.code == SchedulerPolicy.Code.Blocked && w.why.startsWith("budget")).map(_.why)
        .orElse(waits.find(_.code == "budget").map(_.why))
      val starved = waits.find(w => w.code == SchedulerPolicy.Code.Blocked && w.why.startsWith("starved: ")).map(_.why.stripPrefix("starved: "))
      val full    = waits.exists(w => List(SchedulerPolicy.Code.Capacity, SchedulerPolicy.Code.Pool, SchedulerPolicy.Code.Manager).contains(w.code))
      val pending = math.max(0, ManagerRepo.counts(ds, m.id).pending)
      val base    = ManagerPolicy.statusNote(State.Active, starved, if full then 0 else 1, pending, waits.size,
        ManagerRepo.running(ds, m.id), blocked)
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

  /** Settle a pending item without a run (its target is gone, or its work is done already). */
  private def settleNow(m: ManagerInfo, item: ManagerItem, status: ItemStatus, why: String): Option[String] =
    if ManagerRepo.settle(ds, item.id, status, why, List(ItemStatus.Pending)) then
      ManagerRepo.event(ds, m.id, Some(item.id), None, if status == ItemStatus.Done then Feed.Evaluated else Feed.Stopped,
        s"${item.target} (${item.kind}): $why")
    None

  private def articleFilter(slug: String): Option[ArticleFilter] =
    com.histmersion.db.ArticleRepo.load(ds, slug).flatMap(_.filter)

  /** Start the next run of one item; Some(manager id) when it started. */
  private def dispatch(m: ManagerInfo, s: ReviewSettings, item: ManagerItem, now: Instant,
      out: collection.mutable.Builder[String, List[String]]): Option[String] =
    if ManagerSupervisor.blocks(item.kind, AgentRepo.unsettledOn(ds, item.target)) then None // another run writes it: wait
    else item.kind match
      case ArticleReview =>
        if ManagerPolicy.waitsFor(item.kind, item.target, ManagerRepo.openKeys(ds, m.id)) then None
        else h.getArticle(item.target) match
          case Left(_) => settleNow(m, item, ItemStatus.Cancelled, "the article no longer exists")
          case Right(_) =>
            val before = ReviewLedgerRepo.get(ds, item.target).toOption.flatten.map(_.openMajors.size).getOrElse(0)
            val pairs  = h.openCritiques(item.target)
            val rounds = math.min(s.rounds, cfg.review.roundsCap)
            start(m, item, RunRequest(Pipeline.ReviewArticle.wire, item.target, budgetUsd = m.config.budgetUsd, fix = Some(true),
                rounds = Some(rounds)), identity, item.state.copy(before = Some(before), pairsBefore = pairs),
              s"review-article fix, $rounds rounds; $before open MAJOR(s)${pairs.fold("")(n => s", $n open critique pair(s)")}", out)
      case InferFilter =>
        h.getArticle(item.target) match
          case Left(_) => settleNow(m, item, ItemStatus.Cancelled, "the article no longer exists")
          case Right(v) if articleFilter(v.article.slug).isDefined =>
            settleNow(m, item, ItemStatus.Done, "the article records a filter already")
          case Right(_) =>
            start(m, item, RunRequest(Pipeline.InferFilter.wire, item.target, budgetUsd = m.config.budgetUsd), identity,
              item.state, "infer-filter", out)
      case ArticleExtend =>
        h.getArticle(item.target) match
          case Left(_) => settleNow(m, item, ItemStatus.Cancelled, "the article no longer exists")
          case Right(_) =>
            start(m, item, RunRequest(Pipeline.CompileArticle.wire, item.target, budgetUsd = m.config.budgetUsd),
              _.copy(chainReview = Some(false)), item.state, "compile-article (extend; its review follows as an item)", out)
      case SourceEnrich =>
        h.getSource(item.target, body = false) match
          case Left(_) => settleNow(m, item, ItemStatus.Cancelled, "the source no longer exists")
          case Right(src) =>
            val runs = AgentRepo.forTarget(ds, Pipeline.EnrichSource, src.source.citekey).toList.filterNot(_.dryRun)
              .flatMap(r => RunState.parse(r.status).map(AutoEnrich.PriorRun(r.id, _)))
            // a person's Enrich enriches again; an ingest's or a selection's skips an enriched source
            val enriched = Option.when(item.origin != ItemOrigin.Person.wire)(
              AutoEnrich.enrichedBy(src.findings.size, src.source.notes, runs)).flatten
            enriched match
              case Some(why) => settleNow(m, item, ItemStatus.Done, s"already enriched: $why")
              case None =>
                start(m, item, RunRequest(Pipeline.EnrichSource.wire, src.source.citekey, budgetUsd = m.config.budgetUsd),
                  identity, item.state, "enrich-source", out)
      case ProposeArticles =>
        h.getGoal(item.target) match
          case Left(_) => settleNow(m, item, ItemStatus.Cancelled, "the goal no longer exists")
          case Right(g) =>
            start(m, item, RunRequest(Pipeline.ProposeArticles.wire, g.slug, budgetUsd = m.config.budgetUsd),
              _.copy(minSources = m.config.minSources, maxArticles = m.config.maxArticles), item.state, "propose-articles", out)
      case ArticleCompile =>
        item.state.spec match
          case None => settleNow(m, item, ItemStatus.Cancelled, "no approved article to compile")
          case Some(_) if AgentRepo.slugExists(ds, item.target) =>
            settleNow(m, item, ItemStatus.Cancelled, "an article with this slug was created meanwhile")
          case Some(sp) =>
            start(m, item, RunRequest(Pipeline.CompileArticle.wire, item.target, budgetUsd = m.config.budgetUsd,
                title = Some(sp.title), kind = Some(sp.kind), query = sp.filter.query, topics = sp.filter.topics,
                tags = sp.filter.tags, goal = m.goal),
              _.copy(chainReview = Some(false)), item.state, "compile-article (new; its review follows as an item)", out)
      case PlanGoalItem =>
        h.getGoal(item.target) match
          case Left(_) => settleNow(m, item, ItemStatus.Cancelled, "the goal no longer exists")
          case Right(g) =>
            // sub-goals only above the depth limit (a goal there gets queries and works, no children)
            val depth = AutopilotPolicy.depth(g.slug, s => com.histmersion.db.GoalRepo.get(ds, s).flatMap(_.parentSlug))
            val leaf  = depth >= cfg.approvals.auto.maxGoalDepth
            start(m, item, RunRequest(Pipeline.PlanGoal.wire, g.slug, budgetUsd = m.config.budgetUsd),
              p => if leaf then p.copy(subGoals = Some(0)) else p, item.state,
              s"plan-goal (depth $depth${if leaf then ": queries and works, no sub-goals" else ""})", out)
      case AcquisitionSweep | CurrencyCheck =>
        h.getGoal(item.target) match
          case Left(_) => settleNow(m, item, ItemStatus.Cancelled, "the goal no longer exists")
          case Right(g) =>
            // the deterministic search first (#3761, no model): no new candidate, no agent run — the schedule
            // stops spending model quota on sweeps that change nothing
            val swept = Option.when(item.kind == AcquisitionSweep)(agents.acquisition.sweepCandidates(g.slug, dryRun = false))
            swept.filter(ManagerSupervisor.nothingNew).flatMap(_.toOption) match
              case Some(s) =>
                settleNow(m, item, ItemStatus.Done, s"no new candidates from the goal's watches (${Plural.count(s.watches.size, "watch", "watches")} " +
                  s"read, ${s.found} found, all known) — no agent run")
              case None =>
                val sweep   = item.kind == AcquisitionSweep
                val autoOn  = sweep && m.config.autoIngest.contains(true)
                val own     = if sweep then ManagerPolicy.ingestAllowance(m.config, mcfg,
                                ManagerRepo.countEvents(ds, m.id, Feed.Ingested, ManagerPolicy.dayStart(now))) else 0
                // the autopilot's cap shared by every watcher (none while it is off)
                val shared  = if autoOn then agents.autopilot.sharedIngestLeft(now) else None
                // the ingest backlog drains first: a sweep's new finds get what it leaves of the allowance
                val (mine, all) = if autoOn then agents.oaBacklog.reserved(m.id) else (0, 0)
                val allowed = if autoOn then IngestBacklog.sweepAllowance(own, shared, mine, all) else 0
                val auto    = if !sweep then "" else if autoOn then
                                s"; auto-ingest of legal open access: ${Plural.count(allowed, "work")} left today" +
                                  shared.fold("")(n => s" (all watchers: $n left)") +
                                  (if all > 0 then s"; ${Plural.count(all, "work")} wait in the ingest backlog first" +
                                    (if mine > 0 then s" ($mine of them its own)" else "") else "") +
                                  "; the open-access picks past it wait in the backlog" else "; auto-ingest off"
                start(m, item, RunRequest(ManagerSupervisor.pipelineOf(item.kind).wire, g.slug, budgetUsd = m.config.budgetUsd),
                  p => if autoOn then p.copy(autoIngest = Option.when(allowed > 0)(allowed), oaBacklog = Some(true)) else p,
                  item.state, s"${item.kind}$auto", out)
      case other => settleNow(m, item, ItemStatus.Cancelled, s"'$other' items are not driven by a ${m.kind} manager")

  /** Create, mark and submit one run: ONE transaction (the run row, the item's compare-and-set, the hints
    * it reached, the event), the submit after the commit. The manager's model overrides go to the roles
    * the pipeline uses; `tweak` sets the options the request does not carry. */
  private def start(m: ManagerInfo, item: ManagerItem, req0: RunRequest, tweak: RunParams => RunParams, state: ItemState,
      what: String, out: collection.mutable.Builder[String, List[String]]): Option[String] =
    val roles = ManagerSupervisor.pipelineOf(item.kind).roles
    val req = req0.copy(modelFor = m.config.models.getOrElse(Nil).filter(o => Role.parse(o.role).exists(roles.contains)))
    agents.prepare(req, None) match
      case Left(e) =>
        val note = s"not started: ${e.message}".take(500)
        if !ManagerRepo.lastEventOf(ds, m.id, Feed.Starved).exists(_.text == note) then
          ManagerRepo.event(ds, m.id, Some(item.id), None, Feed.Starved, note)
        None
      case Right(pr) =>
        val hints  = ManagerPolicy.notesFor(ManagerRepo.hints(ds, m.id, live = true), item.id)
        val notes  = hints.map(_.text.trim)
        val params = tweak(pr.params).copy(notes = Option.when(notes.nonEmpty)(notes))
        val id =
          try
            Some(transact(ds) {
              val id = AgentRepo.createIn(pr.pipeline, pr.target, pr.model, BigDecimal(pr.usd), pr.tokens, params, None,
                pr.goal, None, Origin.Manager, pr.dryRun, Some(m.id), Some(item.id))
              if !ManagerRepo.dispatchIn(item.id, id, state) then throw Lost()
              ManagerRepo.usedIn(hints, id)
              ManagerRepo.eventIn(m.id, Some(item.id), Some(id), Feed.Started,
                s"${item.target}: run ${item.attempts + 1} ($what)" + (if notes.nonEmpty then s" with ${Plural.count(notes.size, "note")}" else ""))
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

/** What a guarded step answers when it failed. */
private[agents] final case class Zero[A](value: A)
private[agents] object Zero:
  given Zero[Unit]    = Zero(())
  given Zero[Boolean] = Zero(false)
  given Zero[List[String]] = Zero(Nil)

object ManagerSupervisor:

  /** Pure: does a candidate sweep say there is nothing for a ranking (#3761)? Only an answered sweep with
    * no unseen candidate does; a search that is not connected (or failed) lets the run start as before. */
  def nothingNew(sweep: Either[String, CandidateSweep]): Boolean = sweep.exists(_.unseen == 0)
  /** The priority a prioritise hint gives when it names none (a person's enqueue is 10). */
  val HintPriority = 5
  /** A person's enqueue (the Review button, `agent manager … enqueue`). */
  val PersonPriority = 10
  /** A review a compile or an extension chained (§4: origin chain, priority 2). */
  val ChainPriority = 2
  /** Stale candidates read per article the selection has room for (most are not due yet). */
  val StaleLookahead = 5

  /** [[ManagerPolicy.blocks]] over the unsettled runs on an item's target. */
  def blocks(kind: String, unsettled: Seq[Agents.RunInfo]): Boolean =
    ManagerPolicy.blocks(kind, pipelineOf(kind).wire, unsettled.map(_.pipeline))

  /** The pipeline an item kind runs. */
  def pipelineOf(itemKind: String): Pipeline = itemKind match
    case InferFilter                    => Pipeline.InferFilter
    case ArticleExtend | ArticleCompile => Pipeline.CompileArticle
    case SourceEnrich                   => Pipeline.EnrichSource
    case ProposeArticles                => Pipeline.ProposeArticles
    case AcquisitionSweep               => Pipeline.AcquisitionSweep
    case CurrencyCheck                  => Pipeline.CurrencyCheck
    case PlanGoalItem                   => Pipeline.PlanGoal
    case _                              => Pipeline.ReviewArticle

  /** The roles a manager of kind `k` runs (what its `models` setting may name). */
  def roles(k: Kind): List[Role] = itemKinds(k).filterNot(_ == OaIngest).map(pipelineOf).flatMap(_.roles).distinct

  /** What a manager of kind `k` does, for its created-paused event. */
  def purpose(k: Kind): String = k match
    case Kind.Review => "reviewing"
    case Kind.Enrich => "enriching"
    case Kind.Seed   => "planning and compiling articles"
    case Kind.Watch  => "watching"
