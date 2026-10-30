package com.histmersion.server.agents

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Autopilot.*
import com.histmersion.domain.AutopilotPolicy.{GoalFacts, GoalRow, ManagerRow, Step}
import com.histmersion.api.GoalPlans.LinkKind
import com.histmersion.api.Managers.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.{AgentRepo, AutopilotRepo, GoalLinkRepo, GoalRepo, ManagerRepo}

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/** The autopilot (docs/research-process-managers.md "As built: the autopilot"): ONE switch, kept in the database, that
  * lets the process managers carry every research goal from planned to reviewed with no person in the loop — within the
  * managers' own caps, `agents.max-concurrent-runs`, the budgets and the plan floors, which it never lifts.
  *
  * On, every [[tick]] of the managers (at most every `agents.autopilot.ensure-minutes`, and at once when it is turned
  * on or a plan created sub-goals):
  *
  *   1. ENSURE ([[AutopilotPolicy.ensure]]): `review` and `enrich` active; every unfinished goal a seeding manager (a
  *      `plan-goal` item first when it has no plan) and a watcher with auto-ingest of legal open access on; a reviewed
  *      goal a watcher on the slower cron. Crons are spread per goal. A manager a person paused stays paused; a setting
  *      a person chose is kept.
  *   2. ADVANCE ([[AutopilotPolicy.advance]]): each goal's status moves on from the facts, sub-goals first.
  *
  * and its housekeeping, when due: a backup (`backups.keep` prunes as usual) and the safe `lint --fix` repairs, each on
  * its own thread so the tick never waits for them.
  *
  * Off, the seeding managers and watchers it runs are paused (and remembered, so turning it on again resumes exactly
  * those). Every change is a manager event and a worklog line.
  */
final class AutopilotService(
    ds: DataSource,
    cfg: => AgentsConfig,
    h: com.histmersion.server.Handlers,
    agents: AgentsHandlers
):

  private def acfg = cfg.autopilot

  /** Whether it is on (one row read). */
  def on: Boolean = AutopilotRepo.get(ds).enabled

  /** Today's shared ingest cap: `ingest-per-day` (0 = none), scaled to the watchers' ingest backlog when
    * `ingest-backlog-share` is above 0 (at most `ingest-ceiling`).
    */
  def ingestCap: Int =
    if acfg.ingestPerDay <= 0 then 0
    else AutopilotPolicy.scaledCap(acfg.ingestPerDay, backlogPending, acfg.ingestBacklogShare, acfg.ingestCeiling)

  private def backlogPending: Int =
    if acfg.ingestBacklogShare <= 0 then 0 else ManagerRepo.backlogCounts(ds).values.map(_._1).sum

  /** What is left today of the legal open-access ingests all watchers share — the acquisition list's re-check's finds
    * counted too (None: off, or no shared cap).
    */
  def sharedIngestLeft(now: Instant): Option[Int] =
    if !on then None
    else AutopilotPolicy.sharedIngestLeft(ingestCap, ingestedToday(now))

  /** The legal open-access ingests today: every watcher's, and the acquisition list's re-check's. */
  def ingestedToday(now: Instant): Int =
    ManagerRepo.countEventsAll(ds, Feed.Ingested, ManagerPolicy.dayStart(now)) + agents.acquireRecheck.fetchedToday(now)

  /** Make sure of the goals' managers at the next tick (a plan created sub-goals). */
  def soon(): Unit = AutopilotRepo.ensureSoon(ds)

  // ── the switch ─────────────────────────────────────────────────────────────────────────────────

  /** Turn it on (ensure and advance at once) or off (pause the seeding managers and watchers it runs). Idempotent; the
    * answer is the status with what it did.
    */
  def set(in: AutopilotSet, now: Instant = Instant.now()): AutopilotStatus =
    val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
    val row = AutopilotRepo.get(ds)
    val changes =
      if in.on then
        val turned = AutopilotRepo.set(ds, enabled = true, by, row.paused)
        // turning it on lifts a drain: the person wants it running
        val undrained = Option.when(AutopilotRepo.setDrain(ds, on = false, by))(s"drain lifted by $by")
        (if turned then List(s"turned on by $by") else List("already on")) ++ undrained ++ ensureNow(now) ++ advance(
          now
        )
      else if !row.enabled then List("already off")
      else
        val ids = AutopilotPolicy.toPause(rows())
        ids.foreach { id =>
          val _ = ManagerRepo.setState(ds, id, State.Paused, pauseRuns = false)
          ManagerRepo.event(
            ds,
            id,
            None,
            None,
            Feed.Autopilot,
            s"paused: the autopilot was turned off by $by (its runs in flight go on)"
          )
        }
        val _ = AutopilotRepo.set(ds, enabled = false, by, ids)
        List(s"turned off by $by", s"paused ${Plural.count(ids.size, "manager")} it ran (turning it on resumes them)")
    log(s"autopilot ${if in.on then "on" else "off"} (by $by): ${changes.mkString("; ")}")
    agents.managers.nudge()
    status(now).copy(changes = changes)

  // ── the tick ───────────────────────────────────────────────────────────────────────────────────

  /** One pass, from the managers' tick: nothing while off; on, ensure and advance when due, and the housekeeping when
    * due.
    */
  def tick(now: Instant): List[String] =
    val row = AutopilotRepo.get(ds)
    if !row.enabled then Nil
    else
      val last = row.lastEnsureAt.flatMap(ManagerPolicy.parseInstant)
      val ensured =
        if last.forall(t => !t.plusSeconds(math.max(1, acfg.ensureMinutes).toLong * 60L).isAfter(now)) then
          val out = ensureNow(now) ++ advance(now) ++ (if agents.drain.draining then Nil else nextArticles(now))
          if out.nonEmpty then log(s"autopilot: ${out.mkString("; ")}".take(1000))
          out
        else Nil
      ensured ++ housekeeping(row, now)

  private def rows(): List[ManagerRow] =
    ManagerRepo
      .light(ds)
      .map(m =>
        ManagerRow(m.id, m.kind, m.goal, m.state, m.cron, m.config.autoIngest, m.planItem, m.config.extendMinNew)
      )

  private def settings = AutopilotPolicy.Settings(
    acfg.watchCron,
    acfg.seedCron,
    acfg.finishedWatchCron,
    math.max(0, acfg.watchIngestPerDay),
    math.max(0, acfg.extendMinNew)
  )

  /** Every goal's managers ([[AutopilotPolicy.ensure]]), applied; one line per change. */
  def ensureNow(now: Instant): List[String] =
    val goals = GoalRepo.all(ds)
    val planned = GoalLinkRepo.planned(ds)
    val links = GoalLinkRepo.linkCounts(ds)
    val gs = goals.map(g =>
      GoalRow(g.slug, g.status, needsPlan = !planned(g.slug) && links.getOrElse((g.slug, LinkKind.Query.wire), 0) == 0)
    )
    val steps = AutopilotPolicy.ensure(gs, rows(), AutopilotRepo.get(ds).paused.toSet, settings)
    val out = steps.flatMap(s => applyStep(s, now))
    AutopilotRepo.ensured(ds, now)
    if out.nonEmpty then agents.managers.nudge()
    out

  private def next(cron: String, now: Instant): Option[Instant] = Cron.parse(cron).toOption.flatMap(_.next(now))

  private def event(id: String, text: String): Unit = ManagerRepo.event(ds, id, None, None, Feed.Autopilot, text)

  private def applyStep(s: Step, now: Instant): Option[String] =
    s match
      case Step.CreateSeed(goal, cron, planFirst) =>
        val id = seedId(goal)
        Option.when(
          ManagerRepo.create(ds, id, Kind.Seed, Some(goal), State.Active, ManagerConfig(), Some(cron), next(cron, now))
        ) {
          if planFirst then
            val _ = ManagerRepo.upsert(ds, id, PlanGoalItem, goal, ItemOrigin.Selection, 1, reopen = false)
          val _ = ManagerRepo.upsert(ds, id, ProposeArticles, goal, ItemOrigin.Selection, 0, reopen = false)
          event(
            id,
            s"created active by the autopilot, cron `$cron` (UTC)" +
              (if planFirst then ": the goal has no plan — plan-goal first, then propose-articles"
               else ": propose-articles")
          )
          s"$id created"
        }
      case Step.CreateWatch(goal, cron, perDay) =>
        val id = watchId(goal)
        val c = ManagerConfig(autoIngest = Some(true), autoIngestPerDay = Some(perDay))
        Option.when(ManagerRepo.create(ds, id, Kind.Watch, Some(goal), State.Active, c, Some(cron), next(cron, now))) {
          Sweep
            .of(c)
            .foreach(sw => ManagerRepo.upsert(ds, id, sw.itemKind, goal, ItemOrigin.Selection, 0, reopen = false))
          event(
            id,
            s"created active by the autopilot, cron `$cron` (UTC), auto-ingest of legal open access on (at most $perDay a day)"
          )
          s"$id created"
        }
      case Step.Resume(id) =>
        Option.when(ManagerRepo.setState(ds, id, State.Active, pauseRuns = false)) {
          AutopilotRepo.unpaused(ds, id)
          event(id, "resumed by the autopilot")
          s"$id resumed"
        }
      case Step.SetCron(id, cron) =>
        ManagerRepo.get(ds, id).flatMap { m =>
          Option.when(ManagerRepo.setConfig(ds, id, m.config, Some(cron), next(cron, now))) {
            event(id, s"cron `$cron` (UTC) set by the autopilot")
            s"$id cron $cron"
          }
        }
      case Step.AutoIngestOn(id, perDay) =>
        ManagerRepo.get(ds, id).flatMap { m =>
          val c =
            m.config.copy(autoIngest = Some(true), autoIngestPerDay = m.config.autoIngestPerDay.orElse(Some(perDay)))
          Option.when(ManagerRepo.setConfig(ds, id, c, m.cron, m.nextTickAt.flatMap(ManagerPolicy.parseInstant))) {
            event(
              id,
              s"auto-ingest of legal open access turned on by the autopilot (at most ${c.autoIngestPerDay.getOrElse(perDay)} a day)"
            )
            s"$id auto-ingest on"
          }
        }
      case Step.AddPlanItem(id, goal) =>
        val (item, isNew) = ManagerRepo.upsert(ds, id, PlanGoalItem, goal, ItemOrigin.Selection, 1, reopen = false)
        Option.when(isNew) {
          ManagerRepo.event(
            ds,
            id,
            Some(item),
            None,
            Feed.Autopilot,
            s"$goal ($PlanGoalItem): the goal has no plan — planned first"
          )
          s"$id plan-goal queued"
        }
      case Step.ExtendMinNew(id, n) =>
        ManagerRepo.get(ds, id).flatMap { m =>
          Option.when(
            ManagerRepo.setConfig(
              ds,
              id,
              m.config.copy(extendMinNew = Some(n)),
              m.cron,
              m.nextTickAt.flatMap(ManagerPolicy.parseInstant)
            )
          ) {
            event(
              id,
              s"extend-min-new $n set by the autopilot: an article whose filter returns $n sources it neither cites " +
                "nor gives a reason for is extended"
            )
            s"$id extend-min-new $n"
          }
        }

  // ── goal statuses ──────────────────────────────────────────────────────────────────────────────

  /** Every goal's status moved on from the facts ([[AutopilotPolicy.advance]]), the deepest goals first so a parent
    * sees its sub-goals' new status in the same pass; one line per goal moved.
    */
  def advance(now: Instant): List[String] =
    val goals = GoalRepo.all(ds)
    val parentOf = goals.map(g => g.slug -> g.parentSlug).toMap
    val managers = ManagerRepo.light(ds)
    val articles = GoalLinkRepo.articlesByGoal(ds)
    val passed = ManagerRepo.passedReview(ds, ReviewId)
    val status = collection.mutable.Map.from(goals.map(g => g.slug -> g.status))
    val order = goals.sortBy(g => -AutopilotPolicy.depth(g.slug, s => parentOf.get(s).flatten))
    order.flatMap { g =>
      val mine = managers.filter(_.goal.contains(g.slug))
      val managed =
        mine.exists(m => m.state == State.Active.wire && (m.kind == Kind.Seed.wire || m.kind == Kind.Watch.wire))
      val arts = articles.getOrElse(g.slug, Nil)
      val seed = seedId(g.slug)
      val compileOpen = ManagerRepo.openOf(ds, seed, List(ArticleCompile), List("pending", "held", "active")) > 0 ||
        ManagerRepo.openOf(ds, seed, List(ProposeArticles, PlanGoalItem), List("held", "active"), focusHeld = false) > 0
      val kids = goals.filter(_.parentSlug.contains(g.slug)).map(c => status.getOrElse(c.slug, c.status))
      val facts = GoalFacts(g.slug, status(g.slug), managed, arts.size, compileOpen, arts.forall(passed), kids)
      AutopilotPolicy.advance(facts).flatMap { (to, why) =>
        h.patchGoal(g.slug, GoalPatch(status = Some(to))) match
          case Left(e) =>
            scribe.warn(s"autopilot: goal ${g.slug} not moved to $to: ${e.message}")
            None
          case Right(_) =>
            val from = status(g.slug)
            status(g.slug) = to
            val line = s"goal ${g.slug}: $from → $to ($why)"
            mine.find(_.kind == Kind.Seed.wire).orElse(mine.headOption).foreach(m => event(m.id, line))
            Some(line)
      }
    }

  // ── the next article (#3763) ───────────────────────────────────────────────────────────────────

  /** Every goal whose articles are all compiled and passed review gets its next proposal
    * ([[AutopilotPolicy.nextArticle]]): its seeding manager's propose-articles item re-opened (the scheduler starts it;
    * the plan goes through the article-plan approvals). One line per goal.
    */
  def nextArticles(now: Instant): List[String] =
    if acfg.nextArticleHours <= 0 then Nil
    else
      val seeds = ManagerRepo.light(ds).filter(_.kind == Kind.Seed.wire).flatMap(m => m.goal.map(_ -> m)).toMap
      agents.focus.facts().filter(f => f.articles > 0 && seeds.contains(f.slug)).flatMap { f =>
        val sid = seedId(f.slug)
        val item = ManagerRepo.itemFor(ds, sid, ProposeArticles, f.slug)
        val facts = AutopilotPolicy.NextFacts(
          f.slug,
          f.status,
          f.articles,
          f.unreviewed,
          f.compileOpen,
          seeds.get(f.slug).map(_.state),
          item.map(_.status),
          item.flatMap(i => ManagerPolicy.parseInstant(i.updatedAt))
        )
        AutopilotPolicy.nextArticle(facts, now, acfg.nextArticleHours).map { why =>
          val (id, _) = ManagerRepo.upsert(ds, sid, ProposeArticles, f.slug, ItemOrigin.Selection, 0, reopen = true)
          ManagerRepo.event(ds, sid, Some(id), None, Feed.Autopilot, s"${f.slug}: $why".take(2000))
          s"goal ${f.slug}: next article proposed"
        }
      }

  // ── housekeeping ───────────────────────────────────────────────────────────────────────────────

  private val housekeepingBusy = AtomicBoolean(false)

  /** The backup and the lint repairs, when due and nothing stands in the way — each on its own thread (a dump takes
    * minutes), one at a time. Recorded as started, so a failure is not retried every tick (the next try is a period
    * later); the outcome is a worklog line.
    */
  private def housekeeping(row: AutopilotRepo.Row, now: Instant): List[String] =
    val backupDue = AutopilotPolicy.due(row.lastBackupAt.flatMap(ManagerPolicy.parseInstant), acfg.backupHours, now)
    val lintDue = AutopilotPolicy.due(row.lastLintAt.flatMap(ManagerPolicy.parseInstant), acfg.lintHours, now)
    if !(backupDue || lintDue) then Nil
    else
      h.housekeepingBlock match
        case Some(why)                                            => List(s"housekeeping waits: $why")
        case None if !housekeepingBusy.compareAndSet(false, true) => Nil
        case None                                                 =>
          if backupDue then AutopilotRepo.backedUp(ds, now)
          if lintDue then AutopilotRepo.linted(ds, now)
          val work = agents.pins.carry {
            try
              if backupDue then backup()
              if lintDue then lint()
            finally housekeepingBusy.set(false)
          }
          val _ = Thread.ofPlatform().daemon(true).name("histmersion-autopilot-housekeeping").start(() => work.run())
          List(
            List(Option.when(backupDue)("backup"), Option.when(lintDue)("lint --fix")).flatten
              .mkString(" and ") + " started"
          )

  private def backup(): Unit =
    val line = h.backup() match
      case Left(e)    => s"autopilot: the scheduled backup failed: ${e.message}"
      case Right(st0) =>
        // the request answers `running` after a while: follow the job to its end
        var st = st0 // the job's last status, polled until it settles
        val deadline = System.currentTimeMillis() + 3L * 3600L * 1000L
        while st.stateOf.contains(BackupState.Running) && System.currentTimeMillis() < deadline do
          Thread.sleep(10000)
          st = h.backupStatus.getOrElse(st)
        st.result match
          case Some(r) =>
            s"autopilot: backup ${r.file} (${r.bytes} bytes)" +
              (if r.pruned.isEmpty then "" else s"; pruned ${r.pruned.mkString(", ")}")
          case None => s"autopilot: the scheduled backup ${st.file} ended ${st.state}${st.error.fold("")(e => s": $e")}"
    log(line)

  private def lint(): Unit =
    val line = h.ops.guardedLintFix(Nil) match
      case Left(e)  => s"autopilot: the scheduled lint --fix failed: ${e.message}"
      case Right(r) =>
        s"autopilot: lint --fix — ${r.edgesResolved} edge(s) resolved, ${r.sourcesReindexed} source(s) re-indexed" +
          (if r.remaining > 0 then s", ${r.remaining} left for the next run" else "")
    log(line)

  private def log(line: String): Unit =
    val _ = h.ops.queueAdd("log", QueueAdd(line.take(1000)))
    scribe.info(line)

  // ── status ─────────────────────────────────────────────────────────────────────────────────────

  /** Per goal: its stage, managers, counts, open approvals, last activity and pace — a few queries for every goal at
    * once (readable with agents off).
    */
  def status(now: Instant = Instant.now()): AutopilotStatus =
    val row = AutopilotRepo.get(ds)
    val goals = GoalRepo.all(ds)
    val managers = ManagerRepo.light(ds)
    val pace = ManagerRepo.pace(ds, now.minusSeconds(7L * 86400L))
    val links = GoalLinkRepo.linkCounts(ds)
    val arts = GoalLinkRepo.articlesByGoal(ds)
    val open = AgentRepo.openBySubject(ds)
    val backlog = agents.oaBacklog.snapshot(now)
    def brief(m: ManagerRepo.Light) = ManagerBrief(m.id, m.kind, m.state, m.statusNote)
    val progress = goals.sortBy(_.slug).map { g =>
      val mine = managers.filter(_.goal.contains(g.slug))
      val p = mine.flatMap(m => pace.get(m.id))
      val (o, d) = (p.map(_._1).sum, p.map(_._2).sum)
      GoalProgress(
        g.slug,
        g.title,
        g.status,
        g.parentSlug,
        mine.map(brief),
        links.getOrElse((g.slug, LinkKind.Source.wire), 0),
        arts.getOrElse(g.slug, Nil).size,
        open.getOrElse(g.slug, 0) + arts.getOrElse(g.slug, Nil).map(open.getOrElse(_, 0)).sum,
        p.flatMap(_._3).maxOption,
        d,
        o,
        AutopilotPolicy.etaDays(o, d)
      )
    }
    val caps = cfg.approvals.auto
    def rule(k: Agents.ApprovalKind, cap: String) =
      ApprovalRule(k.wire, if cfg.approvals.checked(k) then "checked" else "person", cap) match
        case r if k == Agents.ApprovalKind.ArticlePublish && cfg.approvals.publishMode == Agents.ApprovalMode.Auto =>
          r.copy(mode = "auto (in the run, unchecked)")
        case r => r
    AutopilotStatus(
      row.enabled,
      Some(row.changedAt),
      Option(row.changedBy).filter(_.nonEmpty),
      cfg.enabled,
      cfg.managers.enabled,
      cfg.scheduler.enabled,
      List(
        rule(
          Agents.ApprovalKind.GoalPlan,
          AutopilotPolicy.capText(
            caps.goalPlansPerDay,
            AgentRepo.openCount(ds, Agents.ApprovalKind.GoalPlan),
            caps.goalPlansBacklogShare,
            caps.goalPlansCeiling
          ) +
            s", sub-goals above depth ${caps.maxGoalDepth}"
        ),
        rule(Agents.ApprovalKind.ArticlePlan, s"${caps.articlesPerGoalPerDay} articles per goal a day"),
        rule(
          Agents.ApprovalKind.ArticlePublish,
          AutopilotPolicy.capText(
            caps.publishPerDay,
            AgentRepo.openCount(ds, Agents.ApprovalKind.ArticlePublish),
            caps.publishBacklogShare,
            caps.publishCeiling
          ) + s", filter at least ${caps.publishMinSources} sources"
        )
      ),
      ingestedToday(now),
      if row.enabled then ingestCap else 0,
      cfg.maxConcurrentRuns,
      row.lastBackupAt,
      row.lastLintAt,
      managers.filter(m => m.id == ReviewId || m.id == EnrichId).map(brief),
      progress,
      backlog = backlog.total,
      backlogDays = backlog.totalDays,
      focus = Some(agents.focus.info(now)),
      drain = agents.drain.info(),
      goalPlansWait = agents.focus.goalPlansWait(),
      upstreams = h.upstreamQuotas(now),
      acquireRecheck = Some(agents.acquireRecheck.status(now))
    )
