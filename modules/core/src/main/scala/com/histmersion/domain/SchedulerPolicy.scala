package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}

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

  /** A pin is worth this many priority levels. */
  type ParseTypeMapResultPart2 = ParseTypeMapResultPart2.T
  object ParseTypeMapResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseTypeMapS = ParseTypeMapS.T
  object ParseTypeMapS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolsWantedResult = PoolsWantedResult.T
  object PoolsWantedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolsWantedChainPoolsResult = PoolsWantedChainPoolsResult.T
  object PoolsWantedChainPoolsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKeyResultPart1 = GoalKeyResultPart1.T
  object GoalKeyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKeyResultPart2 = GoalKeyResultPart2.T
  object GoalKeyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKeyResultPart3 = GoalKeyResultPart3.T
  object GoalKeyResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKeyResultPart4 = GoalKeyResultPart4.T
  object GoalKeyResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKeyResultPart5 = GoalKeyResultPart5.T
  object GoalKeyResultPart5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKeyResultPart6 = GoalKeyResultPart6.T
  object GoalKeyResultPart6 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalKeyResultPart7 = GoalKeyResultPart7.T
  object GoalKeyResultPart7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalNameResult = GoalNameResult.T
  object GoalNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalFactsResult = GoalFactsResult.T
  object GoalFactsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShareDueResult = ShareDueResult.T
  object ShareDueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShareDueMinShare = ShareDueMinShare.T
  object ShareDueMinShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStartResultPart1 = RunStartResultPart1.T
  object RunStartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InFlightOnResult = InFlightOnResult.T
  object InFlightOnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InFlightOnP = InFlightOnP.T
  object InFlightOnP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InFlightOnStartedPart2 = InFlightOnStartedPart2.T
  object InFlightOnStartedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickResultPart2 = PickResultPart2.T
  object PickResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickGathering = PickGathering.T
  object PickGathering extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderResultPart2 = OrderResultPart2.T
  object OrderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HoursResult = HoursResult.T
  object HoursResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HoursMinutes = HoursMinutes.T
  object HoursMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingsStrategy = SettingsStrategy.T
  object SettingsStrategy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val PinBoost = 1000

  // ── job types ──────────────────────────────────────────────────────────────────────────────────

  /** What kind of work an item is, for the priorities and the shares. */
  enum JobType(val wire: String, val label: String):
    case GoalPlan extends JobType("goal-plan", "goal plans")
    case Propose extends JobType("propose", "article proposals")
    case Sweep extends JobType("sweep", "candidate sweeps and their ranking")
    case Currency extends JobType("currency", "currency checks")
    case Enrich extends JobType("enrich", "source enrichment")
    case Compile extends JobType("compile", "article compiles")
    case Extend extends JobType("extend", "article extensions")
    case Filter extends JobType("filter", "coverage filters")
    case Review extends JobType("review", "reviews and their revisions")

  object JobType:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfItemKind = OfItemKind.T
    object OfItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.domain.SchedulerPolicy.JobType.ParseS): Option[JobType] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

    /** The job type of a manager item kind. */
    def of(itemKind: com.histmersion.domain.SchedulerPolicy.JobType.OfItemKind): JobType = itemKind match
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
  def parseTypeMap(s: com.histmersion.domain.SchedulerPolicy.ParseTypeMapS): Map[JobType, com.histmersion.domain.SchedulerPolicy.ParseTypeMapResultPart2] =
    Option(s)
      .getOrElse("")
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(e =>
        e.split("=", 2) match
          case Array(k, v) => JobType.parse(k).zip(v.trim.toIntOption).toList
          case _           => Nil
      )
      .toMap

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
      goalPriority: Candidate.GoalPriority = 0,
      goalCreated: Option[Instant] = None,
      gathering: Candidate.Gathering = false,
      itemPriority: Candidate.ItemPriority = 0,
      waitingSince: Instant = Instant.EPOCH,
      pools: List[Candidate.Pools] = Nil,
      block: Option[Block] = None,
      person: Candidate.Person = false
  ):
    def jobType: JobType = JobType.of(kind)
    def label: com.histmersion.domain.SchedulerPolicy.Candidate.LabelResult = s"#$id $kind $target"

  object Candidate:
    type LabelResult = LabelResult.T
    object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalPriority = GoalPriority.T
    object GoalPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Gathering = Gathering.T
    object Gathering extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemPriority = ItemPriority.T
    object ItemPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pools = Pools.T
    object Pools extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Person = Person.T
    object Person extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Why an item cannot start now whatever the order: a code and a sentence. */
  final case class Block(code: Block.Code, why: Block.Why)

  object Block:
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The [[Block]] code of an item whose role chains are all below their plans' floors. */
  val QuotaBlock = "quota"

  /** The plan pools waiting work wants a current reading of: a waiting candidate's own pools, and for one starved below
    * its plans' floors, the pools its role chains draw on (`chainPools`), since a fresh reading may show the window has
    * reset. A candidate blocked for any other reason wants none.
    */
  def poolsWanted(waiting: List[Candidate], chainPools: Candidate => List[com.histmersion.domain.SchedulerPolicy.PoolsWantedChainPoolsResult]): Set[com.histmersion.domain.SchedulerPolicy.PoolsWantedResult] =
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
      poolCap: Map[Resources.PoolCapKey, Resources.PoolCapValue] = Map.empty,
      poolUsed: Map[Resources.PoolUsedKey, Resources.PoolUsedValue] = Map.empty,
      managerCap: Map[Resources.ManagerCapKey, Resources.ManagerCapValue] = Map.empty,
      managerUsed: Map[Resources.ManagerUsedKey, Resources.ManagerUsedValue] = Map.empty,
      gatheringUsed: Resources.GatheringUsed = 0,
      stop: Option[Block] = None,
      recent: List[JobType] = Nil
  ):
    def room: com.histmersion.domain.SchedulerPolicy.Resources.RoomResult = math.max(0, maxRuns - occupied)

  object Resources:
    type RoomResult = RoomResult.T
    object RoomResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type MaxRuns = MaxRuns.T
    object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Occupied = Occupied.T
    object Occupied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PoolCapKey = PoolCapKey.T
    object PoolCapKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PoolCapValue = PoolCapValue.T
    object PoolCapValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PoolUsedKey = PoolUsedKey.T
    object PoolUsedKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PoolUsedValue = PoolUsedValue.T
    object PoolUsedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerCapKey = ManagerCapKey.T
    object ManagerCapKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerCapValue = ManagerCapValue.T
    object ManagerCapValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerUsedKey = ManagerUsedKey.T
    object ManagerUsedKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerUsedValue = ManagerUsedValue.T
    object ManagerUsedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GatheringUsed = GatheringUsed.T
    object GatheringUsed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The scheduler's settings ([[AgentSchedulerConfig]] parsed). */
  final case class Settings(
      strategy: Strategy,
      agingHours: Settings.AgingHours = 24,
      maxWaitHours: Settings.MaxWaitHours = 72,
      minShare: Settings.MinShare = 0.05,
      weights: Map[JobType, Settings.WeightsValue] = Map.empty,
      typePriority: Map[JobType, Settings.TypePriorityValue] = Map.empty,
      gatheringSlots: Settings.GatheringSlots = 1
  ):
    def weight(t: JobType): com.histmersion.domain.SchedulerPolicy.Settings.WeightResult = math.max(0, weights.getOrElse(t, 1))
    def priorityOf(t: JobType): com.histmersion.domain.SchedulerPolicy.Settings.PriorityOfResult = typePriority.getOrElse(t, 0)

  object Settings:
    type WeightResult = WeightResult.T
    object WeightResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PriorityOfResult = PriorityOfResult.T
    object PriorityOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type AgingHours = AgingHours.T
    object AgingHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxWaitHours = MaxWaitHours.T
    object MaxWaitHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinShare = MinShare.T
    object MinShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WeightsValue = WeightsValue.T
    object WeightsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TypePriorityValue = TypePriorityValue.T
    object TypePriorityValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GatheringSlots = GatheringSlots.T
    object GatheringSlots extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── scoring ────────────────────────────────────────────────────────────────────────────────────

  /** A candidate with what the pass computed: the hours it waited, its aging levels, its effective goal priority (the
    * goal's plus aging), and whether it waited past `max-wait-hours`.
    */
  final case class Scored(c: Candidate, waitedMinutes: Scored.WaitedMinutes, aging: Scored.Aging, effective: Scored.Effective, overdue: Scored.Overdue)

  object Scored:
    type WaitedMinutes = WaitedMinutes.T
    object WaitedMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Aging = Aging.T
    object Aging extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Effective = Effective.T
    object Effective extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Overdue = Overdue.T
    object Overdue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def score(c: Candidate, s: Settings, now: Instant): Scored =
    val mins = math.max(0L, Duration.between(c.waitingSince, now).toMinutes)
    val aging = if s.agingHours <= 0 then 0 else (mins / (s.agingHours.toLong * 60L)).toInt
    Scored(c, mins, aging, c.goalPriority + aging, s.maxWaitHours > 0 && mins >= s.maxWaitHours.toLong * 60L)

  /** The order inside one class of work: effective goal priority (highest first), the goal's age (oldest first; library
    * work after every goal), the job type's priority, the item's, how long it waited, its id.
    */
  def goalKey(x: Scored, s: Settings): (com.histmersion.domain.SchedulerPolicy.GoalKeyResultPart1, com.histmersion.domain.SchedulerPolicy.GoalKeyResultPart2, com.histmersion.domain.SchedulerPolicy.GoalKeyResultPart3, com.histmersion.domain.SchedulerPolicy.GoalKeyResultPart4, com.histmersion.domain.SchedulerPolicy.GoalKeyResultPart5, com.histmersion.domain.SchedulerPolicy.GoalKeyResultPart6, com.histmersion.domain.SchedulerPolicy.GoalKeyResultPart7) =
    (
      -x.effective,
      x.c.goalCreated.fold(Long.MaxValue)(_.toEpochMilli),
      x.c.goal.getOrElse("￿"),
      -s.priorityOf(x.c.jobType),
      -x.c.itemPriority,
      -x.waitedMinutes,
      x.c.id
    )

  // ── strategies ─────────────────────────────────────────────────────────────────────────────────

  /** What a pass has decided so far (the strategy's view): the starts per type, counted from the recent ones plus this
    * pass's.
    */
  final case class PassState(started: Map[JobType, PassState.StartedValue], admitted: List[Scored])

  object PassState:
    type StartedValue = StartedValue.T
    object StartedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A strategy picks the next candidate from those that may still start, and says why. The rest of the pass — the
    * resources, aging, max wait, minimum shares, the gathering places and the explanations — is the same for every
    * strategy.
    */
  trait Strategy:
    def name: com.histmersion.domain.SchedulerPolicy.Strategy.NameResult
    def describe: com.histmersion.domain.SchedulerPolicy.Strategy.DescribeResult
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, com.histmersion.domain.SchedulerPolicy.Strategy.NextResultPart2)]

    /** Why `x` stands behind `ahead` (the first candidate before it in the order), in a sentence. */
    def behindWhy(x: Scored, ahead: Scored, s: Settings): com.histmersion.domain.SchedulerPolicy.Strategy.BehindWhyResult

  object Strategy:
    type NameResult = NameResult.T
    object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NextResultPart2 = NextResultPart2.T
    object NextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BehindWhyResult = BehindWhyResult.T
    object BehindWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def goalName(x: Scored): com.histmersion.domain.SchedulerPolicy.GoalNameResult = x.c.goal.fold("library work")(g => s"goal $g")

  private def goalFacts(x: Scored): com.histmersion.domain.SchedulerPolicy.GoalFactsResult =
    s"priority ${x.c.goalPriority}" + (if x.aging > 0 then s" +${x.aging} aged" else "") +
      x.c.goalCreated.fold("")(t => s", created ${t.toString.take(10)}")

  /** Depth first: the best goal (effective priority, then the oldest) takes everything it can use, then the next goal;
    * inside a goal the job type's priority.
    */
  object GoalFirst extends Strategy:
    type NextResultPart2 = NextResultPart2.T
    object NextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BehindWhyResult = BehindWhyResult.T
    object BehindWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val name = "goal-first"
    val describe =
      "depth first: the highest-priority goals, oldest first, take the allowance; newer goals of the same " +
        "priority wait until the older ones have nothing to run (aging and max-wait bound the wait)"
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, com.histmersion.domain.SchedulerPolicy.GoalFirst.NextResultPart2)] =
      pool.minByOption(goalKey(_, s)).map(x => x -> s"goal-first: ${goalName(x)} (${goalFacts(x)}) is first in line")
    def behindWhy(x: Scored, ahead: Scored, s: Settings): com.histmersion.domain.SchedulerPolicy.GoalFirst.BehindWhyResult =
      if ahead.c.goal == x.c.goal then s"goal-first: its goal's ${ahead.c.jobType.label} come first (${ahead.c.label})"
      else s"goal-first: ${goalName(ahead)} (${goalFacts(ahead)}) goes before ${goalName(x)} (${goalFacts(x)})"

  /** Breadth: the next start goes to the job type furthest below its weighted share of the starts so far (stride
    * scheduling over the recent starts and this pass's); inside the type the best goal first.
    */
  object TypeBalanced extends Strategy:
    type NextResultPart2 = NextResultPart2.T
    object NextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BehindWhyResult = BehindWhyResult.T
    object BehindWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val name = "type-balanced"
    val describe = "breadth: the allowance is spread over the job types by weight (agents.scheduler.type-weights); " +
      "inside each type the highest-priority goals first, oldest as tiebreak, so every goal advances"

    /** The type whose next start is the most overdue by weight: (starts + 1) / weight, lowest first; a weight of 0 only
      * runs through the minimum share and max wait. Ties: the type's priority.
      */
    def pickType(types: List[JobType], st: PassState, s: Settings): Option[JobType] =
      types
        .filter(s.weight(_) > 0)
        .minByOption(t => ((st.started.getOrElse(t, 0) + 1).toDouble / s.weight(t), -s.priorityOf(t), t.ordinal))
    def next(pool: List[Scored], st: PassState, s: Settings): Option[(Scored, com.histmersion.domain.SchedulerPolicy.TypeBalanced.NextResultPart2)] =
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
    def behindWhy(x: Scored, ahead: Scored, s: Settings): com.histmersion.domain.SchedulerPolicy.TypeBalanced.BehindWhyResult =
      if ahead.c.jobType != x.c.jobType then
        s"type-balanced: ${ahead.c.jobType.label} are further below their share than ${x.c.jobType.label}"
      else
        s"type-balanced: inside ${x.c.jobType.label}, ${goalName(ahead)} (${goalFacts(ahead)}) goes before ${goalName(x)} (${goalFacts(x)})"

  /** Every strategy by name; the first is the default when a name does not parse. */
  object Strategies:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamesResult = NamesResult.T
    object NamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val all: List[Strategy] = List(TypeBalanced, GoalFirst)
    def parse(s: com.histmersion.domain.SchedulerPolicy.Strategies.ParseS): Option[Strategy] =
      all.find(_.name == Option(s).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT))
    def names: List[com.histmersion.domain.SchedulerPolicy.Strategies.NamesResult] = all.map(_.name)

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** Why a candidate did not start: a code ([[Code]]), a sentence, the items it waits behind (labels), and its place in
    * the order (1 = next).
    */
  final case class Wait(code: Wait.Code, why: Wait.Why, behind: List[Wait.Behind] = Nil, position: Wait.Position = 0)

  object Wait:
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Behind = Behind.T
    object Behind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Position = Position.T
    object Position extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Code:
    val Blocked = "blocked"
    val Stop = "stopped"
    val Pool = "pool"
    val Manager = "manager-cap"
    val Capacity = "capacity"
    val Busy = "busy"

  /** What one pass decided: the items started (with why they were chosen), the ones that wait (why), and the order the
    * waiting ones would start in.
    */
  final case class Decision(started: List[(Scored, Decision.StartedPart2)], waiting: List[(Scored, Wait)]):
    def waitOf(id: com.histmersion.domain.SchedulerPolicy.Decision.WaitOfId): Option[Wait] = waiting.find(_._1.c.id == id).map(_._2)

  object Decision:
    type WaitOfId = WaitOfId.T
    object WaitOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type StartedPart2 = StartedPart2.T
    object StartedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Does a job type with work need its minimum share now: among the last ⌈1/minShare⌉ starts (the recent ones, newest
    * first, and this pass's) none was of type `t` — and that many starts have been made.
    */
  def shareDue(t: JobType, started: List[JobType], minShare: com.histmersion.domain.SchedulerPolicy.ShareDueMinShare): com.histmersion.domain.SchedulerPolicy.ShareDueResult =
    minShare > 0 && {
      val n = math.ceil(1.0 / math.min(1.0, minShare)).toInt
      started.size >= n && !started.take(n).contains(t)
    }

  /** One pass: the candidates in the order the strategy and the anti-starvation rules give, each started (`start`:
    * Right when its run was created; Left why not — another run writes its target, say) while there is room and its
    * pools and its manager have room. Deterministic: the same inputs and the same `start` answers give the same
    * decision.
    */
  def run(cands: List[Candidate], res: Resources, s: Settings, now: Instant)(
      start: Candidate => Either[com.histmersion.domain.SchedulerPolicy.RunStartResultPart1, Unit]
  ): Decision =
    val scored = cands.map(score(_, s, now))
    val (blocked, open) = scored.partition(_.c.block.isDefined)
    val blockedWaits = blocked.map(x => x -> Wait(Code.Blocked, x.c.block.get.why))
    res.stop match
      case Some(b) =>
        Decision(
          Nil,
          blockedWaits ++ order(open, res, s).zipWithIndex.map((x, i) => x._1 -> Wait(b.code, b.why, Nil, i + 1))
        )
      case None =>
        // a `var`-free loop: each step picks one, tries it, and carries the counters on
        final case class Acc(
            pool: List[Scored],
            room: Acc.Room,
            poolUsed: Map[Acc.PoolUsedKey, Acc.PoolUsedValue],
            mgrUsed: Map[Acc.MgrUsedKey, Acc.MgrUsedValue],
            gathering: Acc.Gathering,
            recent: List[JobType],
            started: List[(Scored, Acc.StartedPart2)],
            waits: List[(Scored, Wait)]
        )

        object Acc:
          type Room = Room.T
          object Room extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
          type PoolUsedKey = PoolUsedKey.T
          object PoolUsedKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
          type PoolUsedValue = PoolUsedValue.T
          object PoolUsedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
          type MgrUsedKey = MgrUsedKey.T
          object MgrUsedKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
          type MgrUsedValue = MgrUsedValue.T
          object MgrUsedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
          type Gathering = Gathering.T
          object Gathering extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
          type StartedPart2 = StartedPart2.T
          object StartedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
        def poolFull(x: Scored, a: Acc): Option[String] =
          x.c.pools.find(p => res.poolCap.get(p).exists(cap => a.poolUsed.getOrElse(p, 0) >= cap))
        def mgrFull(x: Scored, a: Acc): Boolean =
          res.managerCap.get(x.c.managerId).exists(cap => a.mgrUsed.getOrElse(x.c.managerId, 0) >= cap)
        @annotation.tailrec
        def go(a: Acc): Acc =
          if a.room <= 0 || a.pool.isEmpty then a
          else
            val st = PassState(a.recent.groupMapReduce(identity)(_ => 1)(_ + _), a.started.map(_._1))
            val (x, why) = pick(a.pool, st, a.recent, a.gathering, s)
            val rest = a.pool.filterNot(_.c.id == x.c.id)
            poolFull(x, a) match
              case Some(p) =>
                go(
                  a.copy(
                    pool = rest,
                    waits = a.waits :+ (x -> Wait(
                      Code.Pool,
                      s"plan pool $p has ${res.poolCap.getOrElse(p, 0)} run(s) drawing on it already (its max-calls-in-flight × " +
                        "agents.scheduler.pool-overcommit)",
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
                      s"its manager ${x.c.managerId} has its own maxRuns ${res.managerCap.getOrElse(x.c.managerId, 0)} in flight"
                    ))
                  )
                )
              case None =>
                start(x.c) match
                  case Left(no) =>
                    go(a.copy(pool = rest, waits = a.waits :+ (x -> Wait(Code.Busy, no))))
                  case Right(_) =>
                    go(
                      Acc(
                        rest,
                        a.room - 1,
                        x.c.pools.foldLeft(a.poolUsed)((m, p) => m.updated(p, m.getOrElse(p, 0) + 1)),
                        a.mgrUsed.updated(x.c.managerId, a.mgrUsed.getOrElse(x.c.managerId, 0) + 1),
                        a.gathering + (if x.c.gathering then 1 else 0),
                        x.c.jobType :: a.recent,
                        a.started :+ (x -> why),
                        a.waits
                      )
                    )
        val done = go(Acc(open, res.room, res.poolUsed, res.managerUsed, res.gatheringUsed, res.recent, Nil, Nil))
        // what is left waits for room: in the order it would start, each behind the one before it
        val left = order(done.pool, res.modify(_.recent).setTo(done.recent), s)
        val heads = done.started.map(_._1)
        val capacity = left.zipWithIndex.map { case ((x, _), i) =>
          val ahead = if i == 0 then heads.lastOption else Some(left(i - 1)._1)
          val behind = (heads.takeRight(2) ++ left.take(i).map(_._1)).takeRight(3).map(_.c.label)
          val because = ahead.fold("")(a => "; " + s.strategy.behindWhy(x, a, s))
          x -> Wait(
            Code.Capacity,
            s"no place free: ${res.maxRuns} run(s) at once (agents.max-concurrent-runs), " +
              s"${math.min(res.maxRuns, res.occupied + done.started.size)} occupied$because",
            behind,
            i + 1
          )
        }
        Decision(done.started, blockedWaits ++ done.waits ++ capacity)

  /** The labels of this pass's starts on pool `p` (what an item waiting for the pool stands behind). */
  private def inFlightOn(p: com.histmersion.domain.SchedulerPolicy.InFlightOnP, started: List[(Scored, com.histmersion.domain.SchedulerPolicy.InFlightOnStartedPart2)]): List[com.histmersion.domain.SchedulerPolicy.InFlightOnResult] =
    started.filter(_._1.c.pools.contains(p)).map(_._1.c.label).takeRight(3)

  /** The next pick: overdue first, then a type's minimum share, then the gathering places, then the strategy. Never
    * empty (`pool` is not).
    */
  private def pick(
      pool: List[Scored],
      st: PassState,
      recent: List[JobType],
      gathering: com.histmersion.domain.SchedulerPolicy.PickGathering,
      s: Settings
  ): (Scored, com.histmersion.domain.SchedulerPolicy.PickResultPart2) =
    val overdue = pool.filter(_.overdue)
    lazy val starved = pool
      .map(_.c.jobType)
      .distinct
      .filter(shareDue(_, recent, s.minShare))
      .sortBy(t => (-s.priorityOf(t), t.ordinal))
    lazy val gatherers = pool.filter(_.c.gathering)
    if overdue.nonEmpty then
      val x = overdue.maxBy(x => (x.waitedMinutes, -x.c.id))
      x -> s"waited ${hours(x.waitedMinutes)} — past agents.scheduler.max-wait-hours (${s.maxWaitHours} h): admitted next"
    else if starved.nonEmpty then
      val t = starved.head
      val x = pool.filter(_.c.jobType == t).minBy(goalKey(_, s))
      x -> s"minimum share: no ${t.label} among the last ${math.ceil(1.0 / math.min(1.0, s.minShare)).toInt} starts (agents.scheduler.min-share ${s.minShare})"
    else if s.gatheringSlots > gathering && gatherers.nonEmpty then
      val x = s.strategy.next(gatherers, st, s).map(_._1).getOrElse(gatherers.minBy(goalKey(_, s)))
      x -> s"gathering share: goals still gathering their sources keep ${s.gatheringSlots} place(s) (agents.scheduler.gathering-slots); ${goalName(x)}"
    else s.strategy.next(pool, st, s).getOrElse(pool.minBy(goalKey(_, s)) -> "the only kind left (weight 0)")

  /** The order the candidates would start in with unlimited room (resources aside) — for the queue view and for what
    * waits for room. The anti-starvation rules apply as in the pass (the gathering places as if free).
    */
  def order(cands: List[Scored], res: Resources, s: Settings): List[(Scored, com.histmersion.domain.SchedulerPolicy.OrderResultPart2)] =
    @annotation.tailrec
    def go(
        pool: List[Scored],
        recent: List[JobType],
        gathering: Int,
        acc: Vector[(Scored, String)]
    ): Vector[(Scored, String)] =
      if pool.isEmpty then acc
      else
        val st = PassState(recent.groupMapReduce(identity)(_ => 1)(_ + _), acc.map(_._1).toList)
        val (x, why) = pick(pool, st, recent, gathering, s)
        go(
          pool.filterNot(_.c.id == x.c.id),
          x.c.jobType :: recent,
          gathering + (if x.c.gathering then 1 else 0),
          acc :+ (x -> why)
        )
    go(cands, res.recent, res.gatheringUsed, Vector.empty).toList

  /** `95` minutes → `1 h 35 min`. */
  def hours(minutes: com.histmersion.domain.SchedulerPolicy.HoursMinutes): com.histmersion.domain.SchedulerPolicy.HoursResult =
    if minutes < 60 then s"$minutes min"
    else s"${minutes / 60} h" + (if minutes % 60 != 0 then s" ${minutes % 60} min" else "")

  /** The settings in effect: `strategy` (a person's choice) over the config's; an unknown name is the default. */
  def settings(d: SchedulerDefaults, strategy: Option[com.histmersion.domain.SchedulerPolicy.SettingsStrategy] = None): Settings =
    Settings(
      strategy.flatMap(Strategies.parse).orElse(Strategies.parse(d.strategy)).getOrElse(Strategies.all.head),
      math.max(0, d.agingHours),
      math.max(0, d.maxWaitHours),
      math.min(1.0, math.max(0.0, d.minShare)),
      parseTypeMap(d.builtInWeights) ++ parseTypeMap(d.typeWeights),
      parseTypeMap(d.builtInPriorities) ++ parseTypeMap(d.typePriorities),
      math.max(0, d.gatheringSlots)
    )
