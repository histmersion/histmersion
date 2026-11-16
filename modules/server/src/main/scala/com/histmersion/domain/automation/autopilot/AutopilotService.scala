package com.histmersion
package domain.automation.autopilot

import com.histmersion.util.problem.Text.{sentence, sentences}
import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.managers.ManagerLight

import com.histmersion.domain.automation.agents.*

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Autopilot.*
import com.histmersion.domain.AutopilotPolicy.{GoalFacts, GoalRow, ManagerRow, Step}
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueueItem}
import com.histmersion.domain.automation.{
  Approval as DomainApproval,
  Goal as DomainGoal,
  ItemStatus as DomainItemStatus,
  Manager as DomainManager
}
import com.histmersion.domain.research.Slug as ResearchSlug
import com.histmersion.RowCount
import com.histmersion.domain.automation.autopilot.AutopilotRepo

import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.Goal
import com.histmersion.api.backup.Backup
import com.histmersion.api.automation.Managers
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.automation.Approval
import com.histmersion.api.automation.Autopilot

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
    agents: AgentsControllers
):

  private def acfg = cfg.autopilot

  /** Whether it is on (one row read). */
  def on: Boolean = agents.autopilotRow.row().enabled.value

  /** Today's shared ingest cap: `ingest-per-day` (0 = none), scaled to the watchers' ingest backlog when
    * `ingest-backlog-share` is above 0 (at most `ingest-ceiling`).
    */
  def ingestCap: AutopilotPolicy.Cap =
    if acfg.ingestPerDay.value <= 0 then AutopilotPolicy.Cap(0)
    else
      AutopilotPolicy.scaledCap(
        AutopilotPolicy.Base(acfg.ingestPerDay.value),
        backlogPending,
        AutopilotPolicy.Share(acfg.ingestBacklogShare.value),
        AutopilotPolicy.Cap(acfg.ingestCeiling.value)
      )

  private def backlogPending: AutopilotPolicy.Backlog =
    if acfg.ingestBacklogShare.value <= 0 then AutopilotPolicy.Backlog(0)
    else AutopilotPolicy.Backlog(agents.managerStore.backlogCounts().values.map(_._1.value).sum)

  /** What is left today of the legal open-access ingests all watchers share — the acquisition list's re-check's finds
    * counted too (None: off, or no shared cap).
    */
  def sharedIngestLeft(now: Instant): Option[AutopilotPolicy.Left] =
    if !on then None
    else AutopilotPolicy.sharedIngestLeft(ingestCap, ingestedToday(now))

  /** The legal open-access ingests today: every watcher's, and the acquisition list's re-check's. */
  def ingestedToday(now: Instant): AutopilotPolicy.IngestedToday =
    AutopilotPolicy.IngestedToday(
      agents.managerStore.countEventsAll(ManagerEvent.Kind(Feed.Ingested), ManagerPolicy.dayStart(now)).value +
        agents.workflows.managerKinds.map(_.ingestedToday(now).value).sum
    )

  /** Make sure of the goals' managers at the next tick (a plan created sub-goals). */
  def soon(): Unit = agents.autopilotRow.ensureSoon()

  // ── the switch ─────────────────────────────────────────────────────────────────────────────────

  /** Turn it on (ensure and advance at once) or off (pause the seeding managers and watchers it runs). Idempotent; the
    * answer is the status with what it did.
    */
  def set(in: AutopilotSet, now: Instant = Instant.now(agents.clock)): EngineStatus =
    val by = in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
    val changedBy = AutopilotRepo.Row.ChangedBy(by)
    val row = agents.autopilotRow.row()
    val changes =
      if in.on.value then
        val turned = agents.autopilotRow.set(enabled = AutopilotRepo.Row.Enabled(true), changedBy, row.paused)
        // turning it on lifts a drain: the person wants it running
        val undrained = Option.when(agents.autopilotRow.setDrain(on = false, changedBy))(s"drain lifted by $by")
        (if turned then List(s"turned on by $by") else List("already on")) ++ undrained ++ ensureNow(now) ++ advance(
          now
        )
      else if !row.enabled.value then List("already off")
      else
        val ids = AutopilotPolicy.toPause(rows())
        ids.foreach { id =>
          val managerId = ManagerInfo.Id(id.value)
          val _ = agents.managerStore.setState(managerId, State.Paused, pauseRuns = ManagerInfo.PauseRuns(false))
          event(managerId, s"paused: the autopilot was turned off by $by (its runs in flight go on)")
        }
        val _ = agents.autopilotRow.set(
          enabled = AutopilotRepo.Row.Enabled(false),
          changedBy,
          ids.map(id => AutopilotRepo.Row.Paused(id.value))
        )
        List(
          s"turned off by $by",
          s"paused ${Plural.count(Plural.CountN(ids.size.toLong), Plural.CountSingular("manager"))} it ran (turning it on resumes them)"
        )
    log(s"autopilot ${if in.on.value then "on" else "off"} (by $by): ${changes.mkString("; ")}")
    agents.managers.nudge()
    status(now).modify(_.changes).setTo(changes.map(c => EngineStatus.Changes(c)))

  // ── the tick ───────────────────────────────────────────────────────────────────────────────────

  /** One pass, from the managers' tick: nothing while off; on, ensure and advance when due, and the housekeeping when
    * due.
    */
  def tick(now: Instant): List[String] =
    val row = agents.autopilotRow.row()
    if !row.enabled.value then Nil
    else
      val last = row.lastEnsureAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
      val ensured =
        if last.forall(t => !t.plusSeconds(math.max(1, acfg.ensureMinutes.value).toLong * 60L).isAfter(now)) then
          val out = ensureNow(now) ++ advance(now) ++ (if agents.drain.draining then Nil else nextArticles(now))
          if out.nonEmpty then log(s"autopilot: ${out.mkString("; ")}".take(1000))
          out
        else Nil
      ensured ++ housekeeping(row, now)

  private def rows(): List[ManagerRow] =
    agents.managerStore
      .light()
      .map(m =>
        ManagerRow(
          ManagerRow.Id(m.id.value),
          DomainManager.Kind.Wire(m.kind.value),
          m.goal.flatMap(g => DomainGoal.Slug.option(g.value)),
          DomainManager.State.Wire(m.state.value),
          m.cron.map(c => AutopilotPolicy.Cron(c.value)),
          m.config.autoIngest.map(a => ManagerRow.AutoIngest(a.value)),
          ManagerRow.PlanItem(m.planItem.value),
          m.config.extendMinNew.map(n => AutopilotPolicy.Settings.ExtendMinNew(n.value))
        )
      )

  private def settings = AutopilotPolicy.Settings(
    AutopilotPolicy.Cron(acfg.watchCron.value),
    AutopilotPolicy.Cron(acfg.seedCron.value),
    AutopilotPolicy.Cron(acfg.finishedWatchCron.value),
    AutopilotPolicy.Settings.WatchIngestPerDay(math.max(0, acfg.watchIngestPerDay.value)),
    AutopilotPolicy.Settings.ExtendMinNew(math.max(0, acfg.extendMinNew.value))
  )

  /** Every goal's managers ([[AutopilotPolicy.ensure]]), applied; one line per change. */
  def ensureNow(now: Instant): List[String] =
    val goals = agents.goals.all()
    val planned = agents.goals.planned()
    val links = agents.goals.linkCounts()
    val gs = goals.map(g =>
      val slug = slugOf(g)
      GoalRow(
        slug,
        DomainGoal.Status.Wire(g.status.value),
        needsPlan = GoalRow.NeedsPlan(
          !planned(slug) && links
            .getOrElse((slug, DomainGoal.LinkKind.Wire(LinkKind.SearchQuery.wire.value)), RowCount(0))
            .value == 0
        )
      )
    )
    val steps = AutopilotPolicy.ensure(
      gs,
      rows(),
      agents.autopilotRow.row().paused.map(p => ManagerRow.Id(p.value)).toSet,
      settings
    )
    val out = steps.flatMap(s => applyStep(s, now))
    agents.autopilotRow.ensured(now)
    if out.nonEmpty then agents.managers.nudge()
    out

  private def next(cron: AutopilotPolicy.Cron, now: Instant): Option[Instant] =
    Cron.parse(Cron.Text(cron.value)).toOption.flatMap(_.next(now))

  /** The slug of a goal read from the database, as the domain types it. */
  private def slugOf(g: Goal): DomainGoal.Slug = DomainGoal.Slug.assume(g.slug.value)

  private def event(id: ManagerInfo.Id, text: String): Unit =
    agents.managerStore.event(id, None, None, ManagerEvent.Kind(Feed.Autopilot), ManagerEvent.Text(text))

  private def itemOf(goal: DomainGoal.Slug): ManagerItem.Target = ManagerItem.Target(goal.value)

  private def applyStep(s: Step, now: Instant): Option[String] =
    s match
      case Step.CreateSeed(goal, cron, planFirst) =>
        val id = seedId(Goal.Ref(goal.value))
        Option.when(
          agents.managerStore
            .create(
              id,
              Kind.Seed,
              Some(goal),
              State.Active,
              ManagerConfig(),
              Some(ManagerInfo.Cron(cron.value)),
              next(cron, now)
            )
        ) {
          if planFirst.value then
            val _ = agents.managerStore.upsert(
              id,
              ManagerItem.Kind(PlanGoalItem),
              itemOf(goal),
              ItemOrigin.Selection,
              ManagerItem.Priority(1),
              reopen = false
            )
          val _ = agents.managerStore.upsert(
            id,
            ManagerItem.Kind(ProposeArticles),
            itemOf(goal),
            ItemOrigin.Selection,
            ManagerItem.Priority(0),
            reopen = false
          )
          event(
            id,
            s"created active by the autopilot, cron `$cron` (UTC)" +
              (if planFirst.value then ": the goal has no plan — plan-goal first, then propose-articles"
               else ": propose-articles")
          )
          s"$id created"
        }
      case Step.CreateWatch(goal, cron, perDay) =>
        val id = watchId(Goal.Ref(goal.value))
        val c = ManagerConfig(
          autoIngest = Some(Managers.ManagerConfig.AutoIngest(true)),
          autoIngestPerDay = Some(ManagerConfig.AutoIngestPerDay(perDay.value))
        )
        Option.when(
          agents.managerStore
            .create(id, Kind.Watch, Some(goal), State.Active, c, Some(ManagerInfo.Cron(cron.value)), next(cron, now))
        ) {
          Sweep
            .of(c)
            .foreach(sw =>
              agents.managerStore.upsert(
                id,
                ManagerItem.Kind(sw.itemKind.value),
                itemOf(goal),
                ItemOrigin.Selection,
                ManagerItem.Priority(0),
                reopen = false
              )
            )
          event(
            id,
            s"created active by the autopilot, cron `$cron` (UTC), auto-ingest of legal open access on (at most $perDay a day)"
          )
          s"$id created"
        }
      case Step.Resume(rowId) =>
        val id = ManagerInfo.Id(rowId.value)
        Option.when(agents.managerStore.setState(id, State.Active, pauseRuns = ManagerInfo.PauseRuns(false))) {
          agents.autopilotRow.unpaused(id)
          event(id, "resumed by the autopilot")
          s"$id resumed"
        }
      case Step.SetCron(rowId, cron) =>
        val id = ManagerInfo.Id(rowId.value)
        agents.managerStore.get(id).flatMap { m =>
          Option.when(
            agents.managerStore.setConfig(id, m.config, Some(ManagerInfo.Cron(cron.value)), next(cron, now))
          ) {
            event(id, s"cron `$cron` (UTC) set by the autopilot")
            s"$id cron $cron"
          }
        }
      case Step.AutoIngestOn(rowId, perDay) =>
        val id = ManagerInfo.Id(rowId.value)
        agents.managerStore.get(id).flatMap { m =>
          val c =
            m.config
              .modify(_.autoIngest)
              .setTo(Some(ManagerConfig.AutoIngest(true)))
              .modify(_.autoIngestPerDay)
              .setTo(m.config.autoIngestPerDay.orElse(Some(ManagerConfig.AutoIngestPerDay(perDay.value))))
          Option.when(
            agents.managerStore.setConfig(
              id,
              c,
              m.cron,
              m.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
            )
          ) {
            event(
              id,
              s"auto-ingest of legal open access turned on by the autopilot (at most ${c.autoIngestPerDay.fold(perDay.value)(_.value)} a day)"
            )
            s"$id auto-ingest on"
          }
        }
      case Step.AddPlanItem(rowId, goal) =>
        val id = ManagerInfo.Id(rowId.value)
        val (item, isNew) = agents.managerStore.upsert(
          id,
          ManagerItem.Kind(PlanGoalItem),
          itemOf(goal),
          ItemOrigin.Selection,
          ManagerItem.Priority(1),
          reopen = false
        )
        Option.when(isNew) {
          agents.managerStore.event(
            id,
            Some(item),
            None,
            ManagerEvent.Kind(Feed.Autopilot),
            ManagerEvent.Text(s"$goal ($PlanGoalItem): the goal has no plan — planned first")
          )
          s"$id plan-goal queued"
        }
      case Step.ExtendMinNew(rowId, n) =>
        val id = ManagerInfo.Id(rowId.value)
        agents.managerStore.get(id).flatMap { m =>
          Option.when(
            agents.managerStore.setConfig(
              id,
              m.config.modify(_.extendMinNew).setTo(Some(ManagerConfig.ExtendMinNew(n.value))),
              m.cron,
              m.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
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
    val goals = agents.goals.all()
    val parentOf: Map[String, Option[String]] =
      goals.map(g => (g.slug.value: String) -> g.parentSlug.map(p => p.value: String)).toMap
    val managers = agents.managerStore.light()
    val articles = agents.workflows.linked().map((k, v) => (k.value: String) -> v.map(a => a.value: String))
    val passed = agents.workflows.finished().map(a => a.value: String)
    val status = collection.mutable.Map.from(goals.map(g => g.slug.value -> g.status))
    val order = goals.sortBy(g =>
      -AutopilotPolicy
        .depth(ResearchSlug.assume(g.slug.value), s => parentOf.get(s.value).flatten.map(p => ResearchSlug.assume(p)))
        .value
    )
    order.flatMap { g =>
      val slug = g.slug.value
      val mine = managers.filter(_.goal.exists(_.value == slug))
      val managed =
        mine.exists(m =>
          m.state.value == State.Active.wire.value &&
            (m.kind.value == Kind.Seed.wire.value || m.kind.value == Kind.Watch.wire.value)
        )
      val arts = articles.getOrElse(slug, Nil)
      val seed = seedId(Goal.Ref(slug))
      val compileOpen = agents.managerStore
        .openOf(
          seed,
          List(ManagerItem.Kind(ArticleCompile)),
          List(ManagerItem.Status("pending"), ManagerItem.Status("held"), ManagerItem.Status("active"))
        )
        .value > 0 ||
        agents.managerStore
          .openOf(
            seed,
            List(ManagerItem.Kind(ProposeArticles), ManagerItem.Kind(PlanGoalItem)),
            List(ManagerItem.Status("held"), ManagerItem.Status("active")),
            focusHeld = false
          )
          .value > 0
      val kids = goals
        .filter(_.parentSlug.exists(_.value == slug))
        .map(c => DomainGoal.Status.Wire(status.getOrElse(c.slug.value, c.status).value))
      val facts = GoalFacts(
        GoalFacts.Slug(slug),
        DomainGoal.Status.Wire(status(slug).value),
        GoalFacts.Managed(managed),
        GoalFacts.Articles(arts.size),
        GoalFacts.CompileOpen(compileOpen),
        GoalFacts.Reviewed(arts.forall(passed)),
        kids
      )
      AutopilotPolicy.advance(facts).flatMap { (to, why) =>
        agents.host.patchGoal(slugOf(g), Goal.Patch(status = Some(Goal.StatusValue(to.value)))) match
          case Left(e) =>
            scribe.warn(s"autopilot: goal $slug not moved to $to: ${e.problems.sentences.mkString("; ")}")
            None
          case Right(_) =>
            val from = status(slug)
            status(slug) = Goal.StatusValue(to.value)
            val line = s"goal $slug: $from → $to ($why)"
            mine
              .find(_.kind.value == Kind.Seed.wire.value)
              .orElse(mine.headOption)
              .foreach(m => event(ManagerInfo.Id(m.id.value), line))
            Some(line)
      }
    }

  // ── the next article (#3763) ───────────────────────────────────────────────────────────────────

  /** Every goal whose articles are all compiled and passed review gets its next proposal
    * ([[AutopilotPolicy.nextArticle]]): its seeding manager's propose-articles item re-opened (the scheduler starts it;
    * the plan goes through the article-plan approvals). One line per goal.
    */
  def nextArticles(now: Instant): List[String] =
    if acfg.nextArticleHours.value <= 0 then Nil
    else
      val seeds = agents.managerStore
        .light()
        .filter(_.kind.value == Kind.Seed.wire.value)
        .flatMap(m => m.goal.map(g => (g.value: String) -> m))
        .toMap
      agents.focus.facts().filter(f => f.articles.value > 0 && seeds.contains(f.slug.value)).flatMap { f =>
        val sid = seedId(Goal.Ref(f.slug.value))
        val item = agents.managerStore.itemFor(sid, ManagerItem.Kind(ProposeArticles), itemOf(f.slug))
        val facts = AutopilotPolicy.NextFacts(
          AutopilotPolicy.NextFacts.Goal(f.slug.value),
          f.status,
          AutopilotPolicy.NextFacts.Articles(f.articles.value),
          AutopilotPolicy.NextFacts.Unreviewed(f.unreviewed.value),
          AutopilotPolicy.NextFacts.CompileOpen(f.compileOpen.value),
          seeds.get(f.slug.value).map(m => DomainManager.State.Wire(m.state.value)),
          item.map(i => DomainItemStatus.Wire(i.status.value)),
          item.flatMap(i => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(i.updatedAt.value)))
        )
        AutopilotPolicy.nextArticle(facts, now, AutopilotPolicy.CooldownHours(acfg.nextArticleHours.value)).map { why =>
          val (id, _) = agents.managerStore.upsert(
            sid,
            ManagerItem.Kind(ProposeArticles),
            itemOf(f.slug),
            ItemOrigin.Selection,
            ManagerItem.Priority(0),
            reopen = true
          )
          agents.managerStore.event(
            sid,
            Some(id),
            None,
            ManagerEvent.Kind(Feed.Autopilot),
            ManagerEvent.Text(s"${f.slug}: $why".take(2000))
          )
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
    val backupDue = AutopilotPolicy.due(
      row.lastBackupAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value))),
      AutopilotPolicy.EveryHours(acfg.backupHours.value),
      now
    )
    val lintDue = AutopilotPolicy.due(
      row.lastLintAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value))),
      AutopilotPolicy.EveryHours(acfg.lintHours.value),
      now
    )
    if !(backupDue || lintDue) then Nil
    else
      agents.host.housekeepingBlock match
        case Some(why)                                            => List(s"housekeeping waits: $why")
        case None if !housekeepingBusy.compareAndSet(false, true) => Nil
        case None                                                 =>
          if backupDue then agents.autopilotRow.backedUp(now)
          if lintDue then agents.autopilotRow.linted(now)
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
    val line = agents.host.backup() match
      case Left(e)    => s"autopilot: the scheduled backup failed: ${e.problems.sentences.mkString("; ")}"
      case Right(st0) =>
        // the request answers `running` after a while: follow the job to its end
        var st = st0 // the job's last status, polled until it settles
        val deadline = agents.timer.after(scala.concurrent.duration.FiniteDuration(3L, scala.concurrent.duration.HOURS))
        while st.stateOf.contains(Backup.State.Running) && !deadline.reached do
          agents.pause.sleep(scala.concurrent.duration.FiniteDuration(10000L, scala.concurrent.duration.MILLISECONDS))
          st = agents.host.backupStatus.getOrElse(st)
        st.result match
          case Some(r) =>
            s"autopilot: backup ${r.file} (${r.bytes} bytes)" +
              (if r.pruned.isEmpty then "" else s"; pruned ${r.pruned.mkString(", ")}")
          case None =>
            s"autopilot: the scheduled backup ${st.file} ended ${st.state}${st.error.fold("")(e => s": ${e.sentence}")}"
    log(line)

  private def lint(): Unit =
    val line = agents.host.lintFix() match
      case Left(e)  => s"autopilot: the scheduled lint --fix failed: ${e.problems.sentences.mkString("; ")}"
      case Right(r) =>
        s"autopilot: lint --fix — ${r.edgesResolved} edge(s) resolved, ${r.sourcesReindexed} source(s) re-indexed" +
          (if r.remaining.value > 0 then s", ${r.remaining} left for the next run" else "")
    log(line)

  private def log(line: String): Unit =
    val _ = agents.workLists.add(WorkListControllers.ListName("log"), QueueAdd(QueueItem.Text(line.take(1000))))
    scribe.info(line)

  // ── status ─────────────────────────────────────────────────────────────────────────────────────

  /** Per goal: its stage, managers, counts, open approvals, last activity and pace — a few queries for every goal at
    * once (readable with agents off).
    */
  def status(now: Instant = Instant.now(agents.clock)): EngineStatus =
    val row = agents.autopilotRow.row()
    val goals = agents.goals.all()
    val managers = agents.managerStore.light()
    val pace = agents.managerStore.pace(now.minusSeconds(7L * 86400L))
    val links = agents.goals.linkCounts()
    val arts = agents.workflows.linked().map((k, v) => (k.value: String) -> v.map(a => a.value: String))
    val open = agents.approvalStore.openBySubject().map((k, v) => (k.value: String) -> v.value)
    def linkCount(g: Goal, kind: DomainGoal.LinkKind): Int =
      links.getOrElse((slugOf(g), kind.wire), RowCount(0)).value
    def brief(m: ManagerLight) =
      ManagerBrief(
        ManagerBrief.Id(m.id.value),
        ManagerBrief.Kind(m.kind.value),
        ManagerBrief.State(m.state.value),
        m.statusNote.map(n => ManagerBrief.Note(n.value))
      )
    val progress = goals.sortBy(_.slug.value).map { g =>
      val mine = managers.filter(_.goal.exists(_.value == g.slug.value))
      val p = mine.flatMap(m => pace.get(ManagerInfo.Id(m.id.value)))
      val (o, d) = (p.map(_._1.value).sum, p.map(_._2.value).sum)
      val mentions = arts.getOrElse(g.slug.value, Nil)
      GoalProgress(
        GoalProgress.Slug(g.slug.value),
        GoalProgress.Title(g.title.value),
        GoalProgress.Status(g.status.value),
        g.parentSlug.map(parent => GoalProgress.Parent(parent.value)),
        mine.map(brief),
        GoalProgress.Sources(linkCount(g, DomainGoal.LinkKind.Source)),
        GoalProgress.Articles(mentions.size),
        GoalProgress.OpenApprovals(open.getOrElse(g.slug.value, 0) + mentions.map(open.getOrElse(_, 0)).sum),
        p.flatMap(_._3.map(_.value)).maxOption.map(t => GoalProgress.LastActivity(t)),
        GoalProgress.DoneLast7(d),
        GoalProgress.Open(o),
        AutopilotPolicy
          .etaDays(AutopilotPolicy.OpenCount(o), AutopilotPolicy.DoneLast7(d))
          .map(e => GoalProgress.EtaDays(e.value))
      )
    }
    val caps = cfg.approvals.auto
    def rule(k: Agents.ApprovalKind, cap: String) =
      ApprovalRule(
        k.wire,
        if cfg.approvals.checked(k.transformInto[Approval.Kind]) then Autopilot.ApprovalRule.Mode("checked")
        else Autopilot.ApprovalRule.Mode("person"),
        ApprovalRule.Cap(cap)
      ) match
        case r if k == Agents.ApprovalKind.ArticlePublish && cfg.approvals.publishMode == DomainApproval.Mode.Auto =>
          r.modify(_.mode).setTo(ApprovalRule.Mode("auto (in the run, unchecked)"))
        case r => r
    val st = EngineStatus(
      EngineStatus.On(row.enabled.value),
      Some(EngineStatus.Since(row.changedAt.value)),
      Option(row.changedBy.value).filter(_.nonEmpty).map(by => EngineStatus.By(by)),
      EngineStatus.AgentsEnabled(cfg.enabled.value),
      EngineStatus.ManagersEnabled(cfg.managers.enabled.value),
      EngineStatus.CronsEnabled(cfg.scheduler.enabled.value),
      List(
        rule(
          Agents.ApprovalKind.GoalPlan,
          AutopilotPolicy
            .capText(
              AutopilotPolicy.Base(caps.goalPlansPerDay.value),
              AutopilotPolicy.Backlog(agents.approvalStore.openCount(Agents.ApprovalKind.GoalPlan).value),
              AutopilotPolicy.Share(caps.goalPlansBacklogShare.value),
              AutopilotPolicy.Cap(caps.goalPlansCeiling.value)
            )
            .value +
            s", sub-goals above depth ${caps.maxGoalDepth}"
        ),
        rule(Agents.ApprovalKind.ArticlePlan, s"${caps.articlesPerGoalPerDay} articles per goal a day"),
        rule(
          Agents.ApprovalKind.ArticlePublish,
          AutopilotPolicy
            .capText(
              AutopilotPolicy.Base(caps.publishPerDay.value),
              AutopilotPolicy.Backlog(agents.approvalStore.openCount(Agents.ApprovalKind.ArticlePublish).value),
              AutopilotPolicy.Share(caps.publishBacklogShare.value),
              AutopilotPolicy.Cap(caps.publishCeiling.value)
            )
            .value + s", filter at least ${caps.publishMinSources} sources"
        )
      ),
      EngineStatus.IngestedToday(ingestedToday(now).value),
      EngineStatus.IngestCap(if row.enabled.value then ingestCap.value else 0),
      EngineStatus.MaxConcurrentRuns(cfg.maxConcurrentRuns.value),
      row.lastBackupAt.map(t => EngineStatus.LastBackup(t.value)),
      row.lastLintAt.map(t => EngineStatus.LastLint(t.value)),
      managers.filter(m => m.id.value == ReviewId || m.id.value == EnrichId).map(brief),
      progress,
      focus = Some(agents.focus.info(now)),
      drain = agents.drain.info(),
      goalPlansWait = agents.focus.goalPlansWait().map(w => EngineStatus.GoalPlansWait(w.value))
    )
    // the domains' parts: the ingest backlog
    agents.workflows.managerKinds.foldLeft(st)((s, k) => k.status(s, now))
