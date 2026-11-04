package com.histmersion.domain.automation.goals

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
import com.histmersion.api.automation.Goal

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
    agents: AgentsHandlers
):

  private def acfg = cfg.autopilot

  /** The cap and where it comes from: a person's (`focus max`) or the config's. */
  def cap: (Int, String) =
    Transactor(ds)
      .read(FocusRepo.maxOverride)
      .fold((math.max(0, acfg.maxActiveGoals), "config"))(n => (math.max(0, n), "person"))

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
  @volatile private var current: Option[Set[String]] = None

  /** The active goals now, as the last pass decided them (None: not in force). */
  def activeGoals: Option[Set[String]] = current

  /** The unfinished goals the last pass did NOT make active (their model items wait). */
  @volatile private var queued: Set[String] = Set.empty

  /** The status line of a seeding manager or watcher whose goal waits for a focus slot (else `note`). */
  def statusNote(goal: Option[String], note: String): String =
    goal.filter(g => current.isDefined && queued(g)).fold(note)(_ => s"focus: its goal waits for a slot — $note")

  // ── the facts ──────────────────────────────────────────────────────────────────────────────────

  /** Does only a person decide an approval of `kind` opened by a run of a manager whose `autoApprove` is `auto` (the
    * same rule as [[AutoApprovals.checked]])?
    */
  private def byPerson(kind: Option[String], autoApprove: Option[String]): Boolean =
    kind.flatMap(ApprovalKind.parse) match
      case Some(k) if List(ApprovalKind.ArticlePlan, ApprovalKind.GoalPlan, ApprovalKind.ArticlePublish).contains(k) =>
        !autoApprove
          .flatMap(_.toBooleanOption)
          .getOrElse(cfg.approvals.checked(k.transformInto[com.histmersion.domain.automation.Approval.Kind]))
      case _ => true

  final private case class Read(
      facts: List[Facts],
      work: List[FocusRepo.WorkRow],
      titles: Map[String, String],
      left: Map[String, (Option[String], Option[String])],
      openPlans: Int
  )

  private def read(): Read =
    val goals = agents.focusStore.goals()
    val rows = Transactor(ds).read(FocusRepo.work)
    val work = rows.map(r =>
      r.goals -> Work(
        r.id,
        r.kind,
        r.status,
        r.target,
        r.person,
        r.focusHeld,
        r.personHeld,
        r.approval,
        r.approval.isDefined && byPerson(r.approvalKind, r.autoApprove)
      )
    )
    val base = goals.map(g =>
      Facts(
        g.slug,
        g.parent,
        g.status,
        g.createdAt,
        pinned = g.pinned,
        priority = g.priority,
        active = g.active,
        since = g.since
      )
    )
    val facts = GoalFocus.build(
      base,
      work,
      agents.focusStore.sourceCounts(),
      agents.workflows.linked(),
      agents.workflows.finished(),
      Transactor(ds).read(FocusRepo.compileOpen),
      agents.focusStore.lastStops()
    )
    Read(
      facts,
      rows,
      goals.map(g => g.slug -> g.title).toMap,
      goals.map(g => g.slug -> (g.leftAt, g.leftWhy)).toMap,
      Transactor(ds).read(FocusRepo.openGoalPlans)
    )

  private def settings(r: Read, max: Int): GoalFocus.Settings =
    GoalFocus.Settings(
      max,
      agents.sourcesToPlan,
      plansByPerson = !cfg.approvals.checked(com.histmersion.domain.automation.Approval.Kind.GoalPlan),
      r.openPlans
    )

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** One pass at `now`, from the managers' tick before the dispatch; one line per change. */
  def tick(now: Instant): List[String] =
    if !inForce then
      current = None
      queued = Set.empty
      val released = agents.managerStore.focusReleaseAll()
      val cleared = Transactor(ds).transact(FocusRepo.clearActive(notInForce, now))
      if released + cleared == 0 then Nil
      else
        val line = s"autopilot focus: not in force ($notInForce): ${Plural.count(released, "item")} released, " +
          s"${Plural.count(cleared, "goal")} no longer active"
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

  private def notInForce: String = "the central scheduler admits the work (agents.scheduler.strategy)"

  /** The goal's manager an event about it goes to: its seeding manager, else its watcher. */
  private def managerOf(goal: String): Option[String] =
    List(seedId(goal), watchId(goal)).find(id => agents.managerStore.get(id).isDefined)

  private def event(goal: String, text: String): Unit =
    managerOf(goal).foreach(id => agents.managerStore.event(id, None, None, Feed.Autopilot, text.take(2000)))

  /** The goals that joined and left, recorded with why. */
  private def record(plan: Plan, r: Read, now: Instant): List[String] =
    val joined = plan.joined.flatMap(g => plan.active.find(_.slug == g)).map { p =>
      agents.focusStore.joined(p.slug, p.why, now)
      event(p.slug, s"focus: active — ${p.why}")
      s"+${p.slug} (${p.stage.wire})"
    }
    val left = plan.left.map { (g, why) =>
      agents.focusStore.left(g, why, now)
      event(g, s"focus: no longer active — $why")
      s"-$g ($why)"
    }
    joined ++ left

  /** Hold what waits, release what may run; one line per manager that changed. */
  private def applyHolds(plan: Plan, r: Read): List[String] =
    val items = r.work.map(w => GoalFocus.Item(w.id, w.kind, w.status, w.goals, w.person, w.focusHeld))
    val hs = GoalFocus.holds(items, plan, r.facts)
    val mgr = r.work.map(w => w.id -> w.managerId).toMap
    val held =
      hs.hold.filter((id, goal, why) => agents.managerStore.focusHold(id, Hold(reason = why, focus = Some(goal))))
    val released = if hs.release.isEmpty then 0 else agents.managerStore.focusRelease(hs.release)
    val heldBy = held.groupBy((id, _, _) => mgr.getOrElse(id, ""))
    heldBy.toList.sortBy(_._1).foreach { (m, xs) =>
      agents.managerStore.event(
        m,
        None,
        None,
        Feed.Held,
        s"focus: ${Plural.count(xs.size, "item")} held — ${xs.head._3}".take(2000)
      )
    }
    hs.release.groupBy(id => mgr.getOrElse(id, "")).toList.sortBy(_._1).foreach { (m, xs) =>
      agents.managerStore.event(
        m,
        None,
        None,
        Feed.Held,
        s"focus: ${Plural.count(xs.size, "item")} released — its goal is active"
      )
    }
    (if held.isEmpty then Nil else List(s"${Plural.count(held.size, "item")} held")) ++
      (if released == 0 then Nil else List(s"${Plural.count(released, "item")} released"))

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
      case Some(active) if m.id == EnrichId || m.id == ReviewId =>
        val link = if m.id == EnrichId then Goal.Plans.LinkKind.Source.wire else Goal.Plans.LinkKind.Article.wire
        val ratio = acfg.focusRatio
        val recent = Transactor(ds).read(FocusRepo.recentTargets(m.id, math.max(1, ratio)))
        val goalsOf = agents.focusStore.goalsOf(link, (due.map(_.target) ++ recent).distinct)
        def mine(t: String) = goalsOf.getOrElse(t, Nil).exists(active)
        val person = pending.filter(_.origin == ItemOrigin.Person.wire).map(_.id).toSet
        GoalFocus.fairOrder(due, c => person(c.id) || mine(c.target), recent.map(mine), ratio)
      case _ => due

  // ── goal plans ─────────────────────────────────────────────────────────────────────────────────

  /** Why a goal plan proposing `proposed` sub-goals for `goal` is not decided by code, if it is not: its unfinished
    * sub-goals (every level below it) plus the new ones would pass `max-open-sub-goals`
    * ([[AutopilotPolicy.goalPlanSubGoals]]). Whether the goal is "active" no longer matters (#3762 proposal 2: the
    * focus and the plan approvals waited for each other — a goal waiting for its plan was never active, so code never
    * decided its plan).
    */
  def goalPlanBlock(goal: String, proposed: Int): Option[String] =
    val goals = agents.focusStore.goals()
    val kids = goals.filter(_.parent.isDefined).groupMap(_.parent.get)(identity)
    def open(s: String, seen: Set[String]): Int =
      kids
        .getOrElse(s, Nil)
        .filterNot(k => seen(k.slug))
        .map { k =>
          (if GoalFocus.finished(k.status) then 0 else 1) + open(k.slug, seen + k.slug)
        }
        .sum
    AutopilotPolicy.goalPlanSubGoals(goal, open(goal, Set(goal)), proposed, acfg.maxOpenSubGoals)

  /** How many goals with an approved plan are unfinished now. */
  def unfinishedPlanned(): Int =
    AutopilotPolicy.unfinishedPlanned(agents.focusStore.goals().map(g => g.slug -> g.status), agents.goals.planned())

  /** Why goal plans are not offered now, if they are not ([[AutopilotPolicy.goalPlansWait]]). */
  def goalPlansWait(): Option[String] =
    val limit = acfg.maxUnfinishedGoals
    if limit <= 0 then None else AutopilotPolicy.goalPlansWait(unfinishedPlanned(), limit)

  // ── what it shows ──────────────────────────────────────────────────────────────────────────────

  /** The focus as the next pass would decide it (read-only: with the autopilot off, what it WOULD focus on). */
  def info(now: Instant = Instant.now(agents.clock), changes: List[String] = Nil): FocusInfo =
    val (max, from) = cap
    val r = read()
    val plan = GoalFocus.select(r.facts, settings(r, if max > 0 then max else acfg.maxActiveGoals))
    val pace = agents.managerStore.pace(now.minusSeconds(7L * 86400L))
    val byGoal = r.facts.map(f => f.slug -> f).toMap
    def goal(p: GoalFocus.Pick, active: Boolean): FocusGoal =
      val f = byGoal.get(p.slug)
      val ps = List(seedId(p.slug), watchId(p.slug)).flatMap(pace.get)
      val (open, done) = (f.fold(0)(_.work.size), ps.map(_._2).sum)
      FocusGoal(
        p.slug,
        r.titles.getOrElse(p.slug, ""),
        f.fold("")(_.status),
        p.stage.wire,
        p.why,
        p.pinned,
        f.fold(0)(_.priority),
        p.since,
        if active then f.flatMap(x => GoalFocus.doing(x.work)) else None,
        open,
        done,
        GoalFocus.etaDays(open, done)
      )
    val shown = math.max(0, acfg.focusQueueShown)
    val note =
      if !agents.autopilot.on then Some("the autopilot is off: nothing is held; this is what it would focus on")
      else if !inForce then
        Some(
          "the central scheduler admits the work (histmersion agent scheduler): the focus holds " +
            "nothing; this is how it ranks the goals, and its pins and priorities are the goals' priorities in the scheduler"
        )
      else if max <= 0 then
        Some(
          s"no cap (max 0${if from == "person" then ", set by a person" else ""}): every goal is active; " +
            s"the order shown is the one a cap of ${acfg.maxActiveGoals} would use"
        )
      else None
    FocusInfo(
      inForce,
      max,
      from,
      plan.active.map(goal(_, active = true)),
      plan.queue.take(shown).map(goal(_, active = false)),
      plan.queue.size,
      plan.waiting.map((s, c, w) => FocusWaiting(s, c, w, byGoal.get(s).exists(_.pinned))),
      agents.managerStore.focusHeldCount(),
      note,
      changes
    )

  // ── a person's settings ────────────────────────────────────────────────────────────────────────

  /** `pin` / `unpin` / `priority` a goal, or the cap (`max`; no value: back to the config's). Applied at the next
    * managers' tick (nudged now); answers the focus as the next pass will decide it.
    */
  def set(in: FocusSet, now: Instant = Instant.now(agents.clock)): Either[Api.Error, FocusInfo] =
    val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
    def goal = in.goal.map(_.trim).filter(_.nonEmpty).toRight(Api.Error(s"`${in.action}` needs `goal`"))
    def missing(g: String) = Api.Error(s"no goal '$g'", Some(Write.CodeNotFound))
    val done: Either[Api.Error, String] = in.action.trim.toLowerCase match
      case "pin" | "unpin" =>
        val on = in.action.trim.equalsIgnoreCase("pin")
        goal.flatMap(g => Either.cond(agents.focusStore.pin(g, on), g, missing(g))).map { g =>
          event(g, s"focus: ${if on then "pinned" else "unpinned"} by $by")
          s"$g ${if on then "pinned: it is active from the next tick" else "unpinned"} (by $by)"
        }
      case "priority" =>
        for
          g <- goal
          n <- in.value.toRight(Api.Error("`priority` needs `value` (higher first)"))
          _ <- Either.cond(agents.focusStore.priority(g, n), (), missing(g))
        yield
          event(g, s"focus: priority $n set by $by")
          s"$g priority $n (by $by)"
      case "max" =>
        in.value match
          case Some(n) if n < 0 => Left(Api.Error("`max` is 0 (no cap) or more"))
          case v                =>
            Transactor(ds).transact(FocusRepo.setMaxOverride(v))
            Right(
              v.fold(s"cap back to agents.autopilot.max-active-goals (${acfg.maxActiveGoals}) (by $by)")(n =>
                s"cap $n${if n == 0 then " (no cap)" else ""} (by $by)"
              )
            )
      case other => Left(Api.Error(s"unknown focus action '$other' (one of: ${FocusSet.Actions.mkString(", ")})"))
    done.map { line =>
      log(s"autopilot focus: $line")
      agents.managers.nudge()
      info(now, List(line))
    }

  private def log(line: String): Unit =
    val _ = agents.workLists.add("log", QueueAdd(line.take(1000)))
    scribe.info(line)
