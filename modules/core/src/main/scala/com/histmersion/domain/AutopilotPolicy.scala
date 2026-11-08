package com.histmersion
package domain

import com.histmersion.api.Plural

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Manager
import io.github.iltotore.iron.constraint.any.Pure

/** The pure half of the autopilot (docs/research-process-managers.md "As built: the autopilot"): the code checks that
  * decide an approval without a person, the rule that moves a research goal's status on from the facts, the managers
  * every goal must have, the spread of their crons, when an article is extended with new sources, and when the
  * housekeeping is due. No I/O: the supervisor reads the facts and applies the result.
  */
object AutopilotPolicy:
  type ApprovedToday = ApprovedToday.T
  object ApprovedToday extends Newtype[Int, Pure]
  type Backlog = Backlog.T
  object Backlog extends Newtype[Int, Pure]
  type Base = Base.T
  object Base extends Newtype[Int, Pure]
  type Cap = Cap.T
  object Cap extends Newtype[Int, Pure]
  type CooldownDays = CooldownDays.T
  object CooldownDays extends Newtype[Int, Pure]
  type CooldownHours = CooldownHours.T
  object CooldownHours extends Newtype[Int, Pure]
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
  type Days = Days.T
  object Days extends Newtype[Int, Pure]
  type DepthLimit = DepthLimit.T
  object DepthLimit extends Newtype[Int, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type DoneLast7 = DoneLast7.T
  object DoneLast7 extends Newtype[Int, Pure]
  type EveryHours = EveryHours.T
  object EveryHours extends Newtype[Int, Pure]
  type IngestedToday = IngestedToday.T
  object IngestedToday extends Newtype[Int, Pure]
  type Left = Left.T
  object Left extends Newtype[Int, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type MinSources = MinSources.T
  object MinSources extends Newtype[Int, Pure]
  type NewSources = NewSources.T
  object NewSources extends Newtype[Int, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type OpenCount = OpenCount.T
  object OpenCount extends Newtype[Int, Pure]
  type Proposed = Proposed.T
  object Proposed extends Newtype[Int, Pure]
  type Share = Share.T
  object Share extends Newtype[Double, Pure]
  type Unfinished = Unfinished.T
  object Unfinished extends Newtype[Int, Pure]

  private def counted(n: Int, singular: String, plural: String = ""): Plural.Phrase =
    Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(singular), Plural.CountPlural(plural))

  // ── auto approvals ─────────────────────────────────────────────────────────────────────────────

  /** What the checks say about one open approval: approve it (why, for the note), reject it (why), or leave it for a
    * person (or for tomorrow, when only a daily cap stood in the way).
    */

  type CapTextBase = Base
  val CapTextBase: Base.type = Base
  type CapTextBacklog = Backlog
  val CapTextBacklog: Backlog.type = Backlog
  type CapTextShare = Share
  val CapTextShare: Share.type = Share
  type CapTextCeiling = Cap
  val CapTextCeiling: Cap.type = Cap
  type NotePrefix = NotePrefixValue
  type NotePrefixValue = NotePrefixValue.T
  object NotePrefixValue extends Newtype[String, Pure]

  enum Verdict derives CanEqual:
    case Approve(reason: Problem)
    case Reject(reason: Problem)
    case Leave(why: Problem)


  /** One article of an `article-plan`, as it stands NOW: its slug still free, its filter's hits. */
  final case class PlannedFacts(slug: PlannedFacts.Slug, slugFree: PlannedFacts.SlugFree, hits: PlannedFacts.Hits)

  object PlannedFacts:
    type Slug = Slug.T
    object Slug extends Newtype[String, Pure]
    type SlugFree = SlugFree.T
    object SlugFree extends Newtype[Boolean, Pure]
    type Hits = Hits.T
    object Hits extends Newtype[Int, Pure]

  /** An `article-plan` (propose-articles): approved when every article's slug is still free and its filter returns at
    * least `minSources`, and the area's planned articles approved today stay within `cap`; a plan larger than the cap
    * is a person's.
    */
  def articlePlan(
      articles: List[PlannedFacts],
      minSources: MinSources,
      approvedToday: ApprovedToday,
      cap: Cap
  ): Verdict =
    val n = articles.size
    val bad = articles.flatMap { a =>
      if !a.slugFree.value then Some(s"${a.slug}: the slug is taken now")
      else if a.hits.value < minSources.value then
        Some(s"${a.slug}: its filter returns ${a.hits} source(s), fewer than $minSources")
      else None
    }
    if n == 0 then Verdict.Reject(Problem("the plan names no article"))
    else if bad.nonEmpty then Verdict.Reject(Problem(s"checks failed — ${bad.mkString("; ")}"))
    else if n > cap.value then
      Verdict.Leave(Problem(s"${counted(n, "article")} exceed the daily cap of $cap for one research area"))
    else if approvedToday.value + n > cap.value then
      Verdict.Leave(
        Problem(s"the area's daily cap is reached ($approvedToday of $cap articles approved today); decided tomorrow")
      )
    else
      Verdict.Approve(
        Problem(
          s"${counted(n, "article")}, every slug free and every filter at least $minSources sources " +
            s"(${articles.map(a => s"${a.slug} ${a.hits}").mkString(", ")}); ${approvedToday.value + n} of $cap today"
        )
      )

  /** A draft to publish (compile-article), as code reads it. `hits` = what the run's filter returned (None: not known).
    */
  final case class DraftFacts(
      slug: DraftFacts.Slug,
      create: DraftFacts.Create,
      filterRecorded: DraftFacts.FilterRecorded,
      hits: Option[DraftFacts.Hits],
      problems: List[DraftFacts.Problems],
      cited: DraftFacts.Cited,
      slugFree: DraftFacts.SlugFree
  )

  object DraftFacts:
    type Slug = Slug.T
    object Slug extends Newtype[String, Pure]
    type Create = Create.T
    object Create extends Newtype[Boolean, Pure]
    type FilterRecorded = FilterRecorded.T
    object FilterRecorded extends Newtype[Boolean, Pure]
    type Hits = Hits.T
    object Hits extends Newtype[Int, Pure]
    type Problems = Problems.T
    object Problems extends Newtype[String, Pure]
    type Cited = Cited.T
    object Cited extends Newtype[Int, Pure]
    type SlugFree = SlugFree.T
    object SlugFree extends Newtype[Boolean, Pure]

  /** An `article-publish`: approved when the run recorded a filter that returns at least `minSources` sources, the code
    * self-check left no problem, the text cites a source and (a new article) the slug is still free — within `cap`
    * drafts a day. A failed check rejects it (the seeding manager compiles again); a reached cap leaves it for
    * tomorrow.
    */
  def articlePublish(d: DraftFacts, minSources: MinSources, approvedToday: ApprovedToday, cap: Cap): Verdict =
    if !d.filterRecorded.value then Verdict.Reject(Problem("the run records no coverage filter"))
    else if d.hits.forall(_.value < minSources.value) then
      Verdict.Reject(
        Problem(
          s"its filter returns ${d.hits.fold("an unknown number of")(_.toString)} source(s), fewer than $minSources"
        )
      )
    else if d.problems.nonEmpty then
      Verdict.Reject(
        Problem(
          s"the self-check left ${counted(d.problems.size, "problem")}: ${d.problems.take(3).mkString("; ").take(600)}"
        )
      )
    else if d.cited.value == 0 then Verdict.Reject(Problem("the text cites no source"))
    else if d.create.value && !d.slugFree.value then Verdict.Reject(Problem(s"the slug ${d.slug} is taken now"))
    else if approvedToday.value >= cap.value then
      Verdict.Leave(Problem(s"the daily cap of $cap drafts is reached; decided tomorrow"))
    else
      Verdict.Approve(
        Problem(
          s"filter ${d.hits.getOrElse(0)} sources (at least $minSources), ${counted(d.cited.value, "source")} cited, " +
            s"no self-check problem; ${approvedToday.value + 1} of $cap today"
        )
      )

  /** A goal's plan (plan-goal), as code reads it; `depth` = how far below a root goal it is (0 = a root). */
  final case class GoalPlanFacts(
      goal: GoalPlanFacts.Goal,
      subGoals: GoalPlanFacts.SubGoals,
      queries: GoalPlanFacts.Queries,
      works: GoalPlanFacts.Works,
      depth: GoalPlanFacts.Depth
  )

  object GoalPlanFacts:
    type Goal = Goal.T
    object Goal extends Newtype[String, Pure]
    type SubGoals = SubGoals.T
    object SubGoals extends Newtype[Int, Pure]
    type Queries = Queries.T
    object Queries extends Newtype[Int, Pure]
    type Works = Works.T
    object Works extends Newtype[Int, Pure]
    type Depth = Depth.T
    object Depth extends Newtype[Int, Pure]

  /** A `goal-plan`: approved when it proposes something, and sub-goals only for a goal less than `maxDepth` deep —
    * within `cap` plans a day.
    */
  def goalPlan(p: GoalPlanFacts, maxDepth: DepthLimit, approvedToday: ApprovedToday, cap: Cap): Verdict =
    if p.subGoals.value + p.queries.value + p.works.value == 0 then
      Verdict.Reject(Problem("the plan proposes nothing"))
    else if p.subGoals.value > 0 && p.depth.value >= maxDepth.value then
      Verdict.Reject(
        Problem(
          s"it proposes ${counted(p.subGoals.value, "sub-goal")} for a goal ${depthText(p.depth)}; " +
            s"sub-goals stop at depth $maxDepth (the deepest sub-goal the checks approve)"
        )
      )
    else if approvedToday.value >= cap.value then
      Verdict.Leave(Problem(s"the daily cap of $cap goal plans is reached; decided tomorrow"))
    else
      Verdict.Approve(
        Problem(
          s"${counted(p.subGoals.value, "sub-goal")}, ${counted(p.queries.value, "query", "queries")}, ${counted(p.works.value, "work")}" +
            s" (depth ${p.depth} of $maxDepth); ${approvedToday.value + 1} of $cap today"
        )
      )

  /** A goal plan while the focus is on (docs/research-process-managers.md "As built: the focus"): decided by code only
    * when its goal is one of the active goals and its unfinished sub-goals (`openSubGoals`, every level below it) plus
    * the `proposed` new ones stay within `limit`; else why it waits for a person.
    */
  def goalPlanFocus(
      goal: Goal.Slug,
      active: Boolean,
      openSubGoals: OpenCount,
      proposed: Proposed,
      limit: Limit
  ): Option[Description] =
    if !active then
      Some(Description(s"focus: goal $goal is not one of the active goals; its plan waits for a person or its turn"))
    else if proposed.value > 0 && openSubGoals.value + proposed.value > math.max(0, limit.value) then
      Some(
        Description(
          s"focus: goal $goal has ${counted(openSubGoals.value, "unfinished sub-goal")} and the plan adds $proposed; at most " +
            s"$limit are opened by code (the autopilot's limit on open sub-goals)"
        )
      )
    else None

  /** A goal plan decided by code whatever the focus (#3762 proposal 2): only its sub-goals are bounded — its unfinished
    * sub-goals (`openSubGoals`, every level below it) plus the `proposed` new ones stay within `limit`
    * (`agents.autopilot.max-open-sub-goals`); else why it waits for a person. Queries and works alone are always within
    * it.
    */
  def goalPlanSubGoals(
      goal: Goal.Slug,
      openSubGoals: OpenCount,
      proposed: Proposed,
      limit: Limit
  ): Option[Description] =
    Option.when(proposed.value > 0 && openSubGoals.value + proposed.value > math.max(0, limit.value))(
      Description(
        s"goal $goal has ${counted(openSubGoals.value, "unfinished sub-goal")} and the plan adds $proposed; at most " +
          s"$limit are opened by code (the autopilot's limit on open sub-goals)"
      )
    )

  /** The goals with an approved plan that are not finished yet (neither reviewed nor done): what a new goal plan would
    * add to. `goals` = every goal's slug and status.
    */
  def unfinishedPlanned(goals: List[(Goal.Slug, Goal.Status.Wire)], planned: Set[Goal.Slug]): Unfinished =
    Unfinished(goals.count((slug, status) => planned(slug) && !finished(status)))

  /** Why goal plans are not offered now, if they are not: more goals with an approved plan are unfinished than `limit`
    * (0 = no limit). While so, no plan-goal run starts for an unplanned goal and code decides no open goal plan; a
    * person still may.
    */
  def goalPlansWait(unfinished: Unfinished, limit: Limit): Option[Description] =
    Option.when(limit.value > 0 && unfinished.value > limit.value)(
      Description(
        s"goal plans wait until the unfinished goals drop to $limit: ${counted(unfinished.value, "goal")} with an approved " +
          s"plan ${
              if unfinished.value == 1 then "is" else "are"
            } not reviewed yet (the autopilot's limit on unfinished goals)"
      )
    )

  /** A daily cap scaled to the backlog (#3762 proposal 3): `share` ≤ 0 keeps `base`; else
    * `max(base, ⌈backlog × share⌉)`, never above `ceiling` (when it is above 0) — and never below 0.
    */
  def scaledCap(base: Base, backlog: Backlog, share: Share, ceiling: Cap): Cap =
    val b = math.max(0, base.value)
    if share.value <= 0 then Cap(b)
    else
      val scaled = math.max(b, math.ceil(math.max(0, backlog.value) * share.value).toInt)
      Cap(if ceiling.value > 0 then math.min(scaled, math.max(b, ceiling.value)) else scaled)

  /** The cap as the status shows it: `5 a day`, or `5 a day, 22 today for a backlog of 64 (at most 50)`. */
  def capText(base: CapTextBase, backlog: CapTextBacklog, share: CapTextShare, ceiling: CapTextCeiling): Description =
    val n = scaledCap(base, backlog, share, ceiling)
    Description(
      if share.value <= 0 then s"$base a day"
      else
        s"$base a day, $n today for a backlog of $backlog" + (if ceiling.value > 0 then s" (at most $ceiling)" else "")
    )

  // ── the next article (#3763) ───────────────────────────────────────────────────────────────────

  /** What decides whether a goal's next article is proposed: its status, its linked articles and how many of them have
    * not passed review, its approved articles not compiled yet, its seeding manager's state, and its proposal item's
    * status and when it settled.
    */
  final case class NextFacts(
      goal: NextFacts.Goal,
      status: Goal.Status.Wire,
      articles: NextFacts.Articles,
      unreviewed: NextFacts.Unreviewed,
      compileOpen: NextFacts.CompileOpen,
      seedState: Option[Manager.State.Wire],
      proposeStatus: Option[ItemStatus.Wire],
      proposeSettled: Option[java.time.Instant]
  )

  object NextFacts:
    type Goal = Goal.T
    object Goal extends Newtype[String, Pure]
    type Articles = Articles.T
    object Articles extends Newtype[Int, Pure]
    type Unreviewed = Unreviewed.T
    object Unreviewed extends Newtype[Int, Pure]
    type CompileOpen = CompileOpen.T
    object CompileOpen extends Newtype[Int, Pure]

  /** Why a goal's articles are still in progress — what keeps a new proposal back (#3763: no new work while the goal
    * still has unfinished articles); None when they are all done (or it has none).
    */
  def articlesInProgress(unreviewed: NextFacts.Unreviewed, compileOpen: NextFacts.CompileOpen): Option[Description] =
    Option.when(unreviewed.value > 0 || compileOpen.value > 0)(
      Description(
        "its articles in progress come first (" + List(
          Option.when(compileOpen.value > 0)(s"$compileOpen to compile"),
          Option.when(unreviewed.value > 0)(s"$unreviewed to pass review")
        ).flatten.mkString(", ") +
          "): a new proposal waits until they are published and reviewed"
      )
    )

  /** Whether to propose a goal's NEXT article now (#3763), and why: the goal has articles, every one of them compiled
    * and past its review, its seeding manager is active, its proposal item is not open, and that proposal settled at
    * least `cooldownHours` ago (0 = never). A person's goal status `done` ends it. The proposal then goes through the
    * approvals policy like any other (`article-plan`).
    */
  def nextArticle(f: NextFacts, now: java.time.Instant, cooldownHours: CooldownHours): Option[Description] =
    val open = f.proposeStatus.exists(st => ItemStatus.parse(st).exists(!_.isFinal))
    val cooled =
      f.proposeSettled.forall(t => !t.plusSeconds(math.max(0, cooldownHours.value).toLong * 3600L).isAfter(now))
    Option.when(
      cooldownHours.value > 0 && f.status.value != Goal.Status.Done && f.articles.value > 0 &&
        articlesInProgress(f.unreviewed, f.compileOpen).isEmpty && f.seedState.contains(
          Manager.State.Active.wire
        ) && !open && cooled
    )(
      Description(
        s"its ${counted(f.articles.value, "article")} are compiled and passed review: its next article is proposed " +
          "(the plan goes through the article-plan approvals)"
      )
    )

  private def depthText(d: GoalPlanFacts.Depth): String =
    if d.value == 0 then "at the root" else s"$d level(s) below a root"

  /** The note an automatic decision carries — what marks it as the autopilot's (and what the daily caps count).
    */
  val NotePrefix: NotePrefix = NotePrefixValue(Approval.DecidedBy.AutopilotNote)
  def note(v: Verdict): Option[Note] = v match
    case Verdict.Approve(r) => Some(Note(s"$NotePrefix approved — $r"))
    case Verdict.Reject(r)  => Some(Note(s"$NotePrefix rejected — $r"))
    case Verdict.Leave(_)   => None

  /** How deep a goal is: the number of parents above it (a cycle or a missing parent stops the walk). */
  def depth(
      slug: com.histmersion.domain.research.Slug,
      parentOf: com.histmersion.domain.research.Slug => Option[com.histmersion.domain.research.Slug]
  ): GoalPlanFacts.Depth =
    @annotation.tailrec
    def go(
        s: com.histmersion.domain.research.Slug,
        seen: Set[com.histmersion.domain.research.Slug],
        n: Int
    ): GoalPlanFacts.Depth = parentOf(s) match
      case Some(p) if !seen(p) && n < 64 => go(p, seen + p, n + 1)
      case _                             => GoalPlanFacts.Depth(n)
    go(slug, Set(slug), 0)

  // ── goal status ────────────────────────────────────────────────────────────────────────────────

  /** What a goal's status is moved on from. `managed` = it has an active seeding manager or watcher; `articles` = the
    * articles linked to it that exist; `compileOpen` = its seeding manager still has an article to compile (or a plan
    * or a proposal waiting for a decision); `reviewed` = every linked article has a review ledger with no open MAJOR
    * and no review item open; `children` = the statuses of its sub-goals.
    */
  final case class GoalFacts(
      slug: GoalFacts.Slug,
      status: Goal.Status.Wire,
      managed: GoalFacts.Managed,
      articles: GoalFacts.Articles,
      compileOpen: GoalFacts.CompileOpen,
      reviewed: GoalFacts.Reviewed,
      children: List[Goal.Status.Wire]
  )

  object GoalFacts:
    type Slug = Slug.T
    object Slug extends Newtype[String, Pure]
    type Managed = Managed.T
    object Managed extends Newtype[Boolean, Pure]
    type Articles = Articles.T
    object Articles extends Newtype[Int, Pure]
    type CompileOpen = CompileOpen.T
    object CompileOpen extends Newtype[Boolean, Pure]
    type Reviewed = Reviewed.T
    object Reviewed extends Newtype[Boolean, Pure]

  private def rank(s: Goal.Status.Wire): Int = Goal.Status.all.indexOf(s)

  /** The status the facts support, when it is FURTHER than the goal's own (never back; `done` and an unknown status are
    * a person's): seeding once it is managed; compiled once it has an article (a parent: once its sub-goals are
    * compiled) and nothing is left to compile; reviewed once every article passed its review and every sub-goal is
    * reviewed. With why, for the log.
    */
  def advance(f: GoalFacts): Option[(Goal.Status.Wire, Problem)] =
    val cur = rank(f.status)
    if cur < 0 || f.status.value == Goal.Status.Done then None
    else
      val kids = f.children.map(rank)
      val hasOwn = f.articles.value > 0
      val compiled = (hasOwn || kids.nonEmpty) && !f.compileOpen.value && kids.forall(
        _ >= rank(Goal.Status.Wire(Goal.Status.Compiled))
      )
      val reviewed =
        compiled && (!hasOwn || f.reviewed.value) && kids.forall(_ >= rank(Goal.Status.Wire(Goal.Status.Reviewed)))
      val (target, why) =
        if reviewed then
          (
            Goal.Status.Wire(Goal.Status.Reviewed),
            (if hasOwn then s"its ${counted(f.articles.value, "article")} passed review" else "") +
              (if kids.nonEmpty then s"${if hasOwn then ", " else ""}its ${counted(kids.size, "sub-goal")} are reviewed"
               else "")
          )
        else if compiled then
          (
            Goal.Status.Wire(Goal.Status.Compiled),
            (if hasOwn then s"${counted(f.articles.value, "article")} compiled, none left to compile"
             else "nothing left to compile") +
              (if kids.nonEmpty then s", its ${counted(kids.size, "sub-goal")} are compiled" else "")
          )
        else if f.managed.value then
          (Goal.Status.Wire(Goal.Status.Seeding), "its seeding manager and watcher are active")
        else (f.status, "")
      Option.when(rank(target) > cur)((target, Problem(why)))

  // ── the managers every goal has ────────────────────────────────────────────────────────────────

  /** A goal as the autopilot sees it: its status, and whether it still needs a plan (it records none and links no
    * query).
    */
  final case class GoalRow(slug: Goal.Slug, status: Goal.Status.Wire, needsPlan: GoalRow.NeedsPlan)

  object GoalRow:
    type NeedsPlan = NeedsPlan.T
    object NeedsPlan extends Newtype[Boolean, Pure]

  /** A manager as the autopilot sees it. `planItem` = it has a plan-goal item (any status). */
  final case class ManagerRow(
      id: ManagerRow.Id,
      kind: Manager.Kind.Wire,
      goal: Option[Goal.Slug],
      state: Manager.State.Wire,
      cron: Option[Cron],
      autoIngest: Option[ManagerRow.AutoIngest],
      planItem: ManagerRow.PlanItem = ManagerRow.PlanItem(false),
      extendMinNew: Option[Settings.ExtendMinNew] = None
  )

  object ManagerRow:
    type Id = Id.T
    object Id extends Newtype[String, Pure]:
      def seed(goal: Goal.Slug): Id = Id(s"seed:${goal.value}")
      def watch(goal: Goal.Slug): Id = Id(s"watch:${goal.value}")
    type AutoIngest = AutoIngest.T
    object AutoIngest extends Newtype[Boolean, Pure]
    type PlanItem = PlanItem.T
    object PlanItem extends Newtype[Boolean, Pure]

  /** The settings `ensure` works with. */
  final case class Settings(
      watchCron: Cron,
      seedCron: Cron,
      finishedWatchCron: Cron,
      watchIngestPerDay: Settings.WatchIngestPerDay,
      extendMinNew: Settings.ExtendMinNew
  )

  object Settings:
    type WatchIngestPerDay = WatchIngestPerDay.T
    object WatchIngestPerDay extends Newtype[Int, Pure]
    type ExtendMinNew = ExtendMinNew.T
    object ExtendMinNew extends Newtype[Int, Pure]

  /** One change `ensure` asks for. */
  enum Step derives CanEqual:
    /** Create the goal's seeding manager, active, with its cron (and a plan-goal item before its first proposal when
      * the goal has no plan).
      */
    case CreateSeed(goal: Step.CreateSeed.Goal, cron: Step.CreateSeed.Cron, planFirst: Step.CreateSeed.PlanFirst)

    /** Create the goal's watcher, active, auto-ingest on (at most `perDay` a day), with its cron. */
    case CreateWatch(goal: Step.CreateWatch.Goal, cron: Step.CreateWatch.Cron, perDay: Step.CreateWatch.PerDay)

    /** Resume a library-wide manager, or one the autopilot paused when it was turned off. */
    case Resume(id: Step.Resume.Id)

    /** Give a manager with no cron its spread cron. */
    case SetCron(id: Step.SetCron.Id, cron: Step.SetCron.Cron)

    /** Turn a watcher's auto-ingest on (it never said either way). */
    case AutoIngestOn(id: Step.AutoIngestOn.Id, perDay: Step.AutoIngestOn.PerDay)

    /** A plan-goal item first, for a goal that still needs a plan. */
    case AddPlanItem(id: Step.AddPlanItem.Id, goal: Step.AddPlanItem.Goal)

    /** The review manager's extend threshold (it sets none). */
    case ExtendMinNew(id: Step.ExtendMinNew.Id, n: Step.ExtendMinNew.N)

  object Step:
    object CreateSeed:
      export com.histmersion.domain.automation.Goal.Slug as Goal
      export AutopilotPolicy.Cron
      export GoalRow.NeedsPlan as PlanFirst
    object CreateWatch:
      export com.histmersion.domain.automation.Goal.Slug as Goal
      export AutopilotPolicy.Cron
      export Settings.WatchIngestPerDay as PerDay
    object Resume:
      export ManagerRow.Id
    object SetCron:
      export ManagerRow.Id
      export AutopilotPolicy.Cron
    object AutoIngestOn:
      export ManagerRow.Id
      export Settings.WatchIngestPerDay as PerDay
    object AddPlanItem:
      export ManagerRow.Id
      export com.histmersion.domain.automation.Goal.Slug as Goal
    object ExtendMinNew:
      export ManagerRow.Id
      export Settings.ExtendMinNew as N

  /** Finished: reviewed or done — such a goal keeps a watcher (new sources, the currency check), no new seeding
    * manager.
    */
  def finished(status: Goal.Status.Wire): Boolean =
    status.value == Goal.Status.Reviewed || status.value == Goal.Status.Done

  /** What must change so every goal has its managers (idempotent: nothing, when they are there). Library-wide: `review`
    * and `enrich` active (`review` with the extend threshold). Every unfinished goal: a seeding manager (a plan-goal
    * item first when it has no plan) and a watcher with auto-ingest on; a finished goal: a watcher. A manager a person
    * paused stays paused — only the ones in `pausedByAutopilot` are resumed. A manager's settings a person chose are
    * kept (only an unset cron, auto-ingest or extend threshold is filled).
    */
  def ensure(
      goals: List[GoalRow],
      managers: List[ManagerRow],
      pausedByAutopilot: Set[ManagerRow.Id],
      s: Settings
  ): List[Step] =
    val byId = managers.map(m => m.id -> m).toMap
    val library = List(ManagerRow.Id(ReviewId), ManagerRow.Id(EnrichId)).flatMap { id =>
      byId.get(id).toList.flatMap { m =>
        Option.when(m.state == Manager.State.Paused.wire)(Step.Resume(id)).toList ++
          Option
            .when(id.value == ReviewId && m.extendMinNew.isEmpty && s.extendMinNew.value > 0)(
              Step.ExtendMinNew(id, s.extendMinNew)
            )
            .toList
      }
    }
    val perGoal = goals.sortBy(_.slug.value).flatMap { g =>
      val done = finished(g.status)
      val seed = byId.get(ManagerRow.Id.seed(g.slug)) match
        case None if done => Nil
        case None         => List(Step.CreateSeed(g.slug, stagger(s.seedCron, ManagerRow.Id.seed(g.slug)), g.needsPlan))
        case Some(m)      =>
          Option.when(pausedByAutopilot(m.id) && m.state == Manager.State.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(!done && m.cron.isEmpty)(Step.SetCron(m.id, stagger(s.seedCron, m.id))).toList ++
            Option
              .when(!done && g.needsPlan.value && !m.planItem.value && m.state != Manager.State.Stopped.wire)(
                Step.AddPlanItem(m.id, g.slug)
              )
              .toList
      val watchCron = if done then s.finishedWatchCron else s.watchCron
      val watch = byId.get(ManagerRow.Id.watch(g.slug)) match
        case None =>
          List(Step.CreateWatch(g.slug, stagger(watchCron, ManagerRow.Id.watch(g.slug)), s.watchIngestPerDay))
        case Some(m) =>
          Option.when(pausedByAutopilot(m.id) && m.state == Manager.State.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(m.cron.isEmpty)(Step.SetCron(m.id, stagger(watchCron, m.id))).toList ++
            Option.when(m.autoIngest.isEmpty)(Step.AutoIngestOn(m.id, s.watchIngestPerDay)).toList
      seed ++ watch
    }
    library ++ perGoal

  /** The managers the autopilot pauses when it is turned off: the active seeding managers and watchers (the
    * library-wide ones and a person's paused ones are left as they are).
    */
  def toPause(managers: List[ManagerRow]): List[ManagerRow.Id] =
    managers
      .filter(m =>
        m.state == Manager.State.Active.wire && (m.kind == Manager.Kind.Seed.wire || m.kind == Manager.Kind.Watch.wire)
      )
      .map(_.id)

  // ── crons spread per goal ──────────────────────────────────────────────────────────────────────

  /** A stable non-negative hash of `key` (the same on every machine and every run). */
  def spread(key: ManagerRow.Id): Int = key.value.foldLeft(17)((h, c) => 31 * h + c) & Int.MaxValue

  /** `@daily`, `@weekly` and `@monthly` as five fields at a minute, hour (and weekday or day of the month) of `key`'s
    * own, so dozens of managers with the same cron do not fire in the same minute; any other cron is kept as given.
    */
  def stagger(base: Cron, key: ManagerRow.Id): Cron =
    val h = spread(key)
    val (min, hour, day) = (h % 60, (h / 60) % 24, h / 1440)
    Cron(base.value.trim.toLowerCase match
      case "@daily"   => s"$min $hour * * *"
      case "@weekly"  => s"$min $hour * * ${day % 7}"
      case "@monthly" => s"$min $hour ${1 + day % 28} * *"
      case _          => base.value.trim)

  // ── new sources reaching an article ────────────────────────────────────────────────────────────

  /** Why an article is extended now, if it is: its filter returns at least `min` sources it neither cites nor gives a
    * reason for, and it was not extended in the last `cooldownDays`. `min` 0 = never.
    */
  def extendDue(
      newSources: NewSources,
      min: Settings.ExtendMinNew,
      lastExtend: Option[Instant],
      cooldownDays: CooldownDays,
      now: Instant
  ): Option[Description] =
    if min.value <= 0 || newSources.value < min.value then None
    else if lastExtend.exists(t => t.plus(Duration.ofDays(math.max(0, cooldownDays.value).toLong)).isAfter(now)) then
      None
    else
      Some(
        Description(
          s"its filter returns ${counted(newSources.value, "source")} it neither cites nor gives a reason for"
        )
      )

  // ── caps, housekeeping, throughput ─────────────────────────────────────────────────────────────

  /** What is left today of the ingests all watchers share (None: no shared cap). */
  def sharedIngestLeft(cap: Cap, ingestedToday: IngestedToday): Option[Left] =
    Option.when(cap.value > 0)(Left(math.max(0, cap.value - math.max(0, ingestedToday.value))))

  /** Is a housekeeping job due: never run, or `everyHours` passed since (0 = never). */
  def due(last: Option[Instant], everyHours: EveryHours, now: Instant): Boolean =
    everyHours.value > 0 && last.forall(l => !l.plus(Duration.ofHours(everyHours.value.toLong)).isAfter(now))

  /** Days until `open` items are done at the pace of the last week (`doneLast7` items); None when nothing is open or
    * nothing was done.
    */
  def etaDays(open: OpenCount, doneLast7: DoneLast7): Option[Days] =
    Option.when(open.value > 0 && doneLast7.value > 0)(
      Days(math.max(1, math.ceil(open.value * 7.0 / doneLast7.value).toInt))
    )
