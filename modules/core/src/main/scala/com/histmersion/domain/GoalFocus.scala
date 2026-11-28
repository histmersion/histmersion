package com.histmersion
package domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Plural

import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.Slug
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

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
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.AutomationGoals)
object GoalFocus:
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Ratio = Ratio.T
  object Ratio extends Newtype[Int, Pure]
  enum Recent derives CanEqual:
    case Focused, Other
  object Recent extends TwoCases[Recent]:
    extension (recent: Recent) def focused: Boolean = recent == Focused
  type WaitCode = WaitCode.T
  object WaitCode extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def counted(n: Int, singular: String): String =
    Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(singular)).value

  /** How close a goal is to done (lower = closer). */
  enum Stage(val rank: Stage.Rank, val wire: Stage.Wire) derives CanEqual:
    case Reviewing extends Stage(Stage.Rank(0), Stage.Wire("reviewing"))
    case Compiling extends Stage(Stage.Rank(1), Stage.Wire("compiling"))
    case Ready extends Stage(Stage.Rank(2), Stage.Wire("ready"))
    case Gathering extends Stage(Stage.Rank(3), Stage.Wire("gathering"))

  object Stage:
    type Rank = Rank.T
    object Rank extends Newtype[Int, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[Stage] = values.find(_.wire == Wire(s.value.trim.toLowerCase))

  /** Whether an item kind's pipeline calls a model (the currency check and the ingest backlog do not: they run for
    * every goal whatever the focus).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def usesModel(kind: ManagerItem.Kind): Boolean = kind.value != CurrencyCheck && kind.value != OaIngest

  /** Reviewed or done: the focus has nothing more to do for it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def finished(status: Goal.Status.Wire): Boolean =
    status.value == Goal.Status.Reviewed || status.value == Goal.Status.Done

  /** One open (pending, held, active) model-using item of a goal: its seeding manager's, its watcher's sweep, or the
    * review manager's on one of its articles. `focusHeld` = held by the focus; `personHeld` = held by a person (not for
    * an approval); `approval` = the approval it waits for (held for it, or its run waits for it), `approvalByPerson` =
    * only a person decides that one.
    */
  final case class Work(
      itemId: Work.ItemId,
      kind: ManagerItem.Kind,
      status: ItemStatus.Wire,
      target: Work.Target = Work.Target(""),
      person: Work.Person = Work.Person.ForModel,
      focusHeld: Work.FocusHeld = Work.FocusHeld.Free,
      personHeld: Work.HeldByPerson = Work.HeldByPerson.Free,
      approval: Option[Work.Approval] = None,
      approvalByPerson: Work.ApprovedBy = Work.ApprovedBy.Model
  ):
    def waitsForPerson: Boolean = approval.isDefined && approvalByPerson.approvalByPerson

    /** It can still use a model without a person: not waiting for a person's approval, not held by one. */
    def runnable: Boolean = !waitsForPerson && !personHeld.personHeld

  object Work:

    type ItemId = ItemId.T
    object ItemId extends Newtype[Long, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    enum Person derives CanEqual:
      case ForPerson, ForModel
    object Person extends TwoCases[Person]:
      extension (person: Person) def person: Boolean = person == ForPerson
    enum FocusHeld derives CanEqual:
      case Held, Free
    object FocusHeld extends TwoCases[FocusHeld]:
      extension (focusHeld: FocusHeld) def focusHeld: Boolean = focusHeld == Held
    enum HeldByPerson derives CanEqual:
      case Held, Free
    object HeldByPerson extends TwoCases[HeldByPerson]:
      extension (personHeld: HeldByPerson) def personHeld: Boolean = personHeld == Held
    type Approval = Approval.T
    object Approval extends Newtype[Long, Pure]
    enum ApprovedBy derives CanEqual:
      case Person, Model
    object ApprovedBy extends TwoCases[ApprovedBy]:
      extension (approvalByPerson: ApprovedBy) def approvalByPerson: Boolean = approvalByPerson == Person

  /** A goal as the focus sees it. `articles` = its linked articles that exist, `unreviewed` = those not passed their
    * review; `compileOpen` = its approved articles not compiled yet; `work` = its open model-using items; `lastStop` =
    * the outcome of its last stopped or failed item; `pinned`/`priority` = a person's; `active`/`since` = what the
    * focus recorded.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  final case class Facts(
      slug: Goal.Slug,
      parent: Option[Goal.Slug] = None,
      status: Goal.Status.Wire = Goal.Status.Wire(Goal.Status.Seeding),
      createdAt: Facts.CreatedAt = Facts.CreatedAt(""),
      sources: Facts.Sources = Facts.Sources(0),
      articles: Facts.Articles = Facts.Articles(0),
      unreviewed: Facts.Unreviewed = Facts.Unreviewed(0),
      compileOpen: Facts.CompileOpen = Facts.CompileOpen(0),
      work: List[Work] = Nil,
      lastStop: Option[Facts.LastStop] = None,
      pinned: Facts.Pinned = Facts.Pinned.Unpinned,
      priority: Facts.Priority = Facts.Priority(0),
      active: Facts.Active = Facts.Active.Inactive,
      since: Option[Facts.Since] = None
  )

  object Facts:
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type Sources = Sources.T
    object Sources extends Newtype[Int, Pure]
    export AutopilotPolicy.NextFacts.Articles
    export AutopilotPolicy.NextFacts.Unreviewed
    export AutopilotPolicy.NextFacts.CompileOpen
    type LastStop = LastStop.T
    object LastStop extends Newtype[String, Pure]
    enum Pinned derives CanEqual:
      case Pinned, Unpinned
    object Pinned extends TwoCases[Pinned]:
      extension (pinned: Pinned) def pinned: Boolean = pinned == Pinned
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]
    enum Active derives CanEqual:
      case Active, Inactive
    object Active extends TwoCases[Active]:
      extension (active: Active) def active: Boolean = active == Active
    type Since = Since.T
    object Since extends Newtype[String, Pure]

  /** `max` goals at most (≤ 0: no cap); `minSources` = what a proposed article needs (`agents.seed.min-sources`);
    * `plansByPerson` = a person decides goal plans (`agents.approvals.goal-plan = manual`) and `openPersonPlans` of
    * them are open — at `max` such open plans, no further goal is activated only to ask for one more.
    */
  final case class Settings(
      max: Settings.Max,
      minSources: Settings.MinSources,
      plansByPerson: Settings.PlansByPerson = Settings.PlansByPerson.ByModel,
      openPersonPlans: Settings.OpenPersonPlans = Settings.OpenPersonPlans(0)
  )

  object Settings:
    type Max = Max.T
    object Max extends Newtype[Int, Pure]
    type MinSources = MinSources.T
    object MinSources extends Newtype[Int, Pure]
    enum PlansByPerson derives CanEqual:
      case ByPerson, ByModel
    object PlansByPerson extends TwoCases[PlansByPerson]:
      extension (plansByPerson: PlansByPerson) def plansByPerson: Boolean = plansByPerson == ByPerson
    type OpenPersonPlans = OpenPersonPlans.T
    object OpenPersonPlans extends Newtype[Int, Pure]

  /** Why a goal is where it is, or why an item waits: the narration of a decision that succeeded, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  /** Where a goal stands: finished, a candidate at a stage, or waiting (a code and why). */
  enum Fit:
    case Finished
    case Candidate(stage: Stage)
    case Waits(code: Fit.Waits.Code, why: Rationale)

  object Fit:
    object Waits:
      export GoalFocus.WaitCode as Code

  /** The codes of [[Fit.Waits]]. */
  object Code:
    val Children = WaitCode("children")
    val Person = WaitCode("person")
    val Held = WaitCode("held")
    val Stuck = WaitCode("stuck")
    val Idle = WaitCode("idle")
    val Plans = WaitCode("plans")

  /** An active or queued goal, with why it is where it is. */
  final case class Pick(
      slug: Goal.Slug,
      stage: Stage,
      why: Rationale,
      pinned: Facts.Pinned = Facts.Pinned.Unpinned,
      since: Option[Facts.Since] = None
  )

  /** What one pass decides: the active goals (in their order), the queue (the order the next would become active), the
    * goals that joined now, the ones that left (why), and the ones that cannot use a slot now (slug, code, why).
    */
  final case class Plan(
      active: List[Pick] = Nil,
      queue: List[Pick] = Nil,
      joined: List[Goal.Slug] = Nil,
      left: List[(Goal.Slug, Rationale)] = Nil,
      waiting: List[(Goal.Slug, Fit.Waits.Code, Rationale)] = Nil
  ):
    lazy val activeSet: Set[Goal.Slug] = active.map(_.slug).toSet

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def unfinishedKids(f: Facts, kids: Map[Goal.Slug, List[Facts]]): List[Facts] =
    kids.getOrElse(f.slug, Nil).filterNot(k => finished(k.status))

  /** Every goal's facts from what the store reads for all of them at once: `goals` with their identity and what the
    * focus recorded (the counts left empty), each open item with the goals it belongs to, and the per-goal counts.
    * Pure.
    */
  def build(
      goals: List[Facts],
      work: List[(List[Goal.Slug], Work)],
      sources: Map[Goal.Slug, Facts.Sources],
      articles: Map[Goal.Slug, List[Slug]],
      passed: Set[Slug],
      compileOpen: Map[Goal.Slug, Facts.CompileOpen],
      lastStops: Map[Goal.Slug, Facts.LastStop]
  ): List[Facts] =
    val byGoal = work.flatMap((gs, w) => gs.distinct.map(_ -> w)).groupMap(_._1)(_._2)
    goals.map { g =>
      val arts = articles.getOrElse(g.slug, Nil)
      g.modify(_.sources)
        .setTo(sources.getOrElse(g.slug, Facts.Sources(0)))
        .modify(_.articles)
        .setTo(Facts.Articles(arts.size))
        .modify(_.unreviewed)
        .setTo(Facts.Unreviewed(arts.count(a => !passed(a))))
        .modify(_.compileOpen)
        .setTo(compileOpen.getOrElse(g.slug, Facts.CompileOpen(0)))
        .modify(_.work)
        .setTo(byGoal.getOrElse(g.slug, Nil).sortBy(_.itemId))
        .modify(_.lastStop)
        .setTo(lastStops.get(g.slug))
    }

  /** What an active goal is doing or waiting for, in a line: its runs in flight, the approvals it waits for (and who
    * decides them), its next item.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def doing(work: List[Work]): Option[Description] =
    def name(w: Work) = s"${w.kind} ${w.target}".trim
    val running = work.filter(w => w.status == ItemStatus.Active.wire && w.approval.isEmpty)
    val waiting = work.filter(_.approval.isDefined)
    val next = work.filter(w => w.status == ItemStatus.Pending.wire || w.focusHeld.focusHeld)
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
              (if w.approvalByPerson.approvalByPerson then "a person decides"
               else "the autopilot's checks decide") + ")"
          )
          .mkString(", ")
      ),
      Option.when(next.nonEmpty)(s"next ${name(next.head)}" + (if next.size > 1 then s" (+${next.size - 1} more)"
                                                               else ""))
    ).flatten
    Option.when(parts.nonEmpty)(Description(parts.mkString("; ")))

  /** The stage the facts put a goal at. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stage(f: Facts, minSources: Settings.MinSources): Stage =
    if f.unreviewed.value > 0 then Stage.Reviewing
    else if f.compileOpen.value > 0 then Stage.Compiling
    else if f.sources.value >= math.max(1, minSources.value) then Stage.Ready
    else Stage.Gathering

  /** The stage in words, with the numbers behind it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stageWhy(f: Facts, st: Stage, minSources: Settings.MinSources): Rationale = st match
    case Stage.Reviewing =>
      Rationale(s"${f.unreviewed} of ${counted(f.articles.value, "article")} still to pass review")
    case Stage.Compiling =>
      Rationale(s"${counted(f.compileOpen.value, "approved article")} to compile")
    case Stage.Ready =>
      Rationale(
        s"${counted(f.sources.value, "source")} (at least ${math.max(1, minSources.value)}), no article yet"
      )
    case Stage.Gathering =>
      Rationale(s"${counted(f.sources.value, "source")}: still gathering its plan and sources")

  /** Where one goal stands now. A parent with unfinished sub-goals counts only its plan; a goal whose model-using items
    * all wait for a person (an approval only a person decides, or a person's hold), or that has none left (stopped:
    * stuck; else idle until its cron or new sources), cannot use a slot.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fit(f: Facts, kids: Map[Goal.Slug, List[Facts]], s: Settings): Fit =
    if finished(f.status) then Fit.Finished
    else
      val open = unfinishedKids(f, kids)
      val allowed = if open.isEmpty then f.work else f.work.filter(_.kind.value == PlanGoalItem)
      // an item that waits for another (a proposal for its plan, a review for its filter) can run only when
      // that one can: a proposal behind a plan waiting for a person waits for that person too
      val runnable = allowed.filter(w => w.runnable && blockers(w, f.work).forall(_.runnable))
      if runnable.isEmpty && open.nonEmpty then
        Fit.Waits(
          Code.Children,
          Rationale(s"waits for its ${counted(open.size, "unfinished sub-goal")} before its own articles")
        )
      else if f.work.isEmpty then
        f.lastStop match
          case Some(o) =>
            Fit.Waits(
              Code.Stuck,
              Rationale(s"stuck: its last item stopped — ${o.value.take(300)}")
            )
          case None =>
            Fit.Waits(
              Code.Idle,
              Rationale("idle: nothing for a model to do until its cron fires or new sources arrive")
            )
      else if runnable.isEmpty then
        val ids = (allowed ++ allowed
          .flatMap(blockers(_, f.work)))
          .flatMap(w => w.approval.filter(_ => w.approvalByPerson.approvalByPerson))
          .distinct
          .sorted
        if ids.nonEmpty then
          Fit.Waits(
            Code.Person,
            Rationale(s"waits for a person: ${ids.map(i => s"approval #$i").mkString(", ")}")
          )
        else Fit.Waits(Code.Held, Rationale("its items are held by a person"))
      else if !f.active.active && !f.pinned.pinned && s.plansByPerson.plansByPerson && s.max.value > 0 && s.openPersonPlans.value >= s.max.value &&
        runnable.forall(w => w.kind.value == PlanGoalItem && w.status == ItemStatus.Pending.wire)
      then
        Fit.Waits(
          Code.Plans,
          Rationale(
            s"its next step is a goal plan, and ${counted(s.openPersonPlans.value, "goal plan")} already wait for a person"
          )
        )
      else Fit.Candidate(stage(f, s.minSources))

  /** The open items `w` waits for before it may run ([[ManagerPolicy.waitsFor]]): a proposal waits for its goal's plan,
    * an article's review for the article's filter.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def blockers(w: Work, all: List[Work]): List[Work] = w.kind.value match
    case ProposeArticles => all.filter(_.kind.value == PlanGoalItem)
    case ArticleReview   => all.filter(p => p.kind.value == InferFilter && p.target == w.target)
    case _               => Nil

  /** The root of a goal's tree (a cycle or a missing parent stops the walk). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def root(slug: Goal.Slug, parentOf: Goal.Slug => Option[Goal.Slug]): Goal.Slug =
    @annotation.tailrec
    def go(s: Goal.Slug, seen: Set[Goal.Slug]): Goal.Slug = parentOf(s) match
      case Some(p) if !seen(p) && seen.size < 64 => go(p, seen + p)
      case _                                     => s
    go(slug, Set(slug))

  /** How near `f` is to the goals already chosen: 0 = a sibling (or a sub-goal) of one, 1 = in the same tree, 2 =
    * elsewhere.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def kin(f: Facts, chosen: List[Facts], rootOf: Goal.Slug => Goal.Slug): (Int, Option[Goal.Slug]) =
    chosen.find(c => (c.parent.isDefined && c.parent == f.parent) || f.parent.contains(c.slug)) match
      case Some(c) => (0, Some(c.slug))
      case None    =>
        val r = rootOf(f.slug)
        chosen.find(c => rootOf(c.slug) == r) match
          case Some(c) => (1, Some(c.slug))
          case None    => (2, None)

  private type Key = (Int, Int, Int, Int, String, String)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def key(f: Facts, st: Stage, k: Int): Key =
    (st.rank.value, k, -f.priority.value, -f.sources.value, f.createdAt.value, f.slug.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pickWhy(f: Facts, st: Stage, k: (Int, Option[Goal.Slug]), minSources: Settings.MinSources): Rationale =
    val near = k match
      case (0, Some(s)) if f.parent.contains(s) => List(s"a sub-goal of $s (active)")
      case (0, Some(s)) => List(s"a sibling of $s (active): one parent's sub-goals are finished before another's")
      case (1, Some(s)) => List(s"in the same tree as $s (active)")
      case _            => Nil
    Rationale(
      (List(s"${st.wire}: ${stageWhy(f, st, minSources)}") ++ near ++
        Option.when(f.priority.value != 0)(s"priority ${f.priority}").toList ++
        Option.when(f.createdAt.value.nonEmpty)(s"created ${f.createdAt.value.take(10)}").toList).mkString("; ")
    )

  /** One pass of the focus over every goal. Pins are always active; the goals active before keep their slots while they
    * are candidates (the cap lowered: the worst go back to the queue first); free slots go to the best candidates one
    * at a time (each pick makes its siblings and its tree nearer for the next); then a candidate with articles to
    * review or compile takes the slot of an active goal still gathering sources. Deterministic: the same facts give the
    * same plan.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def select(goals: List[Facts], s: Settings): Plan =
    val bySlug = goals.map(g => g.slug -> g).toMap
    val kids = goals.filter(_.parent.isDefined).groupBy(_.parent.get)
    val rootMemo = collection.mutable.Map.empty[Goal.Slug, Goal.Slug] // memo of the walk; local to this call
    def rootOf(slug: Goal.Slug): Goal.Slug =
      rootMemo.getOrElseUpdate(slug, root(slug, x => bySlug.get(x).flatMap(_.parent)))
    val fits = goals.map(g => g -> fit(g, kids, s))
    val cands = fits.collect { case (g, Fit.Candidate(st)) => (g, st) }
    val stageOf = cands.map((g, st) => g.slug -> st).toMap
    val cap = if s.max.value <= 0 then Int.MaxValue else s.max.value
    def staticKey(g: Facts) = key(g, stageOf(g.slug), 2)
    val pinned = cands.filter(_._1.pinned.pinned).map(_._1).sortBy(staticKey)
    val kept0 = cands.filter((g, _) => g.active.active && !g.pinned.pinned).map(_._1).sortBy(staticKey)
    val room0 = math.max(0, cap - pinned.size)
    val (kept, displaced) = (kept0.take(room0), kept0.drop(room0))
    // free slots, greedily: each pick changes who is near
    @annotation.tailrec
    def fill(
        chosen: List[Facts],
        pool: List[Facts],
        acc: List[(Facts, Rationale)]
    ): (List[Facts], List[(Facts, Rationale)]) =
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
      .filterNot(g => g.pinned.pinned || kept.exists(_.slug == g.slug))
      .filterNot(g => displaced.exists(_.slug == g.slug))
    val (chosen1, fresh1) = fill(pinned ++ kept, pool0, Nil)
    // a goal with articles to review or compile takes the slot of one still gathering (never a pinned one)
    @annotation.tailrec
    def preempt(
        chosen: List[Facts],
        fresh: List[(Facts, Rationale)],
        swaps: List[(Goal.Slug, Rationale)]
    ): (List[Facts], List[(Facts, Rationale)], List[(Goal.Slug, Rationale)]) =
      val pool = cands.map(_._1).filterNot(g => chosen.exists(_.slug == g.slug))
      val urgent = pool.filter(g => stageOf(g.slug).rank <= Stage.Compiling.rank)
      val victims = chosen.filter(g => !g.pinned.pinned && stageOf(g.slug) == Stage.Gathering)
      if urgent.isEmpty || victims.isEmpty then (chosen, fresh, swaps)
      else
        val rest = chosen.filterNot(g => victims.exists(_.slug == g.slug))
        val in = urgent.minBy(g => key(g, stageOf(g.slug), kin(g, rest, rootOf)._1))
        val out = victims.maxBy(staticKey)
        val left = chosen.filterNot(_.slug == out.slug)
        val why = Rationale(s"${pickWhy(
            in,
            stageOf(in.slug),
            kin(in, left, rootOf),
            s.minSources
          )}; took the slot of ${out.slug} (still gathering)")
        preempt(
          left :+ in,
          fresh.filterNot(_._1.slug == out.slug) :+ (in -> why),
          swaps :+ (out.slug -> Rationale(s"gave its slot to ${in.slug}, which has articles to ${
              if stageOf(in.slug) == Stage.Reviewing then "review" else "compile"
            }"))
        )
    val (chosen, fresh, swaps) = preempt(chosen1, fresh1, Nil)
    val freshWhy = fresh.map((g, w) => g.slug -> w).toMap
    val active = chosen.map { g =>
      val st = stageOf(g.slug)
      val why =
        if g.pinned.pinned then Rationale(s"pinned by a person; ${st.wire}: ${stageWhy(g, st, s.minSources)}")
        else
          freshWhy.getOrElse(
            g.slug,
            Rationale(
              s"active${g.since.fold("")(t => s" since $t")}, kept until it is reviewed; ${st.wire}: ${stageWhy(g, st, s.minSources)}"
            )
          )
      Pick(g.slug, st, why, g.pinned, if g.active.active then g.since else None)
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
    val left = goals.filter(g => g.active.active && !chosen.exists(_.slug == g.slug)).map { g =>
      val why = fits.find(_._1.slug == g.slug).map(_._2) match
        case Some(Fit.Finished)    => Rationale(s"it is ${g.status}")
        case Some(Fit.Waits(_, w)) => w
        case _                     =>
          swapped.getOrElse(
            g.slug,
            if displaced.exists(_.slug == g.slug) then
              Rationale(s"the cap of $cap active goals is full (lowered, or a pin took the slot)")
            else Rationale("back in the queue")
          )
      g.slug -> why
    }
    Plan(active, queue, chosen.filterNot(_.active.active).map(_.slug), left, waiting)

  // ── the items' holds ───────────────────────────────────────────────────────────────────────────

  /** One open item as the holds see it: the goals it belongs to (a seeding manager's or watcher's: its goal; a review
    * item: the goals its article is linked to; none: library work).
    */
  final case class Item(
      id: Work.ItemId,
      kind: ManagerItem.Kind,
      status: ItemStatus.Wire,
      goals: List[Goal.Slug],
      person: Work.Person = Work.Person.ForModel,
      focusHeld: Work.FocusHeld = Work.FocusHeld.Free
  )

  /** What the holds change: items to hold (id, goal, reason) and focus holds to release. */
  final case class Holds(hold: List[(Work.ItemId, Goal.Slug, Rationale)] = Nil, release: List[Work.ItemId] = Nil)

  /** The reason a focus hold carries (it starts with `focus:`; the items window and the status show it). */
  val HoldPrefix = "focus:"

  /** Which items wait: a PENDING model-using item that is not a person's, of an unfinished goal that is not active (or
    * of an active parent whose sub-goals are not finished, apart from its plan) is held; a focus hold whose goal became
    * active (or finished, or only library work is left, or a person asked for it) is released. An item of several goals
    * runs when one of them lets it. Items in flight are never touched.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def holds(items: List[Item], plan: Plan, goals: List[Facts]): Holds =
    val bySlug = goals.map(g => g.slug -> g).toMap
    val openKids = goals.filter(g => g.parent.isDefined && !finished(g.status)).groupBy(_.parent.get)
    val decided = items.map { i =>
      val unfinished = i.goals.filter(g => bySlug.get(g).exists(f => !finished(f.status)))
      val actives = unfinished.filter(plan.activeSet)
      val wait: Option[(Goal.Slug, Rationale)] =
        if !usesModel(i.kind) || unfinished.isEmpty || i.person.person then None
        else if actives.nonEmpty then
          if actives.exists(g => !openKids.contains(g) || i.kind.value == PlanGoalItem) then None
          else
            Some(
              actives.head -> Rationale(
                s"$HoldPrefix goal ${actives.head} waits for its sub-goals before its own ${i.kind}"
              )
            )
        else
          Some(
            unfinished.head -> Rationale(
              s"$HoldPrefix waits until goal ${unfinished.head} is one of the active goals (agents.autopilot.max-active-goals)"
            )
          )
      (i, wait)
    }
    Holds(
      decided.collect {
        case (i, Some((g, why))) if i.status == ItemStatus.Pending.wire && !i.focusHeld.focusHeld => (i.id, g, why)
      },
      decided.collect { case (i, None) if i.focusHeld.focusHeld && i.status == ItemStatus.Held.wire => i.id }
    )

  // ── the library-wide managers ──────────────────────────────────────────────────────────────────

  /** The order the enrich and review managers start their due items in: `ratio` of the active goals' (and a person's)
    * items to one of any other, counting on from the last starts (`recent`, newest first: true = an active goal's). The
    * order inside each class is kept. `ratio` ≤ 0: the active goals' first, the others only after them.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fairOrder[A](cs: List[A], focus: A => Boolean, recent: List[Recent], ratio: Ratio): List[A] =
    val (fs, os) = cs.partition(focus)
    if ratio.value <= 0 || fs.isEmpty || os.isEmpty then fs ++ os
    else
      @annotation.tailrec
      def go(f: List[A], o: List[A], run: Int, acc: List[A]): List[A] =
        (f, o) match
          case (Nil, _)           => acc ++ o
          case (_, Nil)           => acc ++ f
          case (x :: xs, y :: ys) =>
            if run >= ratio.value then go(f, ys, 0, acc :+ y) else go(xs, o, run + 1, acc :+ x)
      go(fs, os, recent.takeWhile(_.focused).size, Nil)

  /** Days until a goal's open items are done at its pace of the last week (None: nothing open or nothing done to
    * measure by).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def etaDays(open: AutopilotPolicy.OpenCount, doneLast7: AutopilotPolicy.DoneLast7): Option[AutopilotPolicy.Days] =
    AutopilotPolicy.etaDays(open, doneLast7)
