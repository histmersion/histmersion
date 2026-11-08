package com.histmersion
package domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}
import io.github.iltotore.iron.constraint.any.Pure

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
object SchedulerPolicy:
  type StartedNote = StartedNote.T
  object StartedNote extends Newtype[String, Pure]
  export SchedulerDefaults.Strategy as StrategyName

  /** A pin is worth this many priority levels. */
  val PinBoost = 1000

  // ── job types ──────────────────────────────────────────────────────────────────────────────────

  /** What kind of work an item is, for the priorities and the shares. */
  enum JobType(val wire: JobType.Wire, val label: JobType.Label) derives CanEqual:
    case GoalPlan extends JobType(JobType.Wire("goal-plan"), JobType.Label("goal plans"))
    case Propose extends JobType(JobType.Wire("propose"), JobType.Label("article proposals"))
    case Sweep extends JobType(JobType.Wire("sweep"), JobType.Label("candidate sweeps and their ranking"))
    case Currency extends JobType(JobType.Wire("currency"), JobType.Label("currency checks"))
    case Enrich extends JobType(JobType.Wire("enrich"), JobType.Label("source enrichment"))
    case Compile extends JobType(JobType.Wire("compile"), JobType.Label("article compiles"))
    case Extend extends JobType(JobType.Wire("extend"), JobType.Label("article extensions"))
    case Filter extends JobType(JobType.Wire("filter"), JobType.Label("coverage filters"))
    case Review extends JobType(JobType.Wire("review"), JobType.Label("reviews and their revisions"))

  object JobType:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]

    def parse(s: Wire): Option[JobType] = values.find(_.wire.value == s.value.trim.toLowerCase(java.util.Locale.ROOT))

    /** The job type of a manager item kind. */
    def of(itemKind: Candidate.Kind): JobType = itemKind.value match
      case PlanGoalItem     => GoalPlan
      case ProposeArticles  => Propose
      case AcquisitionSweep => Sweep
      case CurrencyCheck    => Currency
      case SourceEnrich     => Enrich
      case ArticleCompile   => Compile
      case ArticleExtend    => Extend
      case InferFilter      => Filter
      case _                => Review

  /** `"review=9, compile=8"` → per job type (unknown types and malformed entries dropped). */
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
  def parseWeights(s: String): Map[JobType, Settings.WeightsValue] =
    parseTypeMap(s).view.mapValues(Settings.WeightsValue(_)).toMap

  /** `"review=9, compile=8"` → the priority per job type. */
  def parsePriorities(s: String): Map[JobType, Settings.TypePriorityValue] =
    parseTypeMap(s).view.mapValues(Settings.TypePriorityValue(_)).toMap

  // ── inputs ─────────────────────────────────────────────────────────────────────────────────────

  /** One pending, due manager item. `goal` = the goal it advances (its manager's, or the goal its article or source is
    * linked to; None: library work), `goalPriority` (a pin already added), `goalCreated` (None: library work, after
    * every goal), `gathering` = its goal is still gathering sources; `pools` = the plan pools its run would draw on
    * (none: metered or not known); `block` = why it cannot start whatever the order (a chain below its floor, a budget,
    * a daily cap, an item it waits for); `managerCap` = the manager's own saved `maxRuns`, if a person set one.
    */
  final case class Candidate(
      id: Candidate.Id,
      managerId: Candidate.ManagerId,
      kind: Candidate.Kind,
      target: Candidate.Target,
      goal: Option[Candidate.Goal] = None,
      goalPriority: Candidate.GoalPriority = Candidate.GoalPriority(0),
      goalCreated: Option[Instant] = None,
      gathering: Candidate.Gathering = Candidate.Gathering(false),
      itemPriority: Candidate.ItemPriority = Candidate.ItemPriority(0),
      waitingSince: Instant = Instant.EPOCH,
      pools: List[Candidate.Pools] = Nil,
      block: Option[Block] = None,
      person: Candidate.Person = Candidate.Person(false)
  ):
    def jobType: JobType = JobType.of(kind)
    def label: String = s"#$id $kind $target"

  object Candidate:

    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Goal = Goal.T
    object Goal extends Newtype[String, Pure]
    type GoalPriority = GoalPriority.T
    object GoalPriority extends Newtype[Int, Pure]
    type Gathering = Gathering.T
    object Gathering extends Newtype[Boolean, Pure]
    type ItemPriority = ItemPriority.T
    object ItemPriority extends Newtype[Int, Pure]
    type Pools = Pools.T
    object Pools extends Newtype[String, Pure]
    type Person = Person.T
    object Person extends Newtype[Boolean, Pure]

  /** Why an item cannot start now whatever the order: a code and a sentence. */
  final case class Block(code: Block.Code, why: Block.Why)

  object Block:
    type Code = Code.T
    object Code extends Newtype[String, Pure]
    type Why = Why.T
    object Why extends Newtype[String, Pure]

  /** The [[Block]] code of an item whose role chains are all below their plans' floors. */
  val QuotaBlock: Block.Code = Block.Code("quota")

  /** The plan pools waiting work wants a current reading of: a waiting candidate's own pools, and for one starved below
    * its plans' floors, the pools its role chains draw on (`chainPools`), since a fresh reading may show the window has
    * reset. A candidate blocked for any other reason wants none.
    */
  def poolsWanted(waiting: List[Candidate], chainPools: Candidate => List[Candidate.Pools]): Set[Candidate.Pools] =
    waiting.flatMap { c =>
      c.block match
        case None                            => c.pools
        case Some(b) if b.code == QuotaBlock => chainPools(c)
        case Some(_)                         => Nil
    }.toSet

  /** What is free now: `maxRuns` (`agents.max-concurrent-runs`) and `occupied` (every queued or running agent run, a
    * person's too), each pool's cap in runs and the runs drawing on it, each manager's own cap and its runs, the
    * gathering goals' runs, why nothing may start at all (`stop`: draining, a budget), and the types of the last
    * starts, newest first.
    */
  final case class Resources(
      maxRuns: Resources.MaxRuns,
      occupied: Resources.Occupied,
      poolCap: Map[Candidate.Pools, Resources.PoolCapValue] = Map.empty,
      poolUsed: Map[Candidate.Pools, Resources.PoolUsedValue] = Map.empty,
      managerCap: Map[Candidate.ManagerId, Resources.ManagerCapValue] = Map.empty,
      managerUsed: Map[Candidate.ManagerId, Resources.ManagerUsedValue] = Map.empty,
      gatheringUsed: Resources.GatheringUsed = Resources.GatheringUsed(0),
      stop: Option[Block] = None,
      recent: List[JobType] = Nil
  ):
    def room: Resources.Room = Resources.Room(math.max(0, maxRuns.value - occupied.value))

  object Resources:

    type MaxRuns = MaxRuns.T
    object MaxRuns extends Newtype[Int, Pure]
    type Occupied = Occupied.T
    object Occupied extends Newtype[Int, Pure]
    type Room = Room.T
    object Room extends Newtype[Int, Pure]
    type PoolCapValue = PoolCapValue.T
    object PoolCapValue extends Newtype[Int, Pure]
    type PoolUsedValue = PoolUsedValue.T
    object PoolUsedValue extends Newtype[Int, Pure]
    type ManagerCapValue = ManagerCapValue.T
    object ManagerCapValue extends Newtype[Int, Pure]
    type ManagerUsedValue = ManagerUsedValue.T
    object ManagerUsedValue extends Newtype[Int, Pure]
    type GatheringUsed = GatheringUsed.T
    object GatheringUsed extends Newtype[Int, Pure]

  /** The scheduler's settings ([[AgentSchedulerConfig]] parsed). */
  final case class Settings(
      strategy: Strategy,
      agingHours: Settings.AgingHours = Settings.AgingHours(24),
      maxWaitHours: Settings.MaxWaitHours = Settings.MaxWaitHours(72),
      minShare: Settings.MinShare = Settings.MinShare(0.05),
      weights: Map[JobType, Settings.WeightsValue] = Map.empty,
      typePriority: Map[JobType, Settings.TypePriorityValue] = Map.empty,
      gatheringSlots: Settings.GatheringSlots = Settings.GatheringSlots(1)
  ):
    def weight(t: JobType): Settings.WeightsValue =
      Settings.WeightsValue(math.max(0, weights.get(t).fold(1)(_.value)))
    def priorityOf(t: JobType): Settings.TypePriorityValue =
      typePriority.getOrElse(t, Settings.TypePriorityValue(0))

  object Settings:
    type AgingHours = SchedulerDefaults.AgingHours
    val AgingHours: SchedulerDefaults.AgingHours.type = SchedulerDefaults.AgingHours
    type MaxWaitHours = SchedulerDefaults.MaxWaitHours
    val MaxWaitHours: SchedulerDefaults.MaxWaitHours.type = SchedulerDefaults.MaxWaitHours
    type MinShare = SchedulerDefaults.MinShare
    val MinShare: SchedulerDefaults.MinShare.type = SchedulerDefaults.MinShare
    type WeightsValue = WeightsValue.T
    object WeightsValue extends Newtype[Int, Pure]
    type TypePriorityValue = TypePriorityValue.T
    object TypePriorityValue extends Newtype[Int, Pure]
    type GatheringSlots = SchedulerDefaults.GatheringSlots
    val GatheringSlots: SchedulerDefaults.GatheringSlots.type = SchedulerDefaults.GatheringSlots

  // ── scoring ────────────────────────────────────────────────────────────────────────────────────

  /** A candidate with what the pass computed: the hours it waited, its aging levels, its effective goal priority (the
    * goal's plus aging), and whether it waited past `max-wait-hours`.
    */
  final case class Scored(
      c: Candidate,
      waitedMinutes: Scored.WaitedMinutes,
      aging: Scored.Aging,
      effective: Scored.Effective,
      overdue: Scored.Overdue
  )

  object Scored:
    type WaitedMinutes = WaitedMinutes.T
    object WaitedMinutes extends Newtype[Long, Pure]
    type Aging = Aging.T
    object Aging extends Newtype[Int, Pure]
    type Effective = Effective.T
    object Effective extends Newtype[Int, Pure]
    type Overdue = Overdue.T
    object Overdue extends Newtype[Boolean, Pure]

  def score(c: Candidate, s: Settings, now: Instant): Scored =
    val mins = math.max(0L, Duration.between(c.waitingSince, now).toMinutes)
    val aging = if s.agingHours.value <= 0 then 0 else (mins / (s.agingHours.value.toLong * 60L)).toInt
    Scored(
      c,
      Scored.WaitedMinutes(mins),
      Scored.Aging(aging),
      Scored.Effective(c.goalPriority.value + aging),
      Scored.Overdue(s.maxWaitHours.value > 0 && mins >= s.maxWaitHours.value.toLong * 60L)
    )

  /** The order inside one class of work: effective goal priority (highest first), the goal's age (oldest first; library
    * work after every goal), the job type's priority, the item's, how long it waited, its id.
    */
  def goalKey(x: Scored, s: Settings): (Int, Long, String, Int, Int, Long, Long) =
    (
      -x.effective.value,
      x.c.goalCreated.fold(Long.MaxValue)(_.toEpochMilli),
      x.c.goal.map(_.value).getOrElse("￿"),
      -s.priorityOf(x.c.jobType).value,
      -x.c.itemPriority.value,
      -x.waitedMinutes.value,
      x.c.id.value
    )

  // ── strategies ─────────────────────────────────────────────────────────────────────────────────

  /** What a pass has decided so far (the strategy's view): the starts per type, counted from the recent ones plus this
    * pass's.
    */
  final case class PassState(started: Map[JobType, PassState.StartedValue], admitted: List[Scored])

  object PassState:
    type StartedValue = StartedValue.T
    object StartedValue extends Newtype[Int, Pure]

  /** A strategy picks the next candidate from those that may still start, and says why. The rest of the pass — the
    * resources, aging, max wait, minimum shares, the gathering places and the explanations — is the same for every
    * strategy.
    */
  trait Strategy:
    def name: SchedulerPolicy.StrategyName
    def describe: String
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, String)]

    /** Why `x` stands behind `ahead` (the first candidate before it in the order), in a sentence. */
    def behindWhy(x: Scored, ahead: Scored, s: Settings): String

  private def goalName(x: Scored): String = x.c.goal.fold("library work")(g => s"goal $g")

  private def goalFacts(x: Scored): String =
    s"priority ${x.c.goalPriority}" + (if x.aging.value > 0 then s" +${x.aging} aged" else "") +
      x.c.goalCreated.fold("")(t => s", created ${t.toString.take(10)}")

  /** Depth first: the best goal (effective priority, then the oldest) takes everything it can use, then the next goal;
    * inside a goal the job type's priority.
    */
  object GoalFirst extends Strategy:

    val name = SchedulerPolicy.StrategyName("goal-first")
    val describe =
      "depth first: the highest-priority goals, oldest first, take the allowance; newer goals of the same " +
        "priority wait until the older ones have nothing to run (aging and max-wait bound the wait)"
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, String)] =
      pool.minByOption(goalKey(_, s)).map(x => x -> s"goal-first: ${goalName(x)} (${goalFacts(x)}) is first in line")
    def behindWhy(x: Scored, ahead: Scored, s: Settings): String =
      if ahead.c.goal == x.c.goal then s"goal-first: its goal's ${ahead.c.jobType.label} come first (${ahead.c.label})"
      else s"goal-first: ${goalName(ahead)} (${goalFacts(ahead)}) goes before ${goalName(x)} (${goalFacts(x)})"

  /** Breadth: the next start goes to the job type furthest below its weighted share of the starts so far (stride
    * scheduling over the recent starts and this pass's); inside the type the best goal first.
    */
  object TypeBalanced extends Strategy:

    val name = SchedulerPolicy.StrategyName("type-balanced")
    val describe = "breadth: the allowance is spread over the job types by weight (agents.scheduler.type-weights); " +
      "inside each type the highest-priority goals first, oldest as tiebreak, so every goal advances"

    /** The type whose next start is the most overdue by weight: (starts + 1) / weight, lowest first; a weight of 0 only
      * runs through the minimum share and max wait. Ties: the type's priority.
      */
    def pickType(types: List[JobType], st: PassState, s: Settings): Option[JobType] =
      types
        .filter(s.weight(_).value > 0)
        .minByOption(t =>
          ((st.started.get(t).fold(0)(_.value) + 1).toDouble / s.weight(t).value, -s.priorityOf(t).value, t.ordinal)
        )
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, String)] =
      pickType(pool.map(_.c.jobType).distinct, st, s).flatMap { t =>
        pool
          .filter(_.c.jobType == t)
          .minByOption(goalKey(_, s))
          .map(x =>
            x ->
              (s"type-balanced: ${t.label} are furthest below their share (weight ${s.weight(t)}, " +
                s"${st.started.getOrElse(t, 0)} of the recent starts); inside the type ${goalName(x)} is first (${goalFacts(x)})")
          )
      }
    def behindWhy(x: Scored, ahead: Scored, s: Settings): String =
      if ahead.c.jobType != x.c.jobType then
        s"type-balanced: ${ahead.c.jobType.label} are further below their share than ${x.c.jobType.label}"
      else
        s"type-balanced: inside ${x.c.jobType.label}, ${goalName(ahead)} (${goalFacts(ahead)}) goes before ${goalName(x)} (${goalFacts(x)})"

  /** Every strategy by name; the first is the default when a name does not parse. */
  object Strategies:

    val all: List[Strategy] = List(TypeBalanced, GoalFirst)
    def parse(s: SchedulerPolicy.StrategyName): Option[Strategy] =
      all.find(_.name.value == Option(s).fold("")(_.value).trim.toLowerCase(java.util.Locale.ROOT))
    def names: List[SchedulerPolicy.StrategyName] = all.map(_.name)

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** Why a candidate did not start: a code ([[Code]]), a sentence, the items it waits behind (labels), and its place in
    * the order (1 = next).
    */
  final case class Wait(
      code: Wait.Code,
      why: Wait.Why,
      behind: List[Wait.Behind] = Nil,
      position: Wait.Position = Wait.Position(0)
  )

  object Wait:
    type Code = Block.Code
    val Code: Block.Code.type = Block.Code
    type Why = Block.Why
    val Why: Block.Why.type = Block.Why
    type Behind = Behind.T
    object Behind extends Newtype[String, Pure]
    type Position = Position.T
    object Position extends Newtype[Int, Pure]

  object Code:
    val Blocked: Block.Code = Block.Code("blocked")
    val Stop: Block.Code = Block.Code("stopped")
    val Pool: Block.Code = Block.Code("pool")
    val Manager: Block.Code = Block.Code("manager-cap")
    val Capacity: Block.Code = Block.Code("capacity")
    val Busy: Block.Code = Block.Code("busy")

  /** What one pass decided: the items started (with why they were chosen), the ones that wait (why), and the order the
    * waiting ones would start in.
    */
  final case class Decision(started: List[(Scored, StartedNote)], waiting: List[(Scored, Wait)]):
    def waitOf(id: Candidate.Id): Option[Wait] = waiting.find(_._1.c.id == id).map(_._2)

  /** Does a job type with work need its minimum share now: among the last ⌈1/minShare⌉ starts (the recent ones, newest
    * first, and this pass's) none was of type `t` — and that many starts have been made.
    */
  def shareDue(t: JobType, started: List[JobType], minShare: Settings.MinShare): Boolean =
    minShare.value > 0 && {
      val n = math.ceil(1.0 / math.min(1.0, minShare.value)).toInt
      started.size >= n && !started.take(n).contains(t)
    }

  /** One pass: the candidates in the order the strategy and the anti-starvation rules give, each started (`start`:
    * Right when its run was created; Left why not — another run writes its target, say) while there is room and its
    * pools and its manager have room. Deterministic: the same inputs and the same `start` answers give the same
    * decision.
    */
  def run(cands: List[Candidate], res: Resources, s: Settings, now: Instant)(
      start: Candidate => Either[String, Unit]
  ): Decision =
    val scored = cands.map(score(_, s, now))
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
                      Wait.Why(
                        s"plan pool $p has ${res.poolCap.get(p).fold(0)(_.value)} run(s) drawing on it already (its max-calls-in-flight × " +
                          "agents.scheduler.pool-overcommit)"
                      ),
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
                      Wait.Why(
                        s"its manager ${x.c.managerId} has its own maxRuns ${res.managerCap.get(x.c.managerId).fold(0)(_.value)} in flight"
                      )
                    ))
                  )
                )
              case None =>
                start(x.c) match
                  case Left(no) =>
                    go(a.copy(pool = rest, waits = a.waits :+ (x -> Wait(Code.Busy, Wait.Why(no)))))
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
          val because = ahead.fold("")(a => "; " + s.strategy.behindWhy(x, a, s))
          x -> Wait(
            Code.Capacity,
            Wait.Why(
              s"no place free: ${res.maxRuns} run(s) at once (agents.max-concurrent-runs), " +
                s"${math.min(res.maxRuns.value, res.occupied.value + done.started.size)} occupied$because"
            ),
            behind.map(Wait.Behind(_)),
            Wait.Position(i + 1)
          )
        }
        Decision(done.started.map((x, why) => x -> StartedNote(why)), blockedWaits ++ done.waits ++ capacity)

  /** The labels of this pass's starts on pool `p` (what an item waiting for the pool stands behind). */
  private def inFlightOn(p: Candidate.Pools, started: List[(Scored, String)]): List[Wait.Behind] =
    started.filter(_._1.c.pools.contains(p)).map(x => Wait.Behind(x._1.c.label)).takeRight(3)

  /** The starts per job type among `recent`. */
  private def startsPerType(recent: List[JobType]): Map[JobType, PassState.StartedValue] =
    recent.groupMapReduce(identity)(_ => 1)(_ + _).view.mapValues(PassState.StartedValue(_)).toMap

  /** The next pick: overdue first, then a type's minimum share, then the gathering places, then the strategy. Never
    * empty (`pool` is not).
    */
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
      .filter(shareDue(_, recent, s.minShare))
      .sortBy(t => (-s.priorityOf(t).value, t.ordinal))
    lazy val gatherers = pool.filter(_.c.gathering.value)
    if overdue.nonEmpty then
      val x = overdue.maxBy(x => (x.waitedMinutes.value: Long, -x.c.id.value))
      x -> s"waited ${hours(x.waitedMinutes)} — past agents.scheduler.max-wait-hours (${s.maxWaitHours} h): admitted next"
    else if starved.nonEmpty then
      val t = starved.head
      val x = pool.filter(_.c.jobType == t).minBy(goalKey(_, s))
      x -> s"minimum share: no ${t.label} among the last ${math.ceil(1.0 / math.min(1.0, s.minShare.value)).toInt} starts (agents.scheduler.min-share ${s.minShare})"
    else if s.gatheringSlots.value > gathering && gatherers.nonEmpty then
      val x = s.strategy.next(gatherers, st, s).map(_._1).getOrElse(gatherers.minBy(goalKey(_, s)))
      x -> s"gathering share: goals still gathering their sources keep ${s.gatheringSlots} place(s) (agents.scheduler.gathering-slots); ${goalName(x)}"
    else s.strategy.next(pool, st, s).getOrElse(pool.minBy(goalKey(_, s)) -> "the only kind left (weight 0)")

  /** The order the candidates would start in with unlimited room (resources aside) — for the queue view and for what
    * waits for room. The anti-starvation rules apply as in the pass (the gathering places as if free).
    */
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

  /** `95` minutes → `1 h 35 min`. */
  def hours(minutes: Scored.WaitedMinutes): String =
    val m = minutes.value
    if m < 60 then s"$m min"
    else s"${m / 60} h" + (if m % 60 != 0 then s" ${m % 60} min" else "")

  /** The settings in effect: `strategy` (a person's choice) over the config's; an unknown name is the default. */
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
