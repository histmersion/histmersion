package com.histmersion
package domain

import com.histmersion.domain.schedulerpolicy.JobType
import com.histmersion.domain.schedulerpolicy.Settings
import com.histmersion.domain.schedulerpolicy.Candidate
import com.histmersion.domain.schedulerpolicy.Scored
import com.histmersion.domain.schedulerpolicy.Strategy
import com.histmersion.domain.schedulerpolicy.PassState
import com.histmersion.domain.schedulerpolicy.Block
import com.histmersion.domain.schedulerpolicy.Resources
import com.histmersion.domain.schedulerpolicy.Decision
import com.histmersion.domain.schedulerpolicy.Wait

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.ManagersMarkdown

import java.time.{Duration, Instant}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The central agent scheduler, pure (docs/research-goal-advancement.md "As built"): ONE decision per managers' tick
  * about which pending manager items start a run now, across every job type — goal plans, article proposals, candidate
  * sweeps and their ranking, currency checks, source enrichment, compiles, extensions, coverage filters, reviews and
  * their revisions. The managers produce candidates; this admits them. No I/O: the supervisor reads the facts, calls
  * [[run]] with a `start` that dispatches one item, and records the result for the explanations.
  *
  * RESOURCES — the AI allowance: the runs at once (`agents.max-concurrent-runs`, every queued or running run counts, a
  * person's too), each plan pool's calls in flight (`agents.quota.<plan>.max-calls-in-flight` ×
  * `agents.scheduler.pool-overcommit` runs drawing on it), a pool or chain that is below its floor or out of allowance
  * (`block`), the budgets and a manager's daily cap (`block`), and a manager's OWN saved `maxRuns` when a person set
  * one. Only a run that runs or is queued to run occupies a place — a run waiting for a person's approval never does
  * (an item waiting for a person is not a candidate at all).
  *
  * PRIORITY — a goal's priority (a person's, `goal_focus.priority`; a pin counts [[PinBoost]] more), plus the job
  * type's (`agents.scheduler.type-priorities`), plus AGING: one level per `aging-hours` waited.
  *
  * ANTI-STARVATION — in this order before the strategy is asked: an item that waited `max-wait-hours` goes next (the
  * longest waiting first); a job type with work that had no start among the last ⌈1/min-share⌉ starts goes next; goals
  * still gathering their sources get `gathering-slots` places of their own.
  *
  * STRATEGIES ([[Strategy]]): `goal-first` (depth — the highest-priority goals, oldest first, take what they can use; a
  * newer goal of the same priority waits) and `type-balanced` (breadth — the starts are spread over the job types by
  * weight; inside a type the highest-priority goals first). A third is one more [[Strategy]] in [[Strategies]].
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.AutomationScheduler)
object SchedulerPolicy:
  type StartedNote = StartedNote.T
  object StartedNote extends Newtype[String, Pure]
  export SchedulerDefaults.Strategy as StrategyName

  /** A pin is worth this many priority levels. */
  val PinBoost = 1000

  /** `"review=9, compile=8"` → per job type (unknown types and malformed entries dropped). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def parseTypeMap(s: String): Map[JobType, Int] =
    Option(s)
      .getOrElse("")
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(e =>
        e.split("=", 2) match
          case Array(k, v) => JobType.parse(JobType.Wire(k)).zip(v.trim.toIntOption).toList
          case _           => Nil
      )
      .toMap

  /** `"review=9, compile=8"` → the weight per job type. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseWeights(s: String): Map[JobType, Settings.WeightsValue] =
    parseTypeMap(s).view.mapValues(Settings.WeightsValue(_)).toMap

  /** `"review=9, compile=8"` → the priority per job type. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parsePriorities(s: String): Map[JobType, Settings.TypePriorityValue] =
    parseTypeMap(s).view.mapValues(Settings.TypePriorityValue(_)).toMap

  /** The plan pools waiting work wants a current reading of: a waiting candidate's own pools, and for one starved below
    * its plans' floors, the pools its role chains draw on (`chainPools`), since a fresh reading may show the window has
    * reset. A candidate blocked for any other reason wants none.
    */
  def poolsWanted(waiting: List[Candidate], chainPools: Candidate => List[Candidate.Pools]): Set[Candidate.Pools] =
    waiting.flatMap { c =>
      c.block match
        case None                       => c.pools
        case Some(b) if b.why.isStarved => chainPools(c)
        case Some(_)                    => Nil
    }.toSet

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def goalName(x: Scored): String = x.c.goal.fold("library work")(g => s"goal $g")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def goalFacts(x: Scored): String =
    s"priority ${x.c.goalPriority}" + (if x.aging.value > 0 then s" +${x.aging} aged" else "") +
      x.c.goalCreated.fold("")(t => s", created ${t.toString.take(10)}")

  /** Depth first: the best goal (effective priority, then the oldest) takes everything it can use, then the next goal;
    * inside a goal the job type's priority.
    */
  object GoalFirst extends Strategy:

    val name = StrategyName("goal-first")
    val describe =
      "depth first: the highest-priority goals, oldest first, take the allowance; newer goals of the same " +
        "priority wait until the older ones have nothing to run (aging and max-wait bound the wait)"
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, String)] =
      pool.minByOption(_.goalKey(s)).map(x => x -> s"goal-first: ${goalName(x)} (${goalFacts(x)}) is first in line")
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def behindWhy(x: Scored, ahead: Scored, s: Settings): String =
      if ahead.c.goal == x.c.goal then s"goal-first: its goal's ${ahead.c.jobType.label} come first (${ahead.c.label})"
      else s"goal-first: ${goalName(ahead)} (${goalFacts(ahead)}) goes before ${goalName(x)} (${goalFacts(x)})"

  /** Breadth: the next start goes to the job type furthest below its weighted share of the starts so far (stride
    * scheduling over the recent starts and this pass's); inside the type the best goal first.
    */
  object TypeBalanced extends Strategy:

    val name = StrategyName("type-balanced")
    val describe = "breadth: the allowance is spread over the job types by weight (agents.scheduler.type-weights); " +
      "inside each type the highest-priority goals first, oldest as tiebreak, so every goal advances"

    /** The type whose next start is the most overdue by weight: (starts + 1) / weight, lowest first; a weight of 0 only
      * runs through the minimum share and max wait. Ties: the type's priority.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pickType(types: List[JobType], st: PassState, s: Settings): Option[JobType] =
      types
        .filter(s.weight(_).value > 0)
        .minByOption(t =>
          ((st.started.get(t).fold(0)(_.value) + 1).toDouble / s.weight(t).value, -s.priorityOf(t).value, t.ordinal)
        )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, String)] =
      pickType(pool.map(_.c.jobType).distinct, st, s).flatMap { t =>
        pool
          .filter(_.c.jobType == t)
          .minByOption(_.goalKey(s))
          .map(x =>
            x ->
              (s"type-balanced: ${t.label} are furthest below their share (weight ${s.weight(t)}, " +
                s"${st.started.getOrElse(t, 0)} of the recent starts); inside the type ${goalName(x)} is first (${goalFacts(x)})")
          )
      }
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def behindWhy(x: Scored, ahead: Scored, s: Settings): String =
      if ahead.c.jobType != x.c.jobType then
        s"type-balanced: ${ahead.c.jobType.label} are further below their share than ${x.c.jobType.label}"
      else
        s"type-balanced: inside ${x.c.jobType.label}, ${goalName(ahead)} (${goalFacts(ahead)}) goes before ${goalName(x)} (${goalFacts(x)})"

  /** Every strategy by name; the first is the default when a name does not parse. */
  object Strategies:

    val all: List[Strategy] = List(TypeBalanced, GoalFirst)
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: StrategyName): Option[Strategy] =
      all.find(_.name.value == Option(s).fold("")(_.value).trim.toLowerCase(java.util.Locale.ROOT))
    def names: List[StrategyName] = all.map(_.name)

  object Code:
    val Blocked: Block.Code = Block.Code("blocked")
    val Stop: Block.Code = Block.Code("stopped")
    val Pool: Block.Code = Block.Code("pool")
    val Manager: Block.Code = Block.Code("manager-cap")
    val Capacity: Block.Code = Block.Code("capacity")
    val Busy: Block.Code = Block.Code("busy")

  /** One pass: the candidates in the order the strategy and the anti-starvation rules give, each started (`start`:
    * Right when its run was created; Left why not — another run writes its target, say) while there is room and its
    * pools and its manager have room. Deterministic: the same inputs and the same `start` answers give the same
    * decision.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def run(cands: List[Candidate], res: Resources, s: Settings, now: Instant)(
      start: Candidate => Either[Block.Refusal, Unit]
  ): Decision =
    val scored = cands.map(_.score(s, now))
    val (blocked, open) = scored.partition(_.c.block.isDefined)
    val blockedWaits = blocked.map(x => x -> Wait(Code.Blocked, x.c.block.get.why))
    res.stop match
      case Some(b) =>
        Decision(
          Nil,
          blockedWaits ++ order(open, res, s).zipWithIndex.map((x, i) =>
            x._1 -> Wait(b.code, b.why, Nil, Wait.Position(i + 1))
          )
        )
      case None =>
        // a `var`-free loop: each step picks one, tries it, and carries the counters on
        final case class Acc(
            pool: List[Scored],
            room: Int,
            poolUsed: Map[Candidate.Pools, Int],
            mgrUsed: Map[Candidate.ManagerId, Int],
            gathering: Int,
            recent: List[JobType],
            started: List[(Scored, String)],
            waits: List[(Scored, Wait)]
        )

        def poolFull(x: Scored, a: Acc): Option[Candidate.Pools] =
          x.c.pools.find(p => res.poolCap.get(p).exists(cap => a.poolUsed.getOrElse(p, 0) >= cap.value))
        def mgrFull(x: Scored, a: Acc): Boolean =
          res.managerCap.get(x.c.managerId).exists(cap => a.mgrUsed.getOrElse(x.c.managerId, 0) >= cap.value)
        @annotation.tailrec
        def go(a: Acc): Acc =
          if a.room <= 0 || a.pool.isEmpty then a
          else
            val st = PassState(startsPerType(a.recent), a.started.map(_._1))
            val (x, why) = pick(a.pool, st, a.recent, a.gathering, s)
            val rest = a.pool.filterNot(_.c.id == x.c.id)
            poolFull(x, a) match
              case Some(p) =>
                go(
                  a.copy(
                    pool = rest,
                    waits = a.waits :+ (x -> Wait(
                      Code.Pool,
                      Wait.Why.PoolFull(p, res.poolCap.getOrElse(p, Resources.PoolCapValue(0))),
                      inFlightOn(p, a.started)
                    ))
                  )
                )
              case None if mgrFull(x, a) =>
                go(
                  a.copy(
                    pool = rest,
                    waits = a.waits :+ (x -> Wait(
                      Code.Manager,
                      Wait.Why.ManagerFull(
                        x.c.managerId,
                        res.managerCap.getOrElse(x.c.managerId, Resources.ManagerCapValue(0))
                      )
                    ))
                  )
                )
              case None =>
                start(x.c) match
                  case Left(no) =>
                    go(a.copy(pool = rest, waits = a.waits :+ (x -> Wait(Code.Busy, Wait.Why.Busy(no)))))
                  case Right(_) =>
                    go(
                      Acc(
                        rest,
                        a.room - 1,
                        x.c.pools.foldLeft(a.poolUsed)((m, p) => m.updated(p, m.getOrElse(p, 0) + 1)),
                        a.mgrUsed.updated(x.c.managerId, a.mgrUsed.getOrElse(x.c.managerId, 0) + 1),
                        a.gathering + (if x.c.gathering.value then 1 else 0),
                        x.c.jobType :: a.recent,
                        a.started :+ (x -> why),
                        a.waits
                      )
                    )
        val done = go(
          Acc(
            open,
            res.room.value,
            res.poolUsed.view.mapValues(_.value).toMap,
            res.managerUsed.view.mapValues(_.value).toMap,
            res.gatheringUsed.value,
            res.recent,
            Nil,
            Nil
          )
        )
        // what is left waits for room: in the order it would start, each behind the one before it
        val left = order(done.pool, res.modify(_.recent).setTo(done.recent), s)
        val heads = done.started.map(_._1)
        val capacity = left.zipWithIndex.map { case ((x, _), i) =>
          val ahead = if i == 0 then heads.lastOption else Some(left(i - 1)._1)
          val behind = (heads.takeRight(2) ++ left.take(i).map(_._1)).takeRight(3).map(_.c.label)
          val because = ahead.map(a => Block.Because(s.strategy.behindWhy(x, a, s)))
          x -> Wait(
            Code.Capacity,
            Wait.Why.NoPlaceFree(
              res.maxRuns,
              Resources.Occupied(math.min(res.maxRuns.value, res.occupied.value + done.started.size)),
              because
            ),
            behind.map(Wait.Behind(_)),
            Wait.Position(i + 1)
          )
        }
        Decision(done.started.map((x, why) => x -> StartedNote(why)), blockedWaits ++ done.waits ++ capacity)

  /** The labels of this pass's starts on pool `p` (what an item waiting for the pool stands behind). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def inFlightOn(p: Candidate.Pools, started: List[(Scored, String)]): List[Wait.Behind] =
    started.filter(_._1.c.pools.contains(p)).map(x => Wait.Behind(x._1.c.label)).takeRight(3)

  /** The starts per job type among `recent`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def startsPerType(recent: List[JobType]): Map[JobType, PassState.StartedValue] =
    recent.groupMapReduce(identity)(_ => 1)(_ + _).view.mapValues(PassState.StartedValue(_)).toMap

  /** The next pick: overdue first, then a type's minimum share, then the gathering places, then the strategy. Never
    * empty (`pool` is not).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pick(
      pool: List[Scored],
      st: PassState,
      recent: List[JobType],
      gathering: Int,
      s: Settings
  ): (Scored, String) =
    val overdue = pool.filter(_.overdue.value)
    lazy val starved = pool
      .map(_.c.jobType)
      .distinct
      .filter(_.shareDue(recent, s.minShare))
      .sortBy(t => (-s.priorityOf(t).value, t.ordinal))
    lazy val gatherers = pool.filter(_.c.gathering.value)
    if overdue.nonEmpty then
      val x = overdue.maxBy(x => (x.waitedMinutes.value: Long, -x.c.id.value))
      x -> s"waited ${Scored.hours(x.waitedMinutes)} — past agents.scheduler.max-wait-hours (${s.maxWaitHours} h): admitted next"
    else if starved.nonEmpty then
      val t = starved.head
      val x = pool.filter(_.c.jobType == t).minBy(_.goalKey(s))
      x -> s"minimum share: no ${t.label} among the last ${math.ceil(1.0 / math.min(1.0, s.minShare.value)).toInt} starts (agents.scheduler.min-share ${s.minShare})"
    else if s.gatheringSlots.value > gathering && gatherers.nonEmpty then
      val x = s.strategy.next(gatherers, st, s).map(_._1).getOrElse(gatherers.minBy(_.goalKey(s)))
      x -> s"gathering share: goals still gathering their sources keep ${s.gatheringSlots} place(s) (agents.scheduler.gathering-slots); ${goalName(x)}"
    else s.strategy.next(pool, st, s).getOrElse(pool.minBy(_.goalKey(s)) -> "the only kind left (weight 0)")

  /** The order the candidates would start in with unlimited room (resources aside) — for the queue view and for what
    * waits for room. The anti-starvation rules apply as in the pass (the gathering places as if free).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def order(cands: List[Scored], res: Resources, s: Settings): List[(Scored, String)] =
    @annotation.tailrec
    def go(
        pool: List[Scored],
        recent: List[JobType],
        gathering: Int,
        acc: Vector[(Scored, String)]
    ): Vector[(Scored, String)] =
      if pool.isEmpty then acc
      else
        val st = PassState(startsPerType(recent), acc.map(_._1).toList)
        val (x, why) = pick(pool, st, recent, gathering, s)
        go(
          pool.filterNot(_.c.id == x.c.id),
          x.c.jobType :: recent,
          gathering + (if x.c.gathering.value then 1 else 0),
          acc :+ (x -> why)
        )
    go(cands, res.recent, res.gatheringUsed.value, Vector.empty).toList

  /** The settings in effect: `strategy` (a person's choice) over the config's; an unknown name is the default. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def settings(d: SchedulerDefaults, strategy: Option[StrategyName] = None): Settings =
    Settings(
      strategy.flatMap(Strategies.parse).orElse(Strategies.parse(d.strategy)).getOrElse(Strategies.all.head),
      Settings.AgingHours(math.max(0, d.agingHours.value)),
      Settings.MaxWaitHours(math.max(0, d.maxWaitHours.value)),
      Settings.MinShare(math.min(1.0, math.max(0.0, d.minShare.value))),
      parseWeights(d.builtInWeights.value) ++ parseWeights(d.typeWeights.value),
      parsePriorities(d.builtInPriorities.value) ++ parsePriorities(d.typePriorities.value),
      Settings.GatheringSlots(math.max(0, d.gatheringSlots.value))
    )
