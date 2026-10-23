package com.histmersion.core

import com.histmersion.core.Managers.*

import java.time.{Duration, Instant}

/** The pure half of the autopilot (docs/research-process-managers.md "As built: the autopilot"): the
  * code checks that decide an approval without a person, the rule that moves a research goal's status
  * on from the facts, the managers every goal must have, the spread of their crons, when an article is
  * extended with new sources, and when the housekeeping is due. No I/O: the supervisor reads the facts
  * and applies the result. */
object AutopilotPolicy:

  // ── auto approvals ─────────────────────────────────────────────────────────────────────────────

  /** What the checks say about one open approval: approve it (why, for the note), reject it (why), or
    * leave it for a person (or for tomorrow, when only a daily cap stood in the way). */
  enum Verdict:
    case Approve(reason: String)
    case Reject(reason: String)
    case Leave(why: String)

  /** One article of an `article-plan`, as it stands NOW: its slug still free, its filter's hits. */
  final case class PlannedFacts(slug: String, slugFree: Boolean, hits: Int)

  /** An `article-plan` (propose-articles): approved when every article's slug is still free and its
    * filter returns at least `minSources`, and the area's planned articles approved today stay within
    * `cap`; a plan larger than the cap is a person's. */
  def articlePlan(articles: List[PlannedFacts], minSources: Int, approvedToday: Int, cap: Int): Verdict =
    val n = articles.size
    val bad = articles.flatMap { a =>
      if !a.slugFree then Some(s"${a.slug}: the slug is taken now")
      else if a.hits < minSources then Some(s"${a.slug}: its filter returns ${a.hits} source(s), fewer than $minSources")
      else None
    }
    if n == 0 then Verdict.Reject("the plan names no article")
    else if bad.nonEmpty then Verdict.Reject(s"checks failed — ${bad.mkString("; ")}")
    else if n > cap then Verdict.Leave(s"${Plural.count(n, "article")} exceed the daily cap of $cap for one research area")
    else if approvedToday + n > cap then
      Verdict.Leave(s"the area's daily cap is reached ($approvedToday of $cap articles approved today); decided tomorrow")
    else
      Verdict.Approve(s"${Plural.count(n, "article")}, every slug free and every filter at least $minSources sources " +
        s"(${articles.map(a => s"${a.slug} ${a.hits}").mkString(", ")}); ${approvedToday + n} of $cap today")

  /** A draft to publish (compile-article), as code reads it. `hits` = what the run's filter returned
    * (None: not known). */
  final case class DraftFacts(slug: String, create: Boolean, filterRecorded: Boolean, hits: Option[Int], problems: List[String],
      cited: Int, slugFree: Boolean)

  /** An `article-publish`: approved when the run recorded a filter that returns at least `minSources`
    * sources, the code self-check left no problem, the text cites a source and (a new article) the slug
    * is still free — within `cap` drafts a day. A failed check rejects it (the seeding manager compiles
    * again); a reached cap leaves it for tomorrow. */
  def articlePublish(d: DraftFacts, minSources: Int, approvedToday: Int, cap: Int): Verdict =
    if !d.filterRecorded then Verdict.Reject("the run records no coverage filter")
    else if d.hits.forall(_ < minSources) then
      Verdict.Reject(s"its filter returns ${d.hits.fold("an unknown number of")(_.toString)} source(s), fewer than $minSources")
    else if d.problems.nonEmpty then
      Verdict.Reject(s"the self-check left ${Plural.count(d.problems.size, "problem")}: ${d.problems.take(3).mkString("; ").take(600)}")
    else if d.cited == 0 then Verdict.Reject("the text cites no source")
    else if d.create && !d.slugFree then Verdict.Reject(s"the slug ${d.slug} is taken now")
    else if approvedToday >= cap then Verdict.Leave(s"the daily cap of $cap drafts is reached; decided tomorrow")
    else
      Verdict.Approve(s"filter ${d.hits.getOrElse(0)} sources (at least $minSources), ${Plural.count(d.cited, "source")} cited, " +
        s"no self-check problem; ${approvedToday + 1} of $cap today")

  /** A goal's plan (plan-goal), as code reads it; `depth` = how far below a root goal it is (0 = a root). */
  final case class GoalPlanFacts(goal: String, subGoals: Int, queries: Int, works: Int, depth: Int)

  /** A `goal-plan`: approved when it proposes something, and sub-goals only for a goal less than
    * `maxDepth` deep — within `cap` plans a day. */
  def goalPlan(p: GoalPlanFacts, maxDepth: Int, approvedToday: Int, cap: Int): Verdict =
    if p.subGoals + p.queries + p.works == 0 then Verdict.Reject("the plan proposes nothing")
    else if p.subGoals > 0 && p.depth >= maxDepth then
      Verdict.Reject(s"it proposes ${Plural.count(p.subGoals, "sub-goal")} for a goal ${depthText(p.depth)}; " +
        s"sub-goals stop at depth $maxDepth (agents.approvals.auto.max-goal-depth)")
    else if approvedToday >= cap then Verdict.Leave(s"the daily cap of $cap goal plans is reached; decided tomorrow")
    else
      Verdict.Approve(s"${Plural.count(p.subGoals, "sub-goal")}, ${Plural.count(p.queries, "query", "queries")}, ${Plural.count(p.works, "work")}" +
        s" (depth ${p.depth} of $maxDepth); ${approvedToday + 1} of $cap today")

  /** A goal plan while the focus is on (docs/research-process-managers.md "As built: the focus"): decided by
    * code only when its goal is one of the active goals and its unfinished sub-goals (`openSubGoals`, every
    * level below it) plus the `proposed` new ones stay within `limit`; else why it waits for a person. */
  def goalPlanFocus(goal: String, active: Boolean, openSubGoals: Int, proposed: Int, limit: Int): Option[String] =
    if !active then Some(s"focus: goal $goal is not one of the active goals; its plan waits for a person or its turn")
    else if proposed > 0 && openSubGoals + proposed > math.max(0, limit) then
      Some(s"focus: goal $goal has ${Plural.count(openSubGoals, "unfinished sub-goal")} and the plan adds $proposed; at most " +
        s"$limit (agents.autopilot.max-open-sub-goals) are opened by code")
    else None

  private def depthText(d: Int): String = if d == 0 then "at the root" else s"$d level(s) below a root"

  /** The note an automatic decision carries — what marks it as the autopilot's (and what the daily caps
    * count). */
  val NotePrefix = "autopilot:"
  def note(v: Verdict): Option[String] = v match
    case Verdict.Approve(r) => Some(s"$NotePrefix approved — $r")
    case Verdict.Reject(r)  => Some(s"$NotePrefix rejected — $r")
    case Verdict.Leave(_)   => None

  /** How deep a goal is: the number of parents above it (a cycle or a missing parent stops the walk). */
  def depth(slug: String, parentOf: String => Option[String]): Int =
    @annotation.tailrec
    def go(s: String, seen: Set[String], n: Int): Int = parentOf(s) match
      case Some(p) if !seen(p) && n < 64 => go(p, seen + p, n + 1)
      case _                             => n
    go(slug, Set(slug), 0)

  // ── goal status ────────────────────────────────────────────────────────────────────────────────

  /** What a goal's status is moved on from. `managed` = it has an active seeding manager or watcher;
    * `articles` = the articles linked to it that exist; `compileOpen` = its seeding manager still has an
    * article to compile (or a plan or a proposal waiting for a decision); `reviewed` = every linked
    * article has a review ledger with no open MAJOR and no review item open; `children` = the statuses
    * of its sub-goals. */
  final case class GoalFacts(slug: String, status: String, managed: Boolean, articles: Int, compileOpen: Boolean,
      reviewed: Boolean, children: List[String])

  private def rank(s: String): Int = GoalStatus.all.indexOf(s)

  /** The status the facts support, when it is FURTHER than the goal's own (never back; `done` and an
    * unknown status are a person's): seeding once it is managed; compiled once it has an article (a
    * parent: once its sub-goals are compiled) and nothing is left to compile; reviewed once every article
    * passed its review and every sub-goal is reviewed. With why, for the log. */
  def advance(f: GoalFacts): Option[(String, String)] =
    val cur = rank(f.status)
    if cur < 0 || f.status == GoalStatus.Done then None
    else
      val kids = f.children.map(rank)
      val hasOwn = f.articles > 0
      val compiled = (hasOwn || kids.nonEmpty) && !f.compileOpen && kids.forall(_ >= rank(GoalStatus.Compiled))
      val reviewed = compiled && (!hasOwn || f.reviewed) && kids.forall(_ >= rank(GoalStatus.Reviewed))
      val (target, why) =
        if reviewed then (GoalStatus.Reviewed,
          (if hasOwn then s"its ${Plural.count(f.articles, "article")} passed review" else "") +
            (if kids.nonEmpty then s"${if hasOwn then ", " else ""}its ${Plural.count(kids.size, "sub-goal")} are reviewed" else ""))
        else if compiled then (GoalStatus.Compiled,
          (if hasOwn then s"${Plural.count(f.articles, "article")} compiled, none left to compile" else "nothing left to compile") +
            (if kids.nonEmpty then s", its ${Plural.count(kids.size, "sub-goal")} are compiled" else ""))
        else if f.managed then (GoalStatus.Seeding, "its seeding manager and watcher are active")
        else (f.status, "")
      Option.when(rank(target) > cur)((target, why))

  // ── the managers every goal has ────────────────────────────────────────────────────────────────

  /** A goal as the autopilot sees it: its status, and whether it still needs a plan (it records none and
    * links no query). */
  final case class GoalRow(slug: String, status: String, needsPlan: Boolean)

  /** A manager as the autopilot sees it. `planItem` = it has a plan-goal item (any status). */
  final case class ManagerRow(id: String, kind: String, goal: Option[String], state: String, cron: Option[String],
      autoIngest: Option[Boolean], planItem: Boolean = false, extendMinNew: Option[Int] = None)

  /** The settings `ensure` works with. */
  final case class Settings(watchCron: String, seedCron: String, finishedWatchCron: String, watchIngestPerDay: Int,
      extendMinNew: Int)

  /** One change `ensure` asks for. */
  enum Step:
    /** Create the goal's seeding manager, active, with its cron (and a plan-goal item before its first
      * proposal when the goal has no plan). */
    case CreateSeed(goal: String, cron: String, planFirst: Boolean)
    /** Create the goal's watcher, active, auto-ingest on (at most `perDay` a day), with its cron. */
    case CreateWatch(goal: String, cron: String, perDay: Int)
    /** Resume a library-wide manager, or one the autopilot paused when it was turned off. */
    case Resume(id: String)
    /** Give a manager with no cron its spread cron. */
    case SetCron(id: String, cron: String)
    /** Turn a watcher's auto-ingest on (it never said either way). */
    case AutoIngestOn(id: String, perDay: Int)
    /** A plan-goal item first, for a goal that still needs a plan. */
    case AddPlanItem(id: String, goal: String)
    /** The review manager's extend threshold (it sets none). */
    case ExtendMinNew(id: String, n: Int)

  /** Finished: reviewed or done — such a goal keeps a watcher (new sources, the currency check), no new
    * seeding manager. */
  def finished(status: String): Boolean = status == GoalStatus.Reviewed || status == GoalStatus.Done

  /** What must change so every goal has its managers (idempotent: nothing, when they are there).
    * Library-wide: `review` and `enrich` active (`review` with the extend threshold). Every unfinished
    * goal: a seeding manager (a plan-goal item first when it has no plan) and a watcher with auto-ingest
    * on; a finished goal: a watcher. A manager a person paused stays paused — only the ones in
    * `pausedByAutopilot` are resumed. A manager's settings a person chose are kept (only an unset cron,
    * auto-ingest or extend threshold is filled). */
  def ensure(goals: List[GoalRow], managers: List[ManagerRow], pausedByAutopilot: Set[String], s: Settings): List[Step] =
    val byId = managers.map(m => m.id -> m).toMap
    val library = List(ReviewId, EnrichId).flatMap { id =>
      byId.get(id).toList.flatMap { m =>
        Option.when(m.state == State.Paused.wire)(Step.Resume(id)).toList ++
          Option.when(id == ReviewId && m.extendMinNew.isEmpty && s.extendMinNew > 0)(Step.ExtendMinNew(id, s.extendMinNew)).toList
      }
    }
    val perGoal = goals.sortBy(_.slug).flatMap { g =>
      val done = finished(g.status)
      val seed = byId.get(seedId(g.slug)) match
        case None if done => Nil
        case None         => List(Step.CreateSeed(g.slug, stagger(s.seedCron, seedId(g.slug)), g.needsPlan))
        case Some(m) =>
          Option.when(pausedByAutopilot(m.id) && m.state == State.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(!done && m.cron.isEmpty)(Step.SetCron(m.id, stagger(s.seedCron, m.id))).toList ++
            Option.when(!done && g.needsPlan && !m.planItem && m.state != State.Stopped.wire)(Step.AddPlanItem(m.id, g.slug)).toList
      val watchCron = if done then s.finishedWatchCron else s.watchCron
      val watch = byId.get(watchId(g.slug)) match
        case None => List(Step.CreateWatch(g.slug, stagger(watchCron, watchId(g.slug)), s.watchIngestPerDay))
        case Some(m) =>
          Option.when(pausedByAutopilot(m.id) && m.state == State.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(m.cron.isEmpty)(Step.SetCron(m.id, stagger(watchCron, m.id))).toList ++
            Option.when(m.autoIngest.isEmpty)(Step.AutoIngestOn(m.id, s.watchIngestPerDay)).toList
      seed ++ watch
    }
    library ++ perGoal

  /** The managers the autopilot pauses when it is turned off: the active seeding managers and watchers
    * (the library-wide ones and a person's paused ones are left as they are). */
  def toPause(managers: List[ManagerRow]): List[String] =
    managers.filter(m => m.state == State.Active.wire && (m.kind == Kind.Seed.wire || m.kind == Kind.Watch.wire)).map(_.id)

  // ── crons spread per goal ──────────────────────────────────────────────────────────────────────

  /** A stable non-negative hash of `key` (the same on every machine and every run). */
  def spread(key: String): Int = key.foldLeft(17)((h, c) => 31 * h + c) & Int.MaxValue

  /** `@daily`, `@weekly` and `@monthly` as five fields at a minute, hour (and weekday or day of the
    * month) of `key`'s own, so dozens of managers with the same cron do not fire in the same minute; any
    * other cron is kept as given. */
  def stagger(base: String, key: String): String =
    val h = spread(key)
    val (min, hour, day) = (h % 60, (h / 60) % 24, h / 1440)
    base.trim.toLowerCase match
      case "@daily"   => s"$min $hour * * *"
      case "@weekly"  => s"$min $hour * * ${day % 7}"
      case "@monthly" => s"$min $hour ${1 + day % 28} * *"
      case _          => base.trim

  // ── new sources reaching an article ────────────────────────────────────────────────────────────

  /** Why an article is extended now, if it is: its filter returns at least `min` sources it neither
    * cites nor gives a reason for, and it was not extended in the last `cooldownDays`. `min` 0 = never. */
  def extendDue(newSources: Int, min: Int, lastExtend: Option[Instant], cooldownDays: Int, now: Instant): Option[String] =
    if min <= 0 || newSources < min then None
    else if lastExtend.exists(t => t.plus(Duration.ofDays(math.max(0, cooldownDays).toLong)).isAfter(now)) then None
    else Some(s"its filter returns ${Plural.count(newSources, "source")} it neither cites nor gives a reason for")

  // ── caps, housekeeping, throughput ─────────────────────────────────────────────────────────────

  /** What is left today of the ingests all watchers share (None: no shared cap). */
  def sharedIngestLeft(cap: Int, ingestedToday: Int): Option[Int] =
    Option.when(cap > 0)(math.max(0, cap - math.max(0, ingestedToday)))

  /** Is a housekeeping job due: never run, or `everyHours` passed since (0 = never). */
  def due(last: Option[Instant], everyHours: Int, now: Instant): Boolean =
    everyHours > 0 && last.forall(l => !l.plus(Duration.ofHours(everyHours.toLong)).isAfter(now))

  /** Days until `open` items are done at the pace of the last week (`doneLast7` items); None when
    * nothing is open or nothing was done. */
  def etaDays(open: Int, doneLast7: Int): Option[Int] =
    Option.when(open > 0 && doneLast7 > 0)(math.max(1, math.ceil(open * 7.0 / doneLast7).toInt))
