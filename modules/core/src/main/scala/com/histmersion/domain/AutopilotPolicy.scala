package com.histmersion.domain

import com.histmersion.api.Plural

import com.histmersion.api.Managers.*

import java.time.{Duration, Instant}
import com.histmersion.values.{ApprovalDecidedBy, GoalStatus, ItemStatus, ManagerKind, ManagerState}

/** The pure half of the autopilot (docs/research-process-managers.md "As built: the autopilot"): the code checks that
  * decide an approval without a person, the rule that moves a research goal's status on from the facts, the managers
  * every goal must have, the spread of their crons, when an article is extended with new sources, and when the
  * housekeeping is due. No I/O: the supervisor reads the facts and applies the result.
  */
object AutopilotPolicy:

  // ── auto approvals ─────────────────────────────────────────────────────────────────────────────

  /** What the checks say about one open approval: approve it (why, for the note), reject it (why), or leave it for a
    * person (or for tomorrow, when only a daily cap stood in the way).
    */
  enum Verdict:
    case Approve(reason: String)
    case Reject(reason: String)
    case Leave(why: String)

  /** One article of an `article-plan`, as it stands NOW: its slug still free, its filter's hits. */
  final case class PlannedFacts(slug: String, slugFree: Boolean, hits: Int)

  /** An `article-plan` (propose-articles): approved when every article's slug is still free and its filter returns at
    * least `minSources`, and the area's planned articles approved today stay within `cap`; a plan larger than the cap
    * is a person's.
    */
  def articlePlan(articles: List[PlannedFacts], minSources: Int, approvedToday: Int, cap: Int): Verdict =
    val n = articles.size
    val bad = articles.flatMap { a =>
      if !a.slugFree then Some(s"${a.slug}: the slug is taken now")
      else if a.hits < minSources then
        Some(s"${a.slug}: its filter returns ${a.hits} source(s), fewer than $minSources")
      else None
    }
    if n == 0 then Verdict.Reject("the plan names no article")
    else if bad.nonEmpty then Verdict.Reject(s"checks failed — ${bad.mkString("; ")}")
    else if n > cap then
      Verdict.Leave(s"${Plural.count(n, "article")} exceed the daily cap of $cap for one research area")
    else if approvedToday + n > cap then
      Verdict.Leave(
        s"the area's daily cap is reached ($approvedToday of $cap articles approved today); decided tomorrow"
      )
    else
      Verdict.Approve(
        s"${Plural.count(n, "article")}, every slug free and every filter at least $minSources sources " +
          s"(${articles.map(a => s"${a.slug} ${a.hits}").mkString(", ")}); ${approvedToday + n} of $cap today"
      )

  /** A draft to publish (compile-article), as code reads it. `hits` = what the run's filter returned (None: not known).
    */
  final case class DraftFacts(
      slug: String,
      create: Boolean,
      filterRecorded: Boolean,
      hits: Option[Int],
      problems: List[String],
      cited: Int,
      slugFree: Boolean
  )

  /** An `article-publish`: approved when the run recorded a filter that returns at least `minSources` sources, the code
    * self-check left no problem, the text cites a source and (a new article) the slug is still free — within `cap`
    * drafts a day. A failed check rejects it (the seeding manager compiles again); a reached cap leaves it for
    * tomorrow.
    */
  def articlePublish(d: DraftFacts, minSources: Int, approvedToday: Int, cap: Int): Verdict =
    if !d.filterRecorded then Verdict.Reject("the run records no coverage filter")
    else if d.hits.forall(_ < minSources) then
      Verdict.Reject(
        s"its filter returns ${d.hits.fold("an unknown number of")(_.toString)} source(s), fewer than $minSources"
      )
    else if d.problems.nonEmpty then
      Verdict.Reject(
        s"the self-check left ${Plural.count(d.problems.size, "problem")}: ${d.problems.take(3).mkString("; ").take(600)}"
      )
    else if d.cited == 0 then Verdict.Reject("the text cites no source")
    else if d.create && !d.slugFree then Verdict.Reject(s"the slug ${d.slug} is taken now")
    else if approvedToday >= cap then Verdict.Leave(s"the daily cap of $cap drafts is reached; decided tomorrow")
    else
      Verdict.Approve(
        s"filter ${d.hits.getOrElse(0)} sources (at least $minSources), ${Plural.count(d.cited, "source")} cited, " +
          s"no self-check problem; ${approvedToday + 1} of $cap today"
      )

  /** A goal's plan (plan-goal), as code reads it; `depth` = how far below a root goal it is (0 = a root). */
  final case class GoalPlanFacts(goal: String, subGoals: Int, queries: Int, works: Int, depth: Int)

  /** A `goal-plan`: approved when it proposes something, and sub-goals only for a goal less than `maxDepth` deep —
    * within `cap` plans a day.
    */
  def goalPlan(p: GoalPlanFacts, maxDepth: Int, approvedToday: Int, cap: Int): Verdict =
    if p.subGoals + p.queries + p.works == 0 then Verdict.Reject("the plan proposes nothing")
    else if p.subGoals > 0 && p.depth >= maxDepth then
      Verdict.Reject(
        s"it proposes ${Plural.count(p.subGoals, "sub-goal")} for a goal ${depthText(p.depth)}; " +
          s"sub-goals stop at depth $maxDepth (the deepest sub-goal the checks approve)"
      )
    else if approvedToday >= cap then Verdict.Leave(s"the daily cap of $cap goal plans is reached; decided tomorrow")
    else
      Verdict.Approve(
        s"${Plural.count(p.subGoals, "sub-goal")}, ${Plural.count(p.queries, "query", "queries")}, ${Plural.count(p.works, "work")}" +
          s" (depth ${p.depth} of $maxDepth); ${approvedToday + 1} of $cap today"
      )

  /** A goal plan while the focus is on (docs/research-process-managers.md "As built: the focus"): decided by code only
    * when its goal is one of the active goals and its unfinished sub-goals (`openSubGoals`, every level below it) plus
    * the `proposed` new ones stay within `limit`; else why it waits for a person.
    */
  def goalPlanFocus(goal: String, active: Boolean, openSubGoals: Int, proposed: Int, limit: Int): Option[String] =
    if !active then Some(s"focus: goal $goal is not one of the active goals; its plan waits for a person or its turn")
    else if proposed > 0 && openSubGoals + proposed > math.max(0, limit) then
      Some(
        s"focus: goal $goal has ${Plural.count(openSubGoals, "unfinished sub-goal")} and the plan adds $proposed; at most " +
          s"$limit are opened by code (the autopilot's limit on open sub-goals)"
      )
    else None

  /** A goal plan decided by code whatever the focus (#3762 proposal 2): only its sub-goals are bounded — its unfinished
    * sub-goals (`openSubGoals`, every level below it) plus the `proposed` new ones stay within `limit`
    * (`agents.autopilot.max-open-sub-goals`); else why it waits for a person. Queries and works alone are always within
    * it.
    */
  def goalPlanSubGoals(goal: String, openSubGoals: Int, proposed: Int, limit: Int): Option[String] =
    Option.when(proposed > 0 && openSubGoals + proposed > math.max(0, limit))(
      s"goal $goal has ${Plural.count(openSubGoals, "unfinished sub-goal")} and the plan adds $proposed; at most " +
        s"$limit are opened by code (the autopilot's limit on open sub-goals)"
    )

  /** The goals with an approved plan that are not finished yet (neither reviewed nor done): what a new goal plan would
    * add to. `goals` = every goal's slug and status.
    */
  def unfinishedPlanned(goals: List[(String, String)], planned: Set[String]): Int =
    goals.count((slug, status) => planned(slug) && !finished(status))

  /** Why goal plans are not offered now, if they are not: more goals with an approved plan are unfinished than `limit`
    * (0 = no limit). While so, no plan-goal run starts for an unplanned goal and code decides no open goal plan; a
    * person still may.
    */
  def goalPlansWait(unfinished: Int, limit: Int): Option[String] =
    Option.when(limit > 0 && unfinished > limit)(
      s"goal plans wait until the unfinished goals drop to $limit: ${Plural.count(unfinished, "goal")} with an approved " +
        s"plan ${if unfinished == 1 then "is" else "are"} not reviewed yet (the autopilot's limit on unfinished goals)"
    )

  /** A daily cap scaled to the backlog (#3762 proposal 3): `share` ≤ 0 keeps `base`; else
    * `max(base, ⌈backlog × share⌉)`, never above `ceiling` (when it is above 0) — and never below 0.
    */
  def scaledCap(base: Int, backlog: Int, share: Double, ceiling: Int): Int =
    val b = math.max(0, base)
    if share <= 0 then b
    else
      val scaled = math.max(b, math.ceil(math.max(0, backlog) * share).toInt)
      if ceiling > 0 then math.min(scaled, math.max(b, ceiling)) else scaled

  /** The cap as the status shows it: `5 a day`, or `5 a day, 22 today for a backlog of 64 (at most 50)`. */
  def capText(base: Int, backlog: Int, share: Double, ceiling: Int): String =
    val n = scaledCap(base, backlog, share, ceiling)
    if share <= 0 then s"$base a day"
    else s"$base a day, $n today for a backlog of $backlog" + (if ceiling > 0 then s" (at most $ceiling)" else "")

  // ── the next article (#3763) ───────────────────────────────────────────────────────────────────

  /** What decides whether a goal's next article is proposed: its status, its linked articles and how many of them have
    * not passed review, its approved articles not compiled yet, its seeding manager's state, and its proposal item's
    * status and when it settled.
    */
  final case class NextFacts(
      goal: String,
      status: String,
      articles: Int,
      unreviewed: Int,
      compileOpen: Int,
      seedState: Option[String],
      proposeStatus: Option[String],
      proposeSettled: Option[java.time.Instant]
  )

  /** Why a goal's articles are still in progress — what keeps a new proposal back (#3763: no new work while the goal
    * still has unfinished articles); None when they are all done (or it has none).
    */
  def articlesInProgress(unreviewed: Int, compileOpen: Int): Option[String] =
    Option.when(unreviewed > 0 || compileOpen > 0)(
      "its articles in progress come first (" + List(
        Option.when(compileOpen > 0)(s"$compileOpen to compile"),
        Option.when(unreviewed > 0)(s"$unreviewed to pass review")
      ).flatten.mkString(", ") +
        "): a new proposal waits until they are published and reviewed"
    )

  /** Whether to propose a goal's NEXT article now (#3763), and why: the goal has articles, every one of them compiled
    * and past its review, its seeding manager is active, its proposal item is not open, and that proposal settled at
    * least `cooldownHours` ago (0 = never). A person's goal status `done` ends it. The proposal then goes through the
    * approvals policy like any other (`article-plan`).
    */
  def nextArticle(f: NextFacts, now: java.time.Instant, cooldownHours: Int): Option[String] =
    val open = f.proposeStatus.exists(st => ItemStatus.parse(st).exists(!_.isFinal))
    val cooled = f.proposeSettled.forall(t => !t.plusSeconds(math.max(0, cooldownHours).toLong * 3600L).isAfter(now))
    Option.when(
      cooldownHours > 0 && f.status != GoalStatus.Done && f.articles > 0 &&
        articlesInProgress(f.unreviewed, f.compileOpen).isEmpty && f.seedState.contains(
          ManagerState.Active.wire
        ) && !open && cooled
    )(
      s"its ${Plural.count(f.articles, "article")} are compiled and passed review: its next article is proposed " +
        "(the plan goes through the article-plan approvals)"
    )

  private def depthText(d: Int): String = if d == 0 then "at the root" else s"$d level(s) below a root"

  /** The note an automatic decision carries — what marks it as the autopilot's (and what the daily caps count).
    */
  val NotePrefix: String = ApprovalDecidedBy.AutopilotNote
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

  /** What a goal's status is moved on from. `managed` = it has an active seeding manager or watcher; `articles` = the
    * articles linked to it that exist; `compileOpen` = its seeding manager still has an article to compile (or a plan
    * or a proposal waiting for a decision); `reviewed` = every linked article has a review ledger with no open MAJOR
    * and no review item open; `children` = the statuses of its sub-goals.
    */
  final case class GoalFacts(
      slug: String,
      status: String,
      managed: Boolean,
      articles: Int,
      compileOpen: Boolean,
      reviewed: Boolean,
      children: List[String]
  )

  private def rank(s: String): Int = GoalStatus.all.indexOf(s)

  /** The status the facts support, when it is FURTHER than the goal's own (never back; `done` and an unknown status are
    * a person's): seeding once it is managed; compiled once it has an article (a parent: once its sub-goals are
    * compiled) and nothing is left to compile; reviewed once every article passed its review and every sub-goal is
    * reviewed. With why, for the log.
    */
  def advance(f: GoalFacts): Option[(String, String)] =
    val cur = rank(f.status)
    if cur < 0 || f.status == GoalStatus.Done then None
    else
      val kids = f.children.map(rank)
      val hasOwn = f.articles > 0
      val compiled = (hasOwn || kids.nonEmpty) && !f.compileOpen && kids.forall(_ >= rank(GoalStatus.Compiled))
      val reviewed = compiled && (!hasOwn || f.reviewed) && kids.forall(_ >= rank(GoalStatus.Reviewed))
      val (target, why) =
        if reviewed then
          (
            GoalStatus.Reviewed,
            (if hasOwn then s"its ${Plural.count(f.articles, "article")} passed review" else "") +
              (if kids.nonEmpty then
                 s"${if hasOwn then ", " else ""}its ${Plural.count(kids.size, "sub-goal")} are reviewed"
               else "")
          )
        else if compiled then
          (
            GoalStatus.Compiled,
            (if hasOwn then s"${Plural.count(f.articles, "article")} compiled, none left to compile"
             else "nothing left to compile") +
              (if kids.nonEmpty then s", its ${Plural.count(kids.size, "sub-goal")} are compiled" else "")
          )
        else if f.managed then (GoalStatus.Seeding, "its seeding manager and watcher are active")
        else (f.status, "")
      Option.when(rank(target) > cur)((target, why))

  // ── the managers every goal has ────────────────────────────────────────────────────────────────

  /** A goal as the autopilot sees it: its status, and whether it still needs a plan (it records none and links no
    * query).
    */
  final case class GoalRow(slug: String, status: String, needsPlan: Boolean)

  /** A manager as the autopilot sees it. `planItem` = it has a plan-goal item (any status). */
  final case class ManagerRow(
      id: String,
      kind: String,
      goal: Option[String],
      state: String,
      cron: Option[String],
      autoIngest: Option[Boolean],
      planItem: Boolean = false,
      extendMinNew: Option[Int] = None
  )

  /** The settings `ensure` works with. */
  final case class Settings(
      watchCron: String,
      seedCron: String,
      finishedWatchCron: String,
      watchIngestPerDay: Int,
      extendMinNew: Int
  )

  /** One change `ensure` asks for. */
  enum Step:
    /** Create the goal's seeding manager, active, with its cron (and a plan-goal item before its first proposal when
      * the goal has no plan).
      */
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

  /** Finished: reviewed or done — such a goal keeps a watcher (new sources, the currency check), no new seeding
    * manager.
    */
  def finished(status: String): Boolean = status == GoalStatus.Reviewed || status == GoalStatus.Done

  /** What must change so every goal has its managers (idempotent: nothing, when they are there). Library-wide: `review`
    * and `enrich` active (`review` with the extend threshold). Every unfinished goal: a seeding manager (a plan-goal
    * item first when it has no plan) and a watcher with auto-ingest on; a finished goal: a watcher. A manager a person
    * paused stays paused — only the ones in `pausedByAutopilot` are resumed. A manager's settings a person chose are
    * kept (only an unset cron, auto-ingest or extend threshold is filled).
    */
  def ensure(
      goals: List[GoalRow],
      managers: List[ManagerRow],
      pausedByAutopilot: Set[String],
      s: Settings
  ): List[Step] =
    val byId = managers.map(m => m.id -> m).toMap
    val library = List(ReviewId, EnrichId).flatMap { id =>
      byId.get(id).toList.flatMap { m =>
        Option.when(m.state == ManagerState.Paused.wire)(Step.Resume(id)).toList ++
          Option
            .when(id == ReviewId && m.extendMinNew.isEmpty && s.extendMinNew > 0)(Step.ExtendMinNew(id, s.extendMinNew))
            .toList
      }
    }
    val perGoal = goals.sortBy(_.slug).flatMap { g =>
      val done = finished(g.status)
      val seed = byId.get(seedId(g.slug)) match
        case None if done => Nil
        case None         => List(Step.CreateSeed(g.slug, stagger(s.seedCron, seedId(g.slug)), g.needsPlan))
        case Some(m)      =>
          Option.when(pausedByAutopilot(m.id) && m.state == ManagerState.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(!done && m.cron.isEmpty)(Step.SetCron(m.id, stagger(s.seedCron, m.id))).toList ++
            Option
              .when(!done && g.needsPlan && !m.planItem && m.state != ManagerState.Stopped.wire)(
                Step.AddPlanItem(m.id, g.slug)
              )
              .toList
      val watchCron = if done then s.finishedWatchCron else s.watchCron
      val watch = byId.get(watchId(g.slug)) match
        case None    => List(Step.CreateWatch(g.slug, stagger(watchCron, watchId(g.slug)), s.watchIngestPerDay))
        case Some(m) =>
          Option.when(pausedByAutopilot(m.id) && m.state == ManagerState.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(m.cron.isEmpty)(Step.SetCron(m.id, stagger(watchCron, m.id))).toList ++
            Option.when(m.autoIngest.isEmpty)(Step.AutoIngestOn(m.id, s.watchIngestPerDay)).toList
      seed ++ watch
    }
    library ++ perGoal

  /** The managers the autopilot pauses when it is turned off: the active seeding managers and watchers (the
    * library-wide ones and a person's paused ones are left as they are).
    */
  def toPause(managers: List[ManagerRow]): List[String] =
    managers
      .filter(m =>
        m.state == ManagerState.Active.wire && (m.kind == ManagerKind.Seed.wire || m.kind == ManagerKind.Watch.wire)
      )
      .map(_.id)

  // ── crons spread per goal ──────────────────────────────────────────────────────────────────────

  /** A stable non-negative hash of `key` (the same on every machine and every run). */
  def spread(key: String): Int = key.foldLeft(17)((h, c) => 31 * h + c) & Int.MaxValue

  /** `@daily`, `@weekly` and `@monthly` as five fields at a minute, hour (and weekday or day of the month) of `key`'s
    * own, so dozens of managers with the same cron do not fire in the same minute; any other cron is kept as given.
    */
  def stagger(base: String, key: String): String =
    val h = spread(key)
    val (min, hour, day) = (h % 60, (h / 60) % 24, h / 1440)
    base.trim.toLowerCase match
      case "@daily"   => s"$min $hour * * *"
      case "@weekly"  => s"$min $hour * * ${day % 7}"
      case "@monthly" => s"$min $hour ${1 + day % 28} * *"
      case _          => base.trim

  // ── new sources reaching an article ────────────────────────────────────────────────────────────

  /** Why an article is extended now, if it is: its filter returns at least `min` sources it neither cites nor gives a
    * reason for, and it was not extended in the last `cooldownDays`. `min` 0 = never.
    */
  def extendDue(
      newSources: Int,
      min: Int,
      lastExtend: Option[Instant],
      cooldownDays: Int,
      now: Instant
  ): Option[String] =
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

  /** Days until `open` items are done at the pace of the last week (`doneLast7` items); None when nothing is open or
    * nothing was done.
    */
  def etaDays(open: Int, doneLast7: Int): Option[Int] =
    Option.when(open > 0 && doneLast7 > 0)(math.max(1, math.ceil(open * 7.0 / doneLast7).toInt))
