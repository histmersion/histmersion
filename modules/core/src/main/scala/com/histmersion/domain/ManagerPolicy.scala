package com.histmersion
package domain

import com.histmersion.domain.automation.managers.CronStep
import com.histmersion.api.automation.managers.ManagerConfig
import com.histmersion.domain.automation.scheduler.Block
import com.histmersion.domain.automation.managers.Assignable
import com.histmersion.domain.automation.managers.ReviewSettings
import com.histmersion.api.automation.managers.Hold
import com.histmersion.api.automation.managers.HintEffect
import com.histmersion.api.automation.managers.ManagerHint

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.Plural
import com.histmersion.api.automation.Manager as ApiManager
import com.histmersion.api.given

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Managers
import com.histmersion.domain.automation.Pipeline
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Goal
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The pure half of the process managers (docs/research-process-managers.md §3.4): what to do with an item after its
  * run settled, the order items are dispatched in, how many runs a manager may start, the backoff, and the hold
  * releases. No I/O: the supervisor reads the facts and writes the result.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.AutomationManagers)
object ManagerPolicy:
  type ActiveAll = ActiveAll.T
  object ActiveAll extends Newtype[Int, Pure]
  type Capacity = Capacity.T
  object Capacity extends Newtype[Int, Pure]
  type DueCount = DueCount.T
  object DueCount extends Newtype[Int, Pure]
  type InFlightCount = InFlightCount.T
  object InFlightCount extends Newtype[Int, Pure]
  type InstantText = InstantText.T
  object InstantText extends Newtype[String, Pure]
  type MaxConcurrent = MaxConcurrent.T
  object MaxConcurrent extends Newtype[Int, Pure]
  type MinIntervalMinutes = MinIntervalMinutes.T
  object MinIntervalMinutes extends Newtype[Int, Pure]
  type MineOpen = MineOpen.T
  object MineOpen extends Newtype[Int, Pure]
  type MineRunning = MineRunning.T
  object MineRunning extends Newtype[Int, Pure]
  type PendingCount = PendingCount.T
  object PendingCount extends Newtype[Int, Pure]
  type PoolCap = PoolCap.T
  object PoolCap extends Newtype[Int, Pure]
  type Quota = Quota.T
  object Quota extends Newtype[Int, Pure]
  type Room = Room.T
  object Room extends Newtype[Int, Pure]
  type StepLabel = StepLabel.T
  object StepLabel extends Newtype[String, Pure]
  export EnrichSource.Tag
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
  type Attempts = Attempts.T
  object Attempts extends Newtype[Int, Pure]
  type ApprovalId = ApprovalId.T
  object ApprovalId extends Newtype[Long, Pure]

  /** A manager's item: its id, what it does, the note it carries, what became of it and the task it left. */
  object Item:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]

    /** What became of a manager's item, as its row and the manager's feed show it. */
    type Outcome = Outcome.T
    object Outcome extends Newtype[String, Pure]

    type Task = Task.T
    object Task extends Newtype[String, Pure]

    /** What an item of a manager does: reviews an article, infers its coverage filter, plans a goal, proposes its
      * articles, sweeps for acquisitions or checks currency.
      */
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]:
      val ArticleReview: Kind = Kind(Managers.ArticleReview)
      val InferFilter: Kind = Kind(Managers.InferFilter)
      val ProposeArticles: Kind = Kind(Managers.ProposeArticles)
      val PlanGoalItem: Kind = Kind(Managers.PlanGoalItem)
      val AcquisitionSweep: Kind = Kind(Managers.AcquisitionSweep)
      val CurrencyCheck: Kind = Kind(Managers.CurrencyCheck)

  /** The count of `n` `singular` (`3 runs`). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[domain] def counted(n: Int, singular: String): Plural.Phrase =
    Plural.count(Plural.Count(n.toLong), Plural.CountSingular(singular))

  /** An active manager's cron at `now`, given when it was due (`nextTick`). A firing missed while the manager was
    * paused (or the server down) fires ONCE when it is next looked at, never once per missed time.
    */
  def cronStep(cron: Option[Cron], nextTick: Option[Instant], now: Instant): CronStep =
    cron match
      case None    => CronStep.Idle
      case Some(c) =>
        nextTick match
          case None                       => CronStep.Schedule(c.next(now))
          case Some(t) if !t.isAfter(now) => CronStep.Fire(c.next(now))
          case _                          => CronStep.Idle

  /** A cron a person sets: it parses, and it fires no more often than every `minIntervalMinutes`
    * (`agents.scheduler.min-interval-minutes`).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def checkCron(text: Cron.Text, now: Instant, minIntervalMinutes: MinIntervalMinutes): Either[Problem, Cron] =
    Cron.parse(text).flatMap { c =>
      c.shortestGapMinutes(now).filter(_.value < minIntervalMinutes.value) match
        case Some(gap) =>
          Left(
            Problem.Invalid.Broken.here(
              Problem.Invalid.Rule
                .CronTooOften(Problem.Raw.of(text), Problem.Count.of(gap), Problem.Count.of(minIntervalMinutes))
            )
          )
        case None => Right(c)
    }

  /** A manager's own daily cap in effect: its `dailyUsd`, else `agents.managers.daily-usd`; 0 = none. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dailyCap(c: ManagerConfig, m: ManagerDefaults): Option[ManagerConfig.DailyUsd] =
    c.dailyUsd.orElse(Some(ManagerConfig.DailyUsd(m.dailyUsd.value))).filter(_.value > 0)

  /** Why a manager starts nothing more today, if it does not: its runs' metered spend this UTC day reached its daily
    * cap. Plan-billed calls cost 0 USD, so this caps metered spend only.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dailyBlock(cap: Option[ManagerConfig.DailyUsd], spentToday: BigDecimal): Option[Block.Why] =
    cap
      .filter(c => spentToday >= BigDecimal(c.value))
      .map(c =>
        Block.Why.Budget(
          Block.SpendCap.Manager(
            Block.Usd(spentToday),
            Block.Usd(BigDecimal(c.value))
          )
        )
      )

  /** The start of `now`'s UTC day (what a daily cap counts from). */
  def dayStart(now: Instant): Instant = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)

  /** How an approval a held item waits for was decided, as its outcome (None: still open). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decided(state: Approval.State.Wire, result: Option[Approval.Outcome]): Option[Approval.Outcome] =
    Approval.State.parse(state).filter(_ != Approval.State.Open).map { st =>
      Approval.Outcome(st.wire.value + result.map(_.value).filter(_.nonEmpty).fold("")(r => s": ${r.take(300)}"))
    }

  /** Must an item of `kind` on `target` wait — an article's review while an infer-filter item for the same article is
    * still open (pending, held or active), a goal's article proposal while its plan-goal item is? `open` = the
    * manager's open items (kind, target).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def waitsFor(kind: Item.Kind, target: Assignable.Target, open: List[(Item.Kind, Assignable.Target)]): Boolean =
    (kind == Item.Kind.ArticleReview && open.contains((Item.Kind.InferFilter, target))) ||
      (kind == Item.Kind.ProposeArticles && open.contains((Item.Kind.PlanGoalItem, target)))

  /** The item kinds whose target is a goal. */
  val GoalKinds: Set[Item.Kind] =
    Set(Item.Kind.PlanGoalItem, Item.Kind.ProposeArticles, Item.Kind.AcquisitionSweep, Item.Kind.CurrencyCheck)

  /** Must an item of `kind` (whose pipeline is `pipeline`) wait for the unsettled runs on its target (their pipelines)?
    * On an article or a source: any of them (one writer at a time). On a goal — its plan, its article proposal, its
    * sweeps — only a run of the same pipeline: they write different things (the plan and queries, an approval, sources
    * and acquisition items), so a plan waiting days for a person does not stop its watcher, nor a sweep the proposal
    * (which waits for the plan through [[waitsFor]]).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def blocks(kind: Item.Kind, pipeline: Pipeline.Wire, unsettled: Seq[Pipeline.Wire]): Boolean =
    if GoalKinds(kind) then unsettled.contains(pipeline) else unsettled.nonEmpty

  /** The dispatch order within one manager: priority (highest first), then the earliest `notBefore` (none first), then
    * the oldest.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def order(cs: List[Assignable]): List[Assignable] =
    cs.sortBy(c => (-c.priority.value, c.notBefore.map(_.toEpochMilli).getOrElse(Long.MinValue), c.id))

  /** How many runs a manager may start now: the global room (`maxConcurrent` minus every agent run queued or running, a
    * person's too), its own (`maxRuns` minus its queued/running; `maxOpen` minus every unsettled one, paused and
    * waiting included), and its pools' (`poolCap`, the calls its models' plan pools take at once). Never negative.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def capacity(
      maxConcurrent: MaxConcurrent,
      activeAll: ActiveAll,
      maxRuns: ReviewSettings.MaxRuns,
      mineRunning: MineRunning,
      maxOpen: ReviewSettings.MaxOpen,
      mineOpen: MineOpen,
      poolCap: Option[PoolCap]
  ): Capacity =
    Capacity(
      List(
        maxConcurrent.value - activeAll.value,
        maxRuns.value - mineRunning.value,
        maxOpen.value - mineOpen.value,
        poolCap.fold(Int.MaxValue)(_.value - mineRunning.value)
      ).min.max(0)
    )

  /** Several managers sharing the global room: one item from each in turn (in the order given), each within its own
    * `cap`, until `room` is used or nothing is left.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def roundRobin[M, A](queues: List[(M, Quota, List[A])], room: Room)(using CanEqual[M, M]): List[(M, A)] =
    @annotation.tailrec
    def go(qs: List[(M, Int, List[A])], left: Int, acc: Vector[(M, A)]): Vector[(M, A)] =
      val live = qs.filter((_, cap, as) => cap > 0 && as.nonEmpty)
      if left <= 0 || live.isEmpty then acc
      else
        val take = live.take(left)
        val picked = take.map((m, _, as) => (m, as.head))
        val rest = qs.map { case q @ (m, cap, as) =>
          if take.exists(_._1 == m) then (m, cap - 1, as.tail) else q
        }
        go(rest, left - take.size, acc ++ picked)
    go(queues.map((m, quota, as) => (m, quota.value, as)), math.max(0, room.value), Vector.empty).toList

  /** Is a hold over: its date passed, or the source it waits for exists. A hold with neither waits for a person's
    * release.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def released(h: Hold, now: Instant, sourceExists: Citekey => Boolean): Boolean =
    h.until
      .flatMap(u => parseInstant(InstantText(u.value)))
      .exists(!_.isAfter(now)) || h.untilSource
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(Citekey.assume(_))
      .exists(sourceExists)

  /** `2026-11-01` (UTC midnight) or an ISO instant. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseInstant(s: InstantText): Option[Instant] =
    val t = s.value.trim
    scala.util
      .Try(Instant.parse(t))
      .toOption
      .orElse(scala.util.Try(java.time.LocalDate.parse(t).atStartOfDay(java.time.ZoneOffset.UTC).toInstant).toOption)

  /** The status line a manager shows; `held` is the spend cap or the starved plan pools that hold all its items. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statusNote(
      state: Manager.State,
      held: Option[Block.Why],
      capacity: Capacity,
      pending: PendingCount,
      due: DueCount,
      inFlight: InFlightCount
  ): ApiManager.Status =
    import ApiManager.Status
    import Status.Kind
    if state == Manager.State.Stopped then Status(Kind.Stopped)
    else if state == Manager.State.Paused then
      Status(Kind.Paused, if inFlight.value > 0 then s" (${counted(inFlight.value, "run")} in flight)" else "")
    else
      held match
        case Some(Block.Why.Budget(cap))    => Status(Kind.Budget, cap.render)
        case Some(Block.Why.Starved(chain)) => Status(Kind.Starved, s"$chain")
        case Some(other)                    => Status.parse(other.render)
        case None                           =>
          if pending.value == 0 && inFlight.value == 0 then Status(Kind.Idle)
          else if due.value > 0 && capacity.value == 0 then
            Status(Kind.AtCapacity, s" (${counted(inFlight.value, "run")} in flight, $pending pending)")
          else if pending.value > 0 then
            Status(
              Kind.Other,
              s"$pending pending" + (if inFlight.value > 0 then s", ${counted(inFlight.value, "run")} in flight"
                                     else "")
            )
          else Status(Kind.Other, s"${counted(inFlight.value, "run")} in flight")

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  /** Does a structured hint effect name this target (by slug, or by one of its tags/topics/goals)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matches(
      e: HintEffect,
      target: Assignable.Target,
      tags: List[Tag],
      topics: List[Topic],
      goals: List[Goal.Slug]
  ): Boolean =
    def eq(a: String, b: String) = a.trim.equalsIgnoreCase(b.trim)
    e.targets.exists(t => eq(t.value, target.value)) || e.tag.exists(t => tags.exists(x => eq(x.value, t.value))) ||
    e.topic.exists(t => topics.exists(x => eq(x.value, t.value))) || e.goal.exists(g =>
      goals.exists(x => eq(x.value, g.value))
    )

  /** The notes a run is given: the manager's live standing hints and unconsumed once-hints, manager-wide or about this
    * item, oldest first.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def notesFor(hints: List[ManagerHint], itemId: Item.Id): List[ManagerHint] =
    hints
      .filter(h => h.retiredAt.isEmpty && h.itemId.forall(_.value == itemId.value) && h.text.value.trim.nonEmpty)
      .sortBy(h => h.id.value: Long)
