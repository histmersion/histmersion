package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object AgentScheduler:

  final case class StrategyLine(name: StrategyLine.Name, describe: StrategyLine.Describe = "") derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object StrategyLine:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Describe = Describe.T
    object Describe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Started(
      item: Started.Item,
      manager: Started.Manager,
      kind: Started.Kind,
      jobType: Started.JobType,
      target: Started.Target,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      why: Started.Why = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Started:
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manager = Manager.T
    object Manager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type JobType = JobType.T
    object JobType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Waiting(
      item: Waiting.Item,
      manager: Waiting.Manager,
      kind: Waiting.Kind,
      jobType: Waiting.JobType,
      target: Waiting.Target,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      priority: Waiting.Priority = 0,
      waitedMinutes: Waiting.WaitedMinutes = 0,
      code: Waiting.Code = "",
      why: Waiting.Why = "",
      behind: List[Waiting.Behind] = Nil,
      position: Waiting.Position = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Waiting:
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manager = Manager.T
    object Manager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type JobType = JobType.T
    object JobType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WaitedMinutes = WaitedMinutes.T
    object WaitedMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Behind = Behind.T
    object Behind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Position = Position.T
    object Position extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class PoolUse(pool: PoolUse.Pool, used: PoolUse.Used = 0, cap: PoolUse.Cap = 0) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object PoolUse:
    type Pool = Pool.T
    object Pool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Used = Used.T
    object Used extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cap = Cap.T
    object Cap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class TypeLine(
      jobType: TypeLine.JobType,
      label: TypeLine.Label = "",
      weight: TypeLine.Weight = 0,
      priority: TypeLine.Priority = 0,
      recent: TypeLine.Recent = 0,
      waiting: TypeLine.Waiting = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object TypeLine:
    type JobType = JobType.T
    object JobType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Weight = Weight.T
    object Weight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Recent = Recent.T
    object Recent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Waiting = Waiting.T
    object Waiting extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class SchedulerStatus(
      strategy: SchedulerStatus.Strategy,
      strategyFrom: SchedulerStatus.StrategyFrom = "config",
      strategies: List[StrategyLine] = Nil,
      at: Option[SchedulerStatus.At] = None,
      dry: SchedulerStatus.Dry = false,
      maxRuns: SchedulerStatus.MaxRuns = 0,
      occupied: SchedulerStatus.Occupied = 0,
      pools: List[PoolUse] = Nil,
      types: List[TypeLine] = Nil,
      started: List[Started] = Nil,
      waiting: List[Waiting] = Nil,
      waitingTotal: SchedulerStatus.WaitingTotal = 0,
      forPerson: SchedulerStatus.ForPerson = 0,
      stop: Option[SchedulerStatus.Stop] = None,
      settings: List[SchedulerStatus.Settings] = Nil,
      changes: List[SchedulerStatus.Changes] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object SchedulerStatus:
    type Strategy = Strategy.T
    object Strategy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StrategyFrom = StrategyFrom.T
    object StrategyFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dry = Dry.T
    object Dry extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxRuns = MaxRuns.T
    object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Occupied = Occupied.T
    object Occupied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WaitingTotal = WaitingTotal.T
    object WaitingTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ForPerson = ForPerson.T
    object ForPerson extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Stop = Stop.T
    object Stop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Settings = Settings.T
    object Settings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Changes = Changes.T
    object Changes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class SchedulerSet(strategy: SchedulerSet.Strategy, by: Option[SchedulerSet.By] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object SchedulerSet:
    type Strategy = Strategy.T
    object Strategy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class WhyInfo(
      item: WhyInfo.Item,
      manager: WhyInfo.Manager = "",
      kind: WhyInfo.Kind = "",
      target: WhyInfo.Target = "",
      status: WhyInfo.Status = "",
      code: WhyInfo.Code = "",
      why: WhyInfo.Why = "",
      behind: List[WhyInfo.Behind] = Nil,
      position: WhyInfo.Position = 0,
      run: Option[WhyInfo.Run] = None,
      approval: Option[WhyInfo.Approval] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object WhyInfo:
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manager = Manager.T
    object Manager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Behind = Behind.T
    object Behind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Position = Position.T
    object Position extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Run = Run.T
    object Run extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Approval = Approval.T
    object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  object Code:
    val Person = "person"
    val Held = "held"
    val NotDue = "not-due"
    val Running = "running"
    val Settled = "settled"
    val Paused = "paused"

  /** What a scheduler's wait code means, as the managers' view says it. */
  val waitCodes: Map[String, String] = Map(
    "capacity" -> "No free place",
    "pool" -> "Plan is full",
    "manager-cap" -> "Manager is full",
    "blocked" -> "Blocked",
    "busy" -> "Busy",
    "budget" -> "Spending cap reached",
    "drain" -> "Finishing current work, then pausing",
    Code.Person -> "Needs your decision",
    Code.Held -> "Held by a person",
    Code.NotDue -> "Not due yet",
    Code.Running -> "Running",
    Code.Settled -> "Settled",
    Code.Paused -> "Paused"
  )
