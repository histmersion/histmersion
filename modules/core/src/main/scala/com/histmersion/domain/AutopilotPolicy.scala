package com.histmersion
package domain

import com.histmersion.domain.automation.autopilot.PlannedFacts
import com.histmersion.domain.automation.autopilot.Ruling
import com.histmersion.domain.automation.autopilot.GoalPlanFacts
import com.histmersion.domain.automation.autopilot.GoalRow
import com.histmersion.domain.automation.autopilot.ManagerRow
import com.histmersion.domain.automation.autopilot.Pacing
import com.histmersion.domain.automation.autopilot.Stride

import com.histmersion.api.Plural

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Manager
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The pure half of the autopilot (docs/research-process-managers.md "As built: the autopilot"): the code checks that
  * decide an approval without a person, the rule that moves a research goal's status on from the facts, the managers
  * every goal must have, the spread of their crons, when an article is extended with new sources, and when the
  * housekeeping is due. No I/O: the supervisor reads the facts and applies the result.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.AutomationAutopilot)
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
  export Approval.Note
  type OpenCount = OpenCount.T
  object OpenCount extends Newtype[Int, Pure]
  type Proposed = Proposed.T
  object Proposed extends Newtype[Int, Pure]
  type Share = Share.T
  object Share extends Newtype[Double, Pure]
  type Unfinished = Unfinished.T
  object Unfinished extends Newtype[Int, Pure]

  private[domain] def counted(n: Int, singular: String, plural: String = ""): Plural.Phrase =
    Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(singular), Plural.CountPlural(plural))

  // ── auto approvals ─────────────────────────────────────────────────────────────────────────────

  /** What the checks say about one open approval: approve it (why, for the note), reject it (why), or leave it for a
    * person (or for tomorrow, when only a daily cap stood in the way).
    */

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CapTextBase = Base
  val CapTextBase: Base.type = Base
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CapTextBacklog = Backlog
  val CapTextBacklog: Backlog.type = Backlog
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CapTextShare = Share
  val CapTextShare: Share.type = Share
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CapTextCeiling = Cap
  val CapTextCeiling: Cap.type = Cap
  type NotePrefix = NotePrefixValue
  type NotePrefixValue = NotePrefixValue.T
  object NotePrefixValue extends Newtype[String, Pure]

  /** Why the autopilot decided as it did: the narration of a decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  /** An `article-plan` (propose-articles): approved when every article's slug is still free and its filter returns at
    * least `minSources`, and the area's planned articles approved today stay within `cap`; a plan larger than the cap
    * is a person's.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def articlePlan(
      articles: List[PlannedFacts],
      minSources: MinSources,
      approvedToday: ApprovedToday,
      cap: Cap
  ): Ruling =
    val n = articles.size
    val bad = articles.flatMap { a =>
      if !a.slugFree.value then Some(s"${a.slug}: the slug is taken now")
      else if a.hits.value < minSources.value then
        Some(s"${a.slug}: its filter returns ${a.hits} source(s), fewer than $minSources")
      else None
    }
    if n == 0 then Ruling.Reject(Rationale("the plan names no article"))
    else if bad.nonEmpty then Ruling.Reject(Rationale(s"checks failed — ${bad.mkString("; ")}"))
    else if n > cap.value then
      Ruling.Leave(
        Rationale(s"${counted(n, "article")} exceed the daily cap of $cap for one research area")
      )
    else if approvedToday.value + n > cap.value then
      Ruling.Leave(
        Rationale(
          s"the area's daily cap is reached ($approvedToday of $cap articles approved today); decided tomorrow"
        )
      )
    else
      Ruling.Approve(
        Rationale(
          s"${counted(n, "article")}, every slug free and every filter at least $minSources sources " +
            s"(${articles.map(a => s"${a.slug} ${a.hits}").mkString(", ")}); ${approvedToday.value + n} of $cap today"
        )
      )

  /** A goal plan while the focus is on (docs/research-process-managers.md "As built: the focus"): decided by code only
    * when its goal is one of the active goals and its unfinished sub-goals (`openSubGoals`, every level below it) plus
    * the `proposed` new ones stay within `limit`; else why it waits for a person.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unfinishedPlanned(goals: List[(Goal.Slug, Goal.Status.Wire)], planned: Set[Goal.Slug]): Unfinished =
    Unfinished(goals.count((slug, status) => planned(slug) && !finished(status)))

  /** Why goal plans are not offered now, if they are not: more goals with an approved plan are unfinished than `limit`
    * (0 = no limit). While so, no plan-goal run starts for an unplanned goal and code decides no open goal plan; a
    * person still may.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def scaledCap(base: Base, backlog: Backlog, share: Share, ceiling: Cap): Cap =
    val b = math.max(0, base.value)
    if share.value <= 0 then Cap(b)
    else
      val scaled = math.max(b, math.ceil(math.max(0, backlog.value) * share.value).toInt)
      Cap(if ceiling.value > 0 then math.min(scaled, math.max(b, ceiling.value)) else scaled)

  /** The cap as the status shows it: `5 a day`, or `5 a day, 22 today for a backlog of 64 (at most 50)`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def capText(base: CapTextBase, backlog: CapTextBacklog, share: CapTextShare, ceiling: CapTextCeiling): Description =
    val n = scaledCap(base, backlog, share, ceiling)
    Description(
      if share.value <= 0 then s"$base a day"
      else
        s"$base a day, $n today for a backlog of $backlog" + (if ceiling.value > 0 then s" (at most $ceiling)" else "")
    )

  /** The note an automatic decision carries — what marks it as the autopilot's (and what the daily caps count).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val NotePrefix: NotePrefix = NotePrefixValue(Approval.DecidedBy.AutopilotNote)

  /** How deep a goal is: the number of parents above it (a cycle or a missing parent stops the walk). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def depth(
      slug: research.Slug,
      parentOf: research.Slug => Option[research.Slug]
  ): GoalPlanFacts.Depth =
    @annotation.tailrec
    def go(
        s: research.Slug,
        seen: Set[research.Slug],
        n: Int
    ): GoalPlanFacts.Depth = parentOf(s) match
      case Some(p) if !seen(p) && n < 64 => go(p, seen + p, n + 1)
      case _                             => GoalPlanFacts.Depth(n)
    go(slug, Set(slug), 0)

  /** Finished: reviewed or done — such a goal keeps a watcher (new sources, the currency check), no new seeding
    * manager.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def finished(status: Goal.Status.Wire): Boolean =
    status.value == Goal.Status.Reviewed || status.value == Goal.Status.Done

  /** What must change so every goal has its managers (idempotent: nothing, when they are there). Library-wide: `review`
    * and `enrich` active (`review` with the extend threshold). Every unfinished goal: a seeding manager (a plan-goal
    * item first when it has no plan) and a watcher with auto-ingest on; a finished goal: a watcher. A manager a person
    * paused stays paused — only the ones in `pausedByAutopilot` are resumed. A manager's settings a person chose are
    * kept (only an unset cron, auto-ingest or extend threshold is filled).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ensure(
      goals: List[GoalRow],
      managers: List[ManagerRow],
      pausedByAutopilot: Set[ManagerRow.Id],
      s: Pacing
  ): List[Stride] =
    val byId = managers.map(m => m.id -> m).toMap
    val library = List(ManagerRow.Id(ReviewId), ManagerRow.Id(EnrichId)).flatMap { id =>
      byId.get(id).toList.flatMap { m =>
        Option.when(m.state == Manager.State.Paused.wire)(Stride.Resume(id)).toList ++
          Option
            .when(id.value == ReviewId && m.extendMinNew.isEmpty && s.extendMinNew.value > 0)(
              Stride.ExtendMinNew(id, s.extendMinNew)
            )
            .toList
      }
    }
    val perGoal = goals.sortBy(_.slug.value).flatMap { g =>
      val done = finished(g.status)
      val seed = byId.get(ManagerRow.Id.seed(g.slug)) match
        case None if done => Nil
        case None    => List(Stride.CreateSeed(g.slug, stagger(s.seedCron, ManagerRow.Id.seed(g.slug)), g.needsPlan))
        case Some(m) =>
          Option.when(pausedByAutopilot(m.id) && m.state == Manager.State.Paused.wire)(Stride.Resume(m.id)).toList ++
            Option.when(!done && m.cron.isEmpty)(Stride.SetCron(m.id, stagger(s.seedCron, m.id))).toList ++
            Option
              .when(!done && g.needsPlan.value && !m.planItem.value && m.state != Manager.State.Stopped.wire)(
                Stride.AddPlanItem(m.id, g.slug)
              )
              .toList
      val watchCron = if done then s.finishedWatchCron else s.watchCron
      val watch = byId.get(ManagerRow.Id.watch(g.slug)) match
        case None =>
          List(Stride.CreateWatch(g.slug, stagger(watchCron, ManagerRow.Id.watch(g.slug)), s.watchIngestPerDay))
        case Some(m) =>
          Option.when(pausedByAutopilot(m.id) && m.state == Manager.State.Paused.wire)(Stride.Resume(m.id)).toList ++
            Option.when(m.cron.isEmpty)(Stride.SetCron(m.id, stagger(watchCron, m.id))).toList ++
            Option.when(m.autoIngest.isEmpty)(Stride.AutoIngestOn(m.id, s.watchIngestPerDay)).toList
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

  /** `@daily`, `@weekly` and `@monthly` as five fields at a minute, hour (and weekday or day of the month) of `key`'s
    * own, so dozens of managers with the same cron do not fire in the same minute; any other cron is kept as given.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stagger(base: Cron, key: ManagerRow.Id): Cron =
    val h = ManagerRow.spread(key)
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def extendDue(
      newSources: NewSources,
      min: Pacing.ExtendMinNew,
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sharedIngestLeft(cap: Cap, ingestedToday: IngestedToday): Option[Left] =
    Option.when(cap.value > 0)(Left(math.max(0, cap.value - math.max(0, ingestedToday.value))))

  /** Is a housekeeping job due: never run, or `everyHours` passed since (0 = never). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def due(last: Option[Instant], everyHours: EveryHours, now: Instant): Boolean =
    everyHours.value > 0 && last.forall(l => !l.plus(Duration.ofHours(everyHours.value.toLong)).isAfter(now))

  /** Days until `open` items are done at the pace of the last week (`doneLast7` items); None when nothing is open or
    * nothing was done.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def etaDays(open: OpenCount, doneLast7: DoneLast7): Option[Days] =
    Option.when(open.value > 0 && doneLast7.value > 0)(
      Days(math.max(1, math.ceil(open.value * 7.0 / doneLast7.value).toInt))
    )
