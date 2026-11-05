package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Plural

import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.automation.Goal

/** The autopilot's FOCUS (docs/research-process-managers.md "As built: the focus"), pure: which research goals are
  * ACTIVE for model work — at most `max` at a time — which wait and why, which of their items are held, and in which
  * order the library-wide managers start their items. No I/O: the server reads the facts, applies the plan and records
  * it.
  *
  * A goal is active until it is reviewed, or it can do nothing more with a model (its items stopped, all done, or
  * waiting for a person); then the next one takes its slot at once. The order is FINISH-FIRST:
  *
  *   1. a person's pin (always active);
  *   2. the stage — articles to review, then approved articles to compile, then enough sources for articles, then still
  *      gathering its plan and sources;
  *   3. depth-first — a sibling of an active goal, then a goal in the same tree, then one elsewhere;
  *   4. a person's priority (higher first);
  *   5. more sources first, then the oldest, then the slug.
  *
  * An active goal keeps its slot while it has work (no flapping); only a pin, a lower cap, or a goal with articles to
  * review or compile (taking the slot of one still gathering) moves it back to the queue. A parent with unfinished
  * sub-goals takes a slot only for its plan; its own articles wait for its sub-goals.
  */
object GoalFocus:

  /** How close a goal is to done (lower = closer). */
  type UsesModelResult = UsesModelResult.T
  object UsesModelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UsesModelKind = UsesModelKind.T
  object UsesModelKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishedResult = FinishedResult.T
  object FinishedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishedStatus = FinishedStatus.T
  object FinishedStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfinishedKidsKidsPart1 = UnfinishedKidsKidsPart1.T
  object UnfinishedKidsKidsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildWorkPart1 = BuildWorkPart1.T
  object BuildWorkPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildSourcesPart1 = BuildSourcesPart1.T
  object BuildSourcesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildSourcesPart2 = BuildSourcesPart2.T
  object BuildSourcesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildArticlesPart1 = BuildArticlesPart1.T
  object BuildArticlesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildArticlesPart2 = BuildArticlesPart2.T
  object BuildArticlesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildPassed = BuildPassed.T
  object BuildPassed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildCompileOpenPart1 = BuildCompileOpenPart1.T
  object BuildCompileOpenPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildCompileOpenPart2 = BuildCompileOpenPart2.T
  object BuildCompileOpenPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildLastStopsPart1 = BuildLastStopsPart1.T
  object BuildLastStopsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildLastStopsPart2 = BuildLastStopsPart2.T
  object BuildLastStopsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoingResult = DoingResult.T
  object DoingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StageMinSources = StageMinSources.T
  object StageMinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StageWhyResult = StageWhyResult.T
  object StageWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StageWhyMinSources = StageWhyMinSources.T
  object StageWhyMinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FitKidsPart1 = FitKidsPart1.T
  object FitKidsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootResult = RootResult.T
  object RootResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootSlug = RootSlug.T
  object RootSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootParentOfArg1 = RootParentOfArg1.T
  object RootParentOfArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootParentOfResult = RootParentOfResult.T
  object RootParentOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KinResultPart1 = KinResultPart1.T
  object KinResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KinResultPart2 = KinResultPart2.T
  object KinResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KinRootOfArg1 = KinRootOfArg1.T
  object KinRootOfArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KinRootOfResult = KinRootOfResult.T
  object KinRootOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyK = KeyK.T
  object KeyK extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickWhyResult = PickWhyResult.T
  object PickWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickWhyKPart1 = PickWhyKPart1.T
  object PickWhyKPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickWhyKPart2 = PickWhyKPart2.T
  object PickWhyKPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickWhyMinSources = PickWhyMinSources.T
  object PickWhyMinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FairOrderFocusResult = FairOrderFocusResult.T
  object FairOrderFocusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FairOrderRecent = FairOrderRecent.T
  object FairOrderRecent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FairOrderRatio = FairOrderRatio.T
  object FairOrderRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtaDaysResult = EtaDaysResult.T
  object EtaDaysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtaDaysOpen = EtaDaysOpen.T
  object EtaDaysOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtaDaysDoneLast7 = EtaDaysDoneLast7.T
  object EtaDaysDoneLast7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Stage(val rank: Stage.Rank, val wire: Stage.Wire):
    case Reviewing extends Stage(0, "reviewing")
    case Compiling extends Stage(1, "compiling")
    case Ready extends Stage(2, "ready")
    case Gathering extends Stage(3, "gathering")

  object Stage:
    type Rank = Rank.T
    object Rank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.domain.GoalFocus.Stage.ParseS): Option[Stage] = values.find(_.wire == s.trim.toLowerCase)

  /** Whether an item kind's pipeline calls a model (the currency check and the ingest backlog do not: they run for
    * every goal whatever the focus).
    */
  def usesModel(kind: com.histmersion.domain.GoalFocus.UsesModelKind): com.histmersion.domain.GoalFocus.UsesModelResult = kind != CurrencyCheck && kind != OaIngest

  /** Reviewed or done: the focus has nothing more to do for it. */
  def finished(status: com.histmersion.domain.GoalFocus.FinishedStatus): com.histmersion.domain.GoalFocus.FinishedResult = status == Goal.Status.Reviewed || status == Goal.Status.Done

  /** One open (pending, held, active) model-using item of a goal: its seeding manager's, its watcher's sweep, or the
    * review manager's on one of its articles. `focusHeld` = held by the focus; `personHeld` = held by a person (not for
    * an approval); `approval` = the approval it waits for (held for it, or its run waits for it), `approvalByPerson` =
    * only a person decides that one.
    */
  final case class Work(
      itemId: Work.ItemId,
      kind: Work.Kind,
      status: Work.Status,
      target: Work.Target = "",
      person: Work.Person = false,
      focusHeld: Work.FocusHeld = false,
      personHeld: Work.PersonHeld = false,
      approval: Option[Work.Approval] = None,
      approvalByPerson: Work.ApprovalByPerson = false
  ):
    def waitsForPerson: com.histmersion.domain.GoalFocus.Work.WaitsForPersonResult = approval.isDefined && approvalByPerson

    /** It can still use a model without a person: not waiting for a person's approval, not held by one. */
    def runnable: com.histmersion.domain.GoalFocus.Work.RunnableResult = !waitsForPerson && !personHeld

  object Work:
    type WaitsForPersonResult = WaitsForPersonResult.T
    object WaitsForPersonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunnableResult = RunnableResult.T
    object RunnableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ItemId = ItemId.T
    object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Person = Person.T
    object Person extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHeld = FocusHeld.T
    object FocusHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PersonHeld = PersonHeld.T
    object PersonHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Approval = Approval.T
    object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalByPerson = ApprovalByPerson.T
    object ApprovalByPerson extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A goal as the focus sees it. `articles` = its linked articles that exist, `unreviewed` = those not passed their
    * review; `compileOpen` = its approved articles not compiled yet; `work` = its open model-using items; `lastStop` =
    * the outcome of its last stopped or failed item; `pinned`/`priority` = a person's; `active`/`since` = what the
    * focus recorded.
    */
  final case class Facts(
      slug: Facts.Slug,
      parent: Option[Facts.Parent] = None,
      status: Facts.Status = Goal.Status.Seeding,
      createdAt: Facts.CreatedAt = "",
      sources: Facts.Sources = 0,
      articles: Facts.Articles = 0,
      unreviewed: Facts.Unreviewed = 0,
      compileOpen: Facts.CompileOpen = 0,
      work: List[Work] = Nil,
      lastStop: Option[Facts.LastStop] = None,
      pinned: Facts.Pinned = false,
      priority: Facts.Priority = 0,
      active: Facts.Active = false,
      since: Option[Facts.Since] = None
  )

  object Facts:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Parent = Parent.T
    object Parent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sources = Sources.T
    object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Articles = Articles.T
    object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unreviewed = Unreviewed.T
    object Unreviewed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CompileOpen = CompileOpen.T
    object CompileOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastStop = LastStop.T
    object LastStop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pinned = Pinned.T
    object Pinned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Active = Active.T
    object Active extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `max` goals at most (≤ 0: no cap); `minSources` = what a proposed article needs (`agents.seed.min-sources`);
    * `plansByPerson` = a person decides goal plans (`agents.approvals.goal-plan = manual`) and `openPersonPlans` of
    * them are open — at `max` such open plans, no further goal is activated only to ask for one more.
    */
  final case class Settings(max: Settings.Max, minSources: Settings.MinSources, plansByPerson: Settings.PlansByPerson = false, openPersonPlans: Settings.OpenPersonPlans = 0)

  object Settings:
    type Max = Max.T
    object Max extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinSources = MinSources.T
    object MinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlansByPerson = PlansByPerson.T
    object PlansByPerson extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenPersonPlans = OpenPersonPlans.T
    object OpenPersonPlans extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Where a goal stands: finished, a candidate at a stage, or waiting (a code and why). */
  enum Fit:
    case Finished
    case Candidate(stage: Stage)
    case Waits(code: String, why: String)

  /** The codes of [[Fit.Waits]]. */
  object Code:
    val Children = "children"
    val Person = "person"
    val Held = "held"
    val Stuck = "stuck"
    val Idle = "idle"
    val Plans = "plans"

  /** An active or queued goal, with why it is where it is. */
  final case class Pick(slug: Pick.Slug, stage: Stage, why: Pick.Why, pinned: Pick.Pinned = false, since: Option[Pick.Since] = None)

  object Pick:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pinned = Pinned.T
    object Pinned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What one pass decides: the active goals (in their order), the queue (the order the next would become active), the
    * goals that joined now, the ones that left (why), and the ones that cannot use a slot now (slug, code, why).
    */
  final case class Plan(
      active: List[Pick] = Nil,
      queue: List[Pick] = Nil,
      joined: List[Plan.Joined] = Nil,
      left: List[(Plan.LeftPart1, Plan.LeftPart2)] = Nil,
      waiting: List[(Plan.WaitingPart1, Plan.WaitingPart2, Plan.WaitingPart3)] = Nil
  ):
    lazy val activeSet: Set[com.histmersion.domain.GoalFocus.Pick.Slug] = active.map(_.slug).toSet

  object Plan:
    type Joined = Joined.T
    object Joined extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeftPart1 = LeftPart1.T
    object LeftPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeftPart2 = LeftPart2.T
    object LeftPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WaitingPart1 = WaitingPart1.T
    object WaitingPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WaitingPart2 = WaitingPart2.T
    object WaitingPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WaitingPart3 = WaitingPart3.T
    object WaitingPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def unfinishedKids(f: Facts, kids: Map[com.histmersion.domain.GoalFocus.UnfinishedKidsKidsPart1, List[Facts]]): List[Facts] =
    kids.getOrElse(f.slug, Nil).filterNot(k => finished(k.status))

  /** Every goal's facts from what the store reads for all of them at once: `goals` with their identity and what the
    * focus recorded (the counts left empty), each open item with the goals it belongs to, and the per-goal counts.
    * Pure.
    */
  def build(
      goals: List[Facts],
      work: List[(List[com.histmersion.domain.GoalFocus.BuildWorkPart1], Work)],
      sources: Map[com.histmersion.domain.GoalFocus.BuildSourcesPart1, com.histmersion.domain.GoalFocus.BuildSourcesPart2],
      articles: Map[com.histmersion.domain.GoalFocus.BuildArticlesPart1, List[com.histmersion.domain.GoalFocus.BuildArticlesPart2]],
      passed: Set[com.histmersion.domain.GoalFocus.BuildPassed],
      compileOpen: Map[com.histmersion.domain.GoalFocus.BuildCompileOpenPart1, com.histmersion.domain.GoalFocus.BuildCompileOpenPart2],
      lastStops: Map[com.histmersion.domain.GoalFocus.BuildLastStopsPart1, com.histmersion.domain.GoalFocus.BuildLastStopsPart2]
  ): List[Facts] =
    val byGoal = work.flatMap((gs, w) => gs.distinct.map(_ -> w)).groupMap(_._1)(_._2)
    goals.map { g =>
      val arts = articles.getOrElse(g.slug, Nil)
      g.modify(
        _.sources).setTo(sources.getOrElse(g.slug, 0)).modify(_.articles).setTo(arts.size).modify(_.unreviewed).setTo(arts.count(a => !passed(a))).modify(_.compileOpen).setTo(compileOpen.getOrElse(g.slug, 0)).modify(_.work).setTo(byGoal.getOrElse(g.slug, Nil).sortBy(_.itemId)).modify(_.lastStop).setTo(lastStops.get(g.slug)
      )
    }

  /** What an active goal is doing or waiting for, in a line: its runs in flight, the approvals it waits for (and who
    * decides them), its next item.
    */
  def doing(work: List[Work]): Option[com.histmersion.domain.GoalFocus.DoingResult] =
    def name(w: Work) = s"${w.kind} ${w.target}".trim
    val running = work.filter(w => w.status == ItemStatus.Active.wire && w.approval.isEmpty)
    val waiting = work.filter(_.approval.isDefined)
    val next = work.filter(w => w.status == ItemStatus.Pending.wire || w.focusHeld)
    val parts = List(
      Option.when(running.nonEmpty)(
        s"running ${running.take(3).map(name).mkString(", ")}" +
          (if running.size > 3 then s" (+${running.size - 3})" else "")
      ),
      Option.when(waiting.nonEmpty)(
        waiting
          .take(3)
          .map(w =>
            s"approval #${w.approval.get} (" +
              (if w.approvalByPerson then "a person decides" else "the autopilot's checks decide") + ")"
          )
          .mkString(", ")
      ),
      Option.when(next.nonEmpty)(s"next ${name(next.head)}" + (if next.size > 1 then s" (+${next.size - 1} more)"
                                                               else ""))
    ).flatten
    Option.when(parts.nonEmpty)(parts.mkString("; "))

  /** The stage the facts put a goal at. */
  def stage(f: Facts, minSources: com.histmersion.domain.GoalFocus.StageMinSources): Stage =
    if f.unreviewed > 0 then Stage.Reviewing
    else if f.compileOpen > 0 then Stage.Compiling
    else if f.sources >= math.max(1, minSources) then Stage.Ready
    else Stage.Gathering

  /** The stage in words, with the numbers behind it. */
  def stageWhy(f: Facts, st: Stage, minSources: com.histmersion.domain.GoalFocus.StageWhyMinSources): com.histmersion.domain.GoalFocus.StageWhyResult = st match
    case Stage.Reviewing => s"${f.unreviewed} of ${Plural.count(f.articles, "article")} still to pass review"
    case Stage.Compiling => s"${Plural.count(f.compileOpen, "approved article")} to compile"
    case Stage.Ready => s"${Plural.count(f.sources, "source")} (at least ${math.max(1, minSources)}), no article yet"
    case Stage.Gathering => s"${Plural.count(f.sources, "source")}: still gathering its plan and sources"

  /** Where one goal stands now. A parent with unfinished sub-goals counts only its plan; a goal whose model-using items
    * all wait for a person (an approval only a person decides, or a person's hold), or that has none left (stopped:
    * stuck; else idle until its cron or new sources), cannot use a slot.
    */
  def fit(f: Facts, kids: Map[com.histmersion.domain.GoalFocus.FitKidsPart1, List[Facts]], s: Settings): Fit =
    if finished(f.status) then Fit.Finished
    else
      val open = unfinishedKids(f, kids)
      val allowed = if open.isEmpty then f.work else f.work.filter(_.kind == PlanGoalItem)
      // an item that waits for another (a proposal for its plan, a review for its filter) can run only when
      // that one can: a proposal behind a plan waiting for a person waits for that person too
      val runnable = allowed.filter(w => w.runnable && blockers(w, f.work).forall(_.runnable))
      if runnable.isEmpty && open.nonEmpty then
        Fit.Waits(
          Code.Children,
          s"waits for its ${Plural.count(open.size, "unfinished sub-goal")} before its own articles"
        )
      else if f.work.isEmpty then
        f.lastStop match
          case Some(o) => Fit.Waits(Code.Stuck, s"stuck: its last item stopped — ${o.take(300)}")
          case None    =>
            Fit.Waits(Code.Idle, "idle: nothing for a model to do until its cron fires or new sources arrive")
      else if runnable.isEmpty then
        val ids = (allowed ++ allowed
          .flatMap(blockers(_, f.work))).flatMap(w => w.approval.filter(_ => w.approvalByPerson)).distinct.sorted
        if ids.nonEmpty then
          Fit.Waits(Code.Person, s"waits for a person: ${ids.map(i => s"approval #$i").mkString(", ")}")
        else Fit.Waits(Code.Held, "its items are held by a person")
      else if !f.active && !f.pinned && s.plansByPerson && s.max > 0 && s.openPersonPlans >= s.max &&
        runnable.forall(w => w.kind == PlanGoalItem && w.status == ItemStatus.Pending.wire)
      then
        Fit.Waits(
          Code.Plans,
          s"its next step is a goal plan, and ${Plural.count(s.openPersonPlans, "goal plan")} already wait for a person"
        )
      else Fit.Candidate(stage(f, s.minSources))

  /** The open items `w` waits for before it may run ([[ManagerPolicy.waitsFor]]): a proposal waits for its goal's plan,
    * an article's review for the article's filter.
    */
  def blockers(w: Work, all: List[Work]): List[Work] = w.kind match
    case ProposeArticles => all.filter(_.kind == PlanGoalItem)
    case ArticleReview   => all.filter(p => p.kind == InferFilter && p.target == w.target)
    case _               => Nil

  /** The root of a goal's tree (a cycle or a missing parent stops the walk). */
  def root(slug: com.histmersion.domain.GoalFocus.RootSlug, parentOf: com.histmersion.domain.GoalFocus.RootParentOfArg1 => Option[com.histmersion.domain.GoalFocus.RootParentOfResult]): com.histmersion.domain.GoalFocus.RootResult =
    @annotation.tailrec
    def go(s: String, seen: Set[String]): String = parentOf(s) match
      case Some(p) if !seen(p) && seen.size < 64 => go(p, seen + p)
      case _                                     => s
    go(slug, Set(slug))

  /** How near `f` is to the goals already chosen: 0 = a sibling (or a sub-goal) of one, 1 = in the same tree, 2 =
    * elsewhere.
    */
  private def kin(f: Facts, chosen: List[Facts], rootOf: com.histmersion.domain.GoalFocus.KinRootOfArg1 => com.histmersion.domain.GoalFocus.KinRootOfResult): (com.histmersion.domain.GoalFocus.KinResultPart1, Option[com.histmersion.domain.GoalFocus.KinResultPart2]) =
    chosen.find(c => (c.parent.isDefined && c.parent == f.parent) || f.parent.contains(c.slug)) match
      case Some(c) => (0, Some(c.slug))
      case None    =>
        val r = rootOf(f.slug)
        chosen.find(c => rootOf(c.slug) == r) match
          case Some(c) => (1, Some(c.slug))
          case None    => (2, None)

  private type Key = (Int, Int, Int, Int, String, String)
  private def key(f: Facts, st: Stage, k: com.histmersion.domain.GoalFocus.KeyK): Key = (st.rank, k, -f.priority, -f.sources, f.createdAt, f.slug)

  private def pickWhy(f: Facts, st: Stage, k: (com.histmersion.domain.GoalFocus.PickWhyKPart1, Option[com.histmersion.domain.GoalFocus.PickWhyKPart2]), minSources: com.histmersion.domain.GoalFocus.PickWhyMinSources): com.histmersion.domain.GoalFocus.PickWhyResult =
    val near = k match
      case (0, Some(s)) if f.parent.contains(s) => List(s"a sub-goal of $s (active)")
      case (0, Some(s)) => List(s"a sibling of $s (active): one parent's sub-goals are finished before another's")
      case (1, Some(s)) => List(s"in the same tree as $s (active)")
      case _            => Nil
    (List(s"${st.wire}: ${stageWhy(f, st, minSources)}") ++ near ++
      Option.when(f.priority != 0)(s"priority ${f.priority}").toList ++
      Option.when(f.createdAt.nonEmpty)(s"created ${f.createdAt.take(10)}").toList).mkString("; ")

  /** One pass of the focus over every goal. Pins are always active; the goals active before keep their slots while they
    * are candidates (the cap lowered: the worst go back to the queue first); free slots go to the best candidates one
    * at a time (each pick makes its siblings and its tree nearer for the next); then a candidate with articles to
    * review or compile takes the slot of an active goal still gathering sources. Deterministic: the same facts give the
    * same plan.
    */
  def select(goals: List[Facts], s: Settings): Plan =
    val bySlug = goals.map(g => g.slug -> g).toMap
    val kids = goals.filter(_.parent.isDefined).groupBy(_.parent.get)
    val rootMemo = collection.mutable.Map.empty[String, String] // memo of the walk; local to this call
    def rootOf(slug: String): String = rootMemo.getOrElseUpdate(slug, root(slug, x => bySlug.get(x).flatMap(_.parent)))
    val fits = goals.map(g => g -> fit(g, kids, s))
    val cands = fits.collect { case (g, Fit.Candidate(st)) => (g, st) }
    val stageOf = cands.map((g, st) => g.slug -> st).toMap
    val cap = if s.max <= 0 then Int.MaxValue else s.max
    def staticKey(g: Facts) = key(g, stageOf(g.slug), 2)
    val pinned = cands.filter(_._1.pinned).map(_._1).sortBy(staticKey)
    val kept0 = cands.filter((g, _) => g.active && !g.pinned).map(_._1).sortBy(staticKey)
    val room0 = math.max(0, cap - pinned.size)
    val (kept, displaced) = (kept0.take(room0), kept0.drop(room0))
    // free slots, greedily: each pick changes who is near
    @annotation.tailrec
    def fill(chosen: List[Facts], pool: List[Facts], acc: List[(Facts, String)]): (List[Facts], List[(Facts, String)]) =
      if chosen.size >= cap || pool.isEmpty then (chosen, acc)
      else
        val best = pool.minBy(g => key(g, stageOf(g.slug), kin(g, chosen, rootOf)._1))
        fill(
          chosen :+ best,
          pool.filterNot(_.slug == best.slug),
          acc :+ (best -> pickWhy(best, stageOf(best.slug), kin(best, chosen, rootOf), s.minSources))
        )
    val pool0 = cands
      .map(_._1)
      .filterNot(g => g.pinned || kept.exists(_.slug == g.slug))
      .filterNot(g => displaced.exists(_.slug == g.slug))
    val (chosen1, fresh1) = fill(pinned ++ kept, pool0, Nil)
    // a goal with articles to review or compile takes the slot of one still gathering (never a pinned one)
    @annotation.tailrec
    def preempt(
        chosen: List[Facts],
        fresh: List[(Facts, String)],
        swaps: List[(String, String)]
    ): (List[Facts], List[(Facts, String)], List[(String, String)]) =
      val pool = cands.map(_._1).filterNot(g => chosen.exists(_.slug == g.slug))
      val urgent = pool.filter(g => stageOf(g.slug).rank <= Stage.Compiling.rank)
      val victims = chosen.filter(g => !g.pinned && stageOf(g.slug) == Stage.Gathering)
      if urgent.isEmpty || victims.isEmpty then (chosen, fresh, swaps)
      else
        val rest = chosen.filterNot(g => victims.exists(_.slug == g.slug))
        val in = urgent.minBy(g => key(g, stageOf(g.slug), kin(g, rest, rootOf)._1))
        val out = victims.maxBy(staticKey)
        val left = chosen.filterNot(_.slug == out.slug)
        val why = pickWhy(
          in,
          stageOf(in.slug),
          kin(in, left, rootOf),
          s.minSources
        ) + s"; took the slot of ${out.slug} (still gathering)"
        preempt(
          left :+ in,
          fresh.filterNot(_._1.slug == out.slug) :+ (in -> why),
          swaps :+ (out.slug -> s"gave its slot to ${in.slug}, which has articles to ${
              if stageOf(in.slug) == Stage.Reviewing then "review" else "compile"
            }")
        )
    val (chosen, fresh, swaps) = preempt(chosen1, fresh1, Nil)
    val freshWhy = fresh.map((g, w) => g.slug -> w).toMap
    val active = chosen.map { g =>
      val st = stageOf(g.slug)
      val why =
        if g.pinned then s"pinned by a person; ${st.wire}: ${stageWhy(g, st, s.minSources)}"
        else
          freshWhy.getOrElse(
            g.slug,
            s"active${g.since.fold("")(t => s" since $t")}, kept until it is reviewed; ${st.wire}: " +
              stageWhy(g, st, s.minSources)
          )
      Pick(g.slug, st, why, g.pinned, if g.active then g.since else None)
    }
    // the queue, in the order the next free slots would take them
    @annotation.tailrec
    def order(chosen: List[Facts], pool: List[Facts], acc: List[Pick]): List[Pick] =
      if pool.isEmpty then acc
      else
        val best = pool.minBy(g => key(g, stageOf(g.slug), kin(g, chosen, rootOf)._1))
        order(
          chosen :+ best,
          pool.filterNot(_.slug == best.slug),
          acc :+ Pick(
            best.slug,
            stageOf(best.slug),
            pickWhy(best, stageOf(best.slug), kin(best, chosen, rootOf), s.minSources),
            best.pinned
          )
        )
    val queue = order(chosen, cands.map(_._1).filterNot(g => chosen.exists(_.slug == g.slug)), Nil)
    val waiting = fits.collect { case (g, Fit.Waits(c, w)) => (g.slug, c, w) }
    val swapped = swaps.toMap
    val left = goals.filter(g => g.active && !chosen.exists(_.slug == g.slug)).map { g =>
      val why = fits.find(_._1.slug == g.slug).map(_._2) match
        case Some(Fit.Finished)    => s"it is ${g.status}"
        case Some(Fit.Waits(_, w)) => w
        case _                     =>
          swapped.getOrElse(
            g.slug,
            if displaced.exists(_.slug == g.slug) then
              s"the cap of $cap active goals is full (lowered, or a pin took the slot)"
            else "back in the queue"
          )
      g.slug -> why
    }
    Plan(active, queue, chosen.filterNot(_.active).map(_.slug), left, waiting)

  // ── the items' holds ───────────────────────────────────────────────────────────────────────────

  /** One open item as the holds see it: the goals it belongs to (a seeding manager's or watcher's: its goal; a review
    * item: the goals its article is linked to; none: library work).
    */
  final case class Item(
      id: Item.Id,
      kind: Item.Kind,
      status: Item.Status,
      goals: List[Item.Goals],
      person: Item.Person = false,
      focusHeld: Item.FocusHeld = false
  )

  object Item:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goals = Goals.T
    object Goals extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Person = Person.T
    object Person extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHeld = FocusHeld.T
    object FocusHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What the holds change: items to hold (id, goal, reason) and focus holds to release. */
  final case class Holds(hold: List[(Holds.HoldPart1, Holds.HoldPart2, Holds.HoldPart3)] = Nil, release: List[Holds.Release] = Nil)

  object Holds:
    type HoldPart1 = HoldPart1.T
    object HoldPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HoldPart2 = HoldPart2.T
    object HoldPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HoldPart3 = HoldPart3.T
    object HoldPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Release = Release.T
    object Release extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The reason a focus hold carries (it starts with `focus:`; the items window and the status show it). */
  val HoldPrefix = "focus:"

  /** Which items wait: a PENDING model-using item that is not a person's, of an unfinished goal that is not active (or
    * of an active parent whose sub-goals are not finished, apart from its plan) is held; a focus hold whose goal became
    * active (or finished, or only library work is left, or a person asked for it) is released. An item of several goals
    * runs when one of them lets it. Items in flight are never touched.
    */
  def holds(items: List[Item], plan: Plan, goals: List[Facts]): Holds =
    val bySlug = goals.map(g => g.slug -> g).toMap
    val openKids = goals.filter(g => g.parent.isDefined && !finished(g.status)).groupBy(_.parent.get)
    val decided = items.map { i =>
      val unfinished = i.goals.filter(g => bySlug.get(g).exists(f => !finished(f.status)))
      val actives = unfinished.filter(plan.activeSet)
      val wait: Option[(String, String)] =
        if !usesModel(i.kind) || unfinished.isEmpty || i.person then None
        else if actives.nonEmpty then
          if actives.exists(g => !openKids.contains(g) || i.kind == PlanGoalItem) then None
          else
            Some(actives.head -> s"$HoldPrefix goal ${actives.head} waits for its sub-goals before its own ${i.kind}")
        else
          Some(
            unfinished.head -> s"$HoldPrefix waits until goal ${unfinished.head} is one of the active goals (agents.autopilot.max-active-goals)"
          )
      (i, wait)
    }
    Holds(
      decided.collect {
        case (i, Some((g, why))) if i.status == ItemStatus.Pending.wire && !i.focusHeld => (i.id, g, why)
      },
      decided.collect { case (i, None) if i.focusHeld && i.status == ItemStatus.Held.wire => i.id }
    )

  // ── the library-wide managers ──────────────────────────────────────────────────────────────────

  /** The order the enrich and review managers start their due items in: `ratio` of the active goals' (and a person's)
    * items to one of any other, counting on from the last starts (`recent`, newest first: true = an active goal's). The
    * order inside each class is kept. `ratio` ≤ 0: the active goals' first, the others only after them.
    */
  def fairOrder[A](cs: List[A], focus: A => com.histmersion.domain.GoalFocus.FairOrderFocusResult, recent: List[com.histmersion.domain.GoalFocus.FairOrderRecent], ratio: com.histmersion.domain.GoalFocus.FairOrderRatio): List[A] =
    val (fs, os) = cs.partition(focus)
    if ratio <= 0 || fs.isEmpty || os.isEmpty then fs ++ os
    else
      @annotation.tailrec
      def go(f: List[A], o: List[A], run: Int, acc: List[A]): List[A] =
        (f, o) match
          case (Nil, _)           => acc ++ o
          case (_, Nil)           => acc ++ f
          case (x :: xs, y :: ys) =>
            if run >= ratio then go(f, ys, 0, acc :+ y) else go(xs, o, run + 1, acc :+ x)
      go(fs, os, recent.takeWhile(identity).size, Nil)

  /** Days until a goal's open items are done at its pace of the last week (None: nothing open or nothing done to
    * measure by).
    */
  def etaDays(open: com.histmersion.domain.GoalFocus.EtaDaysOpen, doneLast7: com.histmersion.domain.GoalFocus.EtaDaysDoneLast7): Option[com.histmersion.domain.GoalFocus.EtaDaysResult] = AutopilotPolicy.etaDays(open, doneLast7)
