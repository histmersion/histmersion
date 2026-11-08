package com.histmersion
package domain.automation.goals

import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.approvals.AutoApprovals
import com.histmersion.domain.automation.scheduler.SchedulerService

import com.histmersion.api.{Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.ApprovalKind
import com.histmersion.api.automation.Autopilot.*
import com.histmersion.domain.GoalFocus.Facts
import com.histmersion.domain.GoalFocus.Plan
import com.histmersion.domain.GoalFocus.Work
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.QueueAdd

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal as ApiGoal
import com.histmersion.domain.automation.Goal
import com.histmersion.api.automation.Approval
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.automation.Queue

/** The autopilot's FOCUS (docs/research-process-managers.md "As built: the focus"): while the autopilot is on and the
  * cap (`agents.autopilot.max-active-goals`, or a person's `focus max`) is above 0, at most that many research goals
  * are ACTIVE for model work. Every managers' tick, just before the dispatch ([[tick]]):
  *
  *   1. the facts of every goal are read in a few aggregate queries and [[GoalFocus.select]] decides the active set
  *      (finish-first, depth-first, a person's pins and priority, then the oldest);
  *   2. the goals that joined or left are recorded (`goal_focus`), as an `autopilot` event on the goal's seeding
  *      manager (else its watcher) and one worklog line;
  *   3. [[GoalFocus.holds]]: a pending model-using item of a goal that is not active is HELD with a `focus` hold
  *      (nothing is cancelled; runs in flight finish); a focus hold whose goal became active is released.
  *
  * The currency check and the ingest backlog use no model and are never held; the enrich and review managers start the
  * active goals' items first ([[fair]]: `focus-ratio` to one). Not in force (the autopilot off, a cap of 0), every
  * focus hold is released and no goal is recorded active. The focus decides WHAT runs: `max-concurrent-runs`, the
  * budgets, plan floors and daily caps decide how much.
  */
final class GoalFocusService(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsControllers
):

  private def acfg = cfg.autopilot

  /** The cap and where it comes from: a person's (`focus max`) or the config's. */
  def cap: (GoalFocus.Settings.Max, FocusInfo.MaxFrom) =
    Transactor(ds)
      .read(FocusRepo.maxOverride)
      .fold((GoalFocus.Settings.Max(math.max(0, acfg.maxActiveGoals.value)), FocusInfo.MaxFrom("config")))(n =>
        (GoalFocus.Settings.Max(math.max(0, n.value)), FocusInfo.MaxFrom("person"))
      )

  /** The focus is in force — never since the central scheduler (#3933, [[SchedulerService]]) admits the managers' work:
    * the focus holds nothing; it ranks the goals (what [[info]] shows), and its pins and priorities are the goals'
    * priorities in the scheduler.
    */
  def inForce: Boolean = false

  /** Every goal's facts as the focus reads them (the scheduler's goal priorities, ages and stages). */
  def facts(): List[Facts] = read().facts

  /** The active goals of the last pass (None: the focus is not in force). Read by the dispatch, written by [[tick]]; a
    * `var` because the supervisor's dispatch reads what the pass just before it decided.
    */
  @volatile private var current: Option[Set[Goal.Slug]] = None

  /** The active goals now, as the last pass decided them (None: not in force). */
  def activeGoals: Option[Set[Goal.Slug]] = current

  /** The unfinished goals the last pass did NOT make active (their model items wait). */
  @volatile private var queued: Set[Goal.Slug] = Set.empty

  /** The status line of a seeding manager or watcher whose goal waits for a focus slot (else `note`). */
  def statusNote(goal: Option[Goal.Slug], note: ManagerInfo.StatusNote): ManagerInfo.StatusNote =
    goal
      .filter(g => current.isDefined && queued(g))
      .fold(note)(_ => ManagerInfo.StatusNote(s"focus: its goal waits for a slot — ${note.value}"))

  private def seed(goal: Goal.Slug): ManagerInfo.Id = seedId(ApiGoal.Ref(goal.value))

  private def watch(goal: Goal.Slug): ManagerInfo.Id = watchId(ApiGoal.Ref(goal.value))

  private def count(n: Int, noun: String): String = Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(noun)).value

  // ── the facts ──────────────────────────────────────────────────────────────────────────────────

  /** Does only a person decide an approval of `kind` opened by a run of a manager whose `autoApprove` is `auto` (the
    * same rule as [[AutoApprovals.checked]])?
    */
  private def byPerson(kind: Option[FocusRepo.WorkRow.ApprovalKind], autoApprove: Option[FocusRepo.WorkRow.AutoApprove]): Boolean =
    kind.flatMap(k => ApprovalKind.parse(Approval.Kind.Wire(k.value))) match
      case Some(k) if List(ApprovalKind.ArticlePlan, ApprovalKind.GoalPlan, ApprovalKind.ArticlePublish).contains(k) =>
        !autoApprove
          .flatMap(_.value.toBooleanOption)
          .getOrElse(cfg.approvals.checked(k.transformInto[com.histmersion.domain.automation.Approval.Kind]))
      case _ => true

  final private case class Read(
      facts: List[Facts],
      work: List[FocusRepo.WorkRow],
      titles: Map[Goal.Slug, FocusRepo.GoalRow.Title],
      left: Map[Goal.Slug, (Option[FocusRepo.GoalRow.LeftAt], Option[FocusRepo.GoalRow.LeftWhy])],
      openPlans: RowCount
  )

  private def read(): Read =
    val goals = agents.focusStore.goals()
    val rows = Transactor(ds).read(FocusRepo.work)
    val work = rows.map(r =>
      r.goals.map(g => Goal.Slug.assume(g.value)) -> Work(
        Work.ItemId(r.id.value),
        ManagerItem.Kind(r.kind.value),
        com.histmersion.domain.automation.ItemStatus.Wire(r.status.value),
        Work.Target(r.target.value),
        Work.Person(r.person.value),
        Work.FocusHeld(r.focusHeld.value),
        Work.PersonHeld(r.personHeld.value),
        r.approval.map(a => Work.Approval(a.value)),
        Work.ApprovalByPerson(r.approval.isDefined && byPerson(r.approvalKind, r.autoApprove))
      )
    )
    val base = goals.map(g =>
      Facts(
        Goal.Slug.assume(g.slug.value),
        g.parent.map(p => Goal.Slug.assume(p.value)),
        Goal.Status.Wire(g.status.value),
        Facts.CreatedAt(g.createdAt.value),
        pinned = Facts.Pinned(g.pinned.value),
        priority = Facts.Priority(g.priority.value),
        active = Facts.Active(g.active.value),
        since = g.since.map(s => Facts.Since(s.value))
      )
    )
    val facts = GoalFocus.build(
      base,
      work,
      agents.focusStore.sourceCounts().map((g, n) => g -> Facts.Sources(n.value)),
      agents.workflows.linked(),
      agents.workflows.finished(),
      Transactor(ds).read(FocusRepo.compileOpen).map((g, n) => g -> Facts.CompileOpen(n.value)),
      agents.focusStore.lastStops().map((g, why) => g -> Facts.LastStop(why.value))
    )
    Read(
      facts,
      rows,
      goals.map(g => Goal.Slug.assume(g.slug.value) -> g.title).toMap,
      goals.map(g => Goal.Slug.assume(g.slug.value) -> (g.leftAt, g.leftWhy)).toMap,
      Transactor(ds).read(FocusRepo.openGoalPlans)
    )

  private def settings(r: Read, max: GoalFocus.Settings.Max): GoalFocus.Settings =
    GoalFocus.Settings(
      max,
      GoalFocus.Settings.MinSources(agents.sourcesToPlan.value),
      plansByPerson =
        GoalFocus.Settings.PlansByPerson(!cfg.approvals.checked(com.histmersion.domain.automation.Approval.Kind.GoalPlan)),
      GoalFocus.Settings.OpenPersonPlans(r.openPlans.value)
    )

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** One pass at `now`, from the managers' tick before the dispatch; one line per change. */
  def tick(now: Instant): List[String] =
    if !inForce then
      current = None
      queued = Set.empty
      val released = agents.managerStore.focusReleaseAll()
      val cleared = Transactor(ds).transact(FocusRepo.clearActive(notInForce, now))
      if released.value + cleared.value == 0 then Nil
      else
        val line = s"autopilot focus: not in force (${notInForce.value}): ${count(released.value, "item")} released, " +
          s"${count(cleared.value, "goal")} no longer active"
        log(line)
        List(line)
    else
      val r = read()
      val plan = GoalFocus.select(r.facts, settings(r, cap._1))
      current = Some(plan.activeSet)
      queued = r.facts.filter(f => !GoalFocus.finished(f.status) && !plan.activeSet(f.slug)).map(_.slug).toSet
      val changes = record(plan, r, now) ++ applyHolds(plan, r)
      if changes.nonEmpty then log(s"autopilot focus: ${changes.mkString("; ")}".take(1000))
      changes

  private def notInForce: FocusRepo.GoalRow.LeftWhy = FocusRepo.GoalRow.LeftWhy("the central scheduler admits the work (agents.scheduler.strategy)")

  /** The goal's manager an event about it goes to: its seeding manager, else its watcher. */
  private def managerOf(goal: Goal.Slug): Option[ManagerInfo.Id] =
    List(seed(goal), watch(goal)).find(id => agents.managerStore.get(id).isDefined)

  private def emit(manager: ManagerInfo.Id, feed: String, text: String): Unit =
    agents.managerStore.event(manager, None, None, ManagerEvent.Kind(feed), ManagerEvent.Text(text.take(2000)))

  private def event(goal: Goal.Slug, text: String): Unit =
    managerOf(goal).foreach(id => emit(id, Feed.Autopilot, text))

  /** The goals that joined and left, recorded with why. */
  private def record(plan: Plan, r: Read, now: Instant): List[String] =
    val joined = plan.joined.flatMap(g => plan.active.find(_.slug == g)).map { p =>
      agents.focusStore.joined(p.slug, FocusRepo.JoinWhy(p.why.value), now)
      event(p.slug, s"focus: active — ${p.why.value}")
      s"+${p.slug.value} (${p.stage.wire.value})"
    }
    val left = plan.left.map { (g, why) =>
      agents.focusStore.left(g, FocusRepo.GoalRow.LeftWhy(why.value), now)
      event(g, s"focus: no longer active — ${why.value}")
      s"-${g.value} (${why.value})"
    }
    joined ++ left

  /** Hold what waits, release what may run; one line per manager that changed. */
  private def applyHolds(plan: Plan, r: Read): List[String] =
    val items = r.work.map(w =>
      GoalFocus.Item(
        Work.ItemId(w.id.value),
        ManagerItem.Kind(w.kind.value),
        com.histmersion.domain.automation.ItemStatus.Wire(w.status.value),
        w.goals.map(g => Goal.Slug.assume(g.value)),
        Work.Person(w.person.value),
        Work.FocusHeld(w.focusHeld.value)
      )
    )
    val hs = GoalFocus.holds(items, plan, r.facts)
    val mgr = r.work.map(w => Work.ItemId(w.id.value) -> w.managerId.value).toMap
    val held =
      hs.hold.filter((id, goal, why) =>
        agents.managerStore.focusHold(
          ManagerItem.Id(id.value),
          Hold(reason = Hold.Reason(why.value), focus = Some(Hold.Focus(goal.value)))
        )
      )
    val released =
      if hs.release.isEmpty then 0
      else agents.managerStore.focusRelease(hs.release.map(id => ManagerItem.Id(id.value))).value
    val heldBy = held.groupBy((id, _, _) => mgr.getOrElse(id, ""))
    heldBy.toList.sortBy(_._1).foreach { (m, xs) =>
      emit(ManagerInfo.Id(m), Feed.Held, s"focus: ${count(xs.size, "item")} held — ${xs.head._3.value}")
    }
    hs.release.groupBy(id => mgr.getOrElse(id, "")).toList.sortBy(_._1).foreach { (m, xs) =>
      emit(ManagerInfo.Id(m), Feed.Held, s"focus: ${count(xs.size, "item")} released — its goal is active")
    }
    (if held.isEmpty then Nil else List(s"${count(held.size, "item")} held")) ++
      (if released == 0 then Nil else List(s"${count(released, "item")} released"))

  // ── the library-wide managers ──────────────────────────────────────────────────────────────────

  /** The enrich and review managers' due items in the order they start: `focus-ratio` items of active goals (and a
    * person's) to one of any other, counting on from their last starts. Any other manager, or the focus not in force:
    * as given.
    */
  def fair(
      m: ManagerInfo,
      due: List[ManagerPolicy.Candidate],
      pending: List[ManagerItem]
  ): List[ManagerPolicy.Candidate] =
    current match
      case Some(active) if m.id.value == EnrichId || m.id.value == ReviewId =>
        val link =
          if m.id.value == EnrichId then Goal.LinkKind.Source.wire
          else Goal.LinkKind.Article.wire
        val ratio = acfg.focusRatio
        val recent = Transactor(ds).read(
          FocusRepo.recentTargets(
            FocusRepo.WorkRow.ManagerId(m.id.value),
            PageSize.assume(math.max(1, ratio.value))
          )
        )
        val goalsOf = agents.focusStore.goalsOf(
          link,
          (due.map(_.target.value) ++ recent.map(_.value)).distinct.map(ApiGoal.Plans.GoalLink.Ref(_))
        )
        def mine(t: String): Boolean = goalsOf.getOrElse(ApiGoal.Plans.GoalLink.Ref(t), Nil).exists(active)
        val person = pending.filter(_.origin == ItemOrigin.Person.wire).map(_.id.value).toSet
        GoalFocus.fairOrder(
          due,
          c => person(c.id.value) || mine(c.target.value),
          recent.map(t => GoalFocus.Recent(mine(t.value))),
          GoalFocus.Ratio(ratio.value)
        )
      case _ => due

  // ── goal plans ─────────────────────────────────────────────────────────────────────────────────

  /** Why a goal plan proposing `proposed` sub-goals for `goal` is not decided by code, if it is not: its unfinished
    * sub-goals (every level below it) plus the new ones would pass `max-open-sub-goals`
    * ([[AutopilotPolicy.goalPlanSubGoals]]). Whether the goal is "active" no longer matters (#3762 proposal 2: the
    * focus and the plan approvals waited for each other — a goal waiting for its plan was never active, so code never
    * decided its plan).
    */
  def goalPlanBlock(goal: Goal.Slug, proposed: AutopilotPolicy.Proposed): Option[AutopilotPolicy.Description] =
    val goals = agents.focusStore.goals()
    val kids = goals.filter(_.parent.isDefined).groupMap(g => g.parent.get.value: String)(identity)
    def open(s: String, seen: Set[String]): Int =
      kids
        .getOrElse(s, Nil)
        .filterNot(k => seen(k.slug.value))
        .map { k =>
          (if GoalFocus.finished(Goal.Status.Wire(k.status.value)) then 0 else 1) + open(k.slug.value, seen + k.slug.value)
        }
        .sum
    AutopilotPolicy.goalPlanSubGoals(
      goal,
      AutopilotPolicy.OpenCount(open(goal.value, Set(goal.value))),
      proposed,
      AutopilotPolicy.Limit(acfg.maxOpenSubGoals.value)
    )

  /** How many goals with an approved plan are unfinished now. */
  def unfinishedPlanned(): AutopilotPolicy.Unfinished =
    AutopilotPolicy.unfinishedPlanned(
      agents.focusStore.goals().map(g => Goal.Slug.assume(g.slug.value) -> Goal.Status.Wire(g.status.value)),
      agents.goals.planned()
    )

  /** Why goal plans are not offered now, if they are not ([[AutopilotPolicy.goalPlansWait]]). */
  def goalPlansWait(): Option[AutopilotPolicy.Description] =
    val limit = acfg.maxUnfinishedGoals
    if limit.value <= 0 then None else AutopilotPolicy.goalPlansWait(unfinishedPlanned(), AutopilotPolicy.Limit(limit.value))

  // ── what it shows ──────────────────────────────────────────────────────────────────────────────

  /** The focus as the next pass would decide it (read-only: with the autopilot off, what it WOULD focus on). */
  def info(now: Instant = Instant.now(agents.clock), changes: List[FocusInfo.Changes] = Nil): FocusInfo =
    val (max, from) = cap
    val r = read()
    val plan =
      GoalFocus.select(r.facts, settings(r, if max.value > 0 then max else GoalFocus.Settings.Max(acfg.maxActiveGoals.value)))
    val pace = agents.managerStore.pace(now.minusSeconds(7L * 86400L))
    val byGoal = r.facts.map(f => f.slug -> f).toMap
    def goal(p: GoalFocus.Pick, active: Boolean): FocusGoal =
      val f = byGoal.get(p.slug)
      val ps = List(seed(p.slug), watch(p.slug)).flatMap(pace.get)
      val (open, done) = (f.fold(0)(_.work.size), ps.map(_._2.value).sum)
      FocusGoal(
        FocusGoal.Slug(p.slug.value),
        FocusGoal.Title(r.titles.get(p.slug).fold("")(_.value)),
        FocusGoal.Status(f.fold("")(_.status.value)),
        FocusGoal.Stage(p.stage.wire.value),
        FocusGoal.Why(p.why.value),
        FocusGoal.Pinned(p.pinned.value),
        FocusGoal.Priority(f.fold(0)(_.priority.value)),
        p.since.map(s => FocusGoal.Since(s.value)),
        if active then f.flatMap(x => GoalFocus.doing(x.work)).map(d => FocusGoal.Doing(d.value)) else None,
        FocusGoal.Open(open),
        FocusGoal.DoneLast7(done),
        GoalFocus.etaDays(AutopilotPolicy.OpenCount(open), AutopilotPolicy.DoneLast7(done)).map(d => FocusGoal.EtaDays(d.value))
      )
    val shown = math.max(0, acfg.focusQueueShown.value)
    val note =
      if !agents.autopilot.on then Some("the autopilot is off: nothing is held; this is what it would focus on")
      else if !inForce then
        Some(
          "the central scheduler admits the work (histmersion agent scheduler): the focus holds " +
            "nothing; this is how it ranks the goals, and its pins and priorities are the goals' priorities in the scheduler"
        )
      else if max.value <= 0 then
        Some(
          s"no cap (max 0${if from.value == "person" then ", set by a person" else ""}): every goal is active; " +
            s"the order shown is the one a cap of ${acfg.maxActiveGoals.value} would use"
        )
      else None
    FocusInfo(
      FocusInfo.On(inForce),
      FocusInfo.Max(max.value),
      from,
      plan.active.map(goal(_, active = true)),
      plan.queue.take(shown).map(goal(_, active = false)),
      FocusInfo.Queued(plan.queue.size),
      plan.waiting.map((s, c, w) =>
        FocusWaiting(
          FocusWaiting.Slug(s.value),
          FocusWaiting.Code(c.value),
          FocusWaiting.Why(w.value),
          FocusWaiting.Pinned(byGoal.get(s).exists(_.pinned.value))
        )
      ),
      FocusInfo.Held(agents.managerStore.focusHeldCount().value),
      note.map(FocusInfo.Note(_)),
      changes
    )

  // ── a person's settings ────────────────────────────────────────────────────────────────────────

  /** `pin` / `unpin` / `priority` a goal, or the cap (`max`; no value: back to the config's). Applied at the next
    * managers' tick (nudged now); answers the focus as the next pass will decide it.
    */
  def set(in: FocusSet, now: Instant = Instant.now(agents.clock)): Either[Api.Error, FocusInfo] =
    def error(message: String): Api.Error = Api.Error(Api.Error.Message(message))
    val by = in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
    def goal: Either[Api.Error, String] =
      in.goal.map(_.value.trim).filter(_.nonEmpty).toRight(error(s"`${in.action.value}` needs `goal`"))
    def missing(g: String) =
      Api.Error(Api.Error.Message(s"no goal '$g'"), Some(Api.Error.Code(Write.CodeNotFound)))
    def known(g: String): Either[Api.Error, Goal.Slug] = Goal.Slug.either(g).left.map(_ => missing(g))
    val done: Either[Api.Error, String] = in.action.value.trim.toLowerCase match
      case "pin" | "unpin" =>
        val on = in.action.value.trim.equalsIgnoreCase("pin")
        goal
          .flatMap(g => known(g).flatMap(s => Either.cond(agents.focusStore.pin(s, FocusRepo.GoalRow.Pinned(on)), s, missing(g))))
          .map { g =>
            event(g, s"focus: ${if on then "pinned" else "unpinned"} by $by")
            s"${g.value} ${if on then "pinned: it is active from the next tick" else "unpinned"} (by $by)"
          }
      case "priority" =>
        for
          g <- goal
          s <- known(g)
          n <- in.value.toRight(error("`priority` needs `value` (higher first)"))
          _ <- Either.cond(agents.focusStore.priority(s, FocusRepo.GoalRow.Priority(n.value)), (), missing(g))
        yield
          event(s, s"focus: priority ${n.value} set by $by")
          s"$g priority ${n.value} (by $by)"
      case "max" =>
        in.value match
          case Some(n) if n.value < 0 => Left(error("`max` is 0 (no cap) or more"))
          case v                      =>
            Transactor(ds).transact(FocusRepo.setMaxOverride(v.map(n => GoalFocus.Settings.Max(n.value))))
            Right(
              v.fold(s"cap back to agents.autopilot.max-active-goals (${acfg.maxActiveGoals.value}) (by $by)")(n =>
                s"cap ${n.value}${if n.value == 0 then " (no cap)" else ""} (by $by)"
              )
            )
      case other => Left(error(s"unknown focus action '$other' (one of: ${FocusSet.Actions.mkString(", ")})"))
    done.map { line =>
      log(s"autopilot focus: $line")
      agents.managers.nudge()
      info(now, List(FocusInfo.Changes(line)))
    }

  private def log(line: String): Unit =
    val _ = agents.workLists.add(WorkListControllers.ListName("log"), QueueAdd(Queue.QueueItem.Text(line.take(1000))))
    scribe.info(line)
