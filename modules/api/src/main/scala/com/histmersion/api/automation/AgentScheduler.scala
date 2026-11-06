package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object AgentScheduler:


  final case class StrategyLine(name: StrategyLine.Name, describe: StrategyLine.Describe = _root_.com.histmersion.api.automation.AgentScheduler.StrategyLine.Describe("")) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

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
      why: Started.Why = _root_.com.histmersion.api.automation.AgentScheduler.Started.Why("")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Started:
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manager = Manager.T
    object Manager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
      priority: Waiting.Priority = _root_.com.histmersion.api.automation.AgentScheduler.Waiting.Priority(0),
      waitedMinutes: Waiting.WaitedMinutes = _root_.com.histmersion.api.automation.AgentScheduler.Waiting.WaitedMinutes((0).toLong),
      code: Waiting.Code = _root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(""),
      why: Waiting.Why = _root_.com.histmersion.api.automation.AgentScheduler.Waiting.Why(""),
      behind: List[Waiting.Behind] = Nil,
      position: Waiting.Position = _root_.com.histmersion.api.automation.AgentScheduler.Waiting.Position(0)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Waiting:
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manager = Manager.T
    object Manager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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


  final case class PoolUse(pool: PoolUse.Pool, used: PoolUse.Used = _root_.com.histmersion.api.automation.AgentScheduler.PoolUse.Used(0), cap: PoolUse.Cap = _root_.com.histmersion.api.automation.AgentScheduler.PoolUse.Cap(0)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object PoolUse:
    type Pool = Pool.T
    object Pool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Used = Used.T
    object Used extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cap = Cap.T
    object Cap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class TypeLine(
      jobType: TypeLine.JobType,
      label: TypeLine.Label = _root_.com.histmersion.api.automation.AgentScheduler.TypeLine.Label(""),
      weight: TypeLine.Weight = _root_.com.histmersion.api.automation.AgentScheduler.TypeLine.Weight(0),
      priority: TypeLine.Priority = _root_.com.histmersion.api.automation.AgentScheduler.TypeLine.Priority(0),
      recent: TypeLine.Recent = _root_.com.histmersion.api.automation.AgentScheduler.TypeLine.Recent(0),
      waiting: TypeLine.Waiting = _root_.com.histmersion.api.automation.AgentScheduler.TypeLine.Waiting(0)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object TypeLine:
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
      strategyFrom: SchedulerStatus.StrategyFrom = _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.StrategyFrom("config"),
      strategies: List[StrategyLine] = Nil,
      at: Option[SchedulerStatus.At] = None,
      dry: SchedulerStatus.Dry = _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.Dry(false),
      maxRuns: SchedulerStatus.MaxRuns = _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.MaxRuns(0),
      occupied: SchedulerStatus.Occupied = _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.Occupied(0),
      pools: List[PoolUse] = Nil,
      types: List[TypeLine] = Nil,
      started: List[Started] = Nil,
      waiting: List[Waiting] = Nil,
      waitingTotal: SchedulerStatus.WaitingTotal = _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.WaitingTotal(0),
      forPerson: SchedulerStatus.ForPerson = _root_.com.histmersion.api.automation.AgentScheduler.SchedulerStatus.ForPerson(0),
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
      manager: WhyInfo.Manager = _root_.com.histmersion.api.automation.AgentScheduler.WhyInfo.Manager(""),
      kind: WhyInfo.Kind = _root_.com.histmersion.api.automation.AgentScheduler.WhyInfo.Kind(""),
      target: WhyInfo.Target = _root_.com.histmersion.api.automation.AgentScheduler.WhyInfo.Target(""),
      status: WhyInfo.Status = _root_.com.histmersion.api.automation.AgentScheduler.WhyInfo.Status(""),
      code: WhyInfo.Code = _root_.com.histmersion.api.automation.AgentScheduler.WhyInfo.Code(""),
      why: WhyInfo.Why = _root_.com.histmersion.api.automation.AgentScheduler.WhyInfo.Why(""),
      behind: List[WhyInfo.Behind] = Nil,
      position: WhyInfo.Position = _root_.com.histmersion.api.automation.AgentScheduler.WhyInfo.Position(0),
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
  val waitCodes: Map[com.histmersion.api.automation.AgentScheduler.Waiting.Code, String] = Map(
    { val refactorTuple14 = "capacity" -> "No free place"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple14._1), (refactorTuple14._2)) },
    { val refactorTuple15 = "pool" -> "Plan is full"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple15._1), (refactorTuple15._2)) },
    { val refactorTuple3 = "manager-cap" -> "Manager is full"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple3._1), (refactorTuple3._2)) },
    { val refactorTuple4 = "blocked" -> "Blocked"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple4._1), (refactorTuple4._2)) },
    { val refactorTuple5 = "busy" -> "Busy"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple5._1), (refactorTuple5._2)) },
    { val refactorTuple6 = "budget" -> "Spending cap reached"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple6._1), (refactorTuple6._2)) },
    { val refactorTuple7 = "drain" -> "Finishing current work, then pausing"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple7._1), (refactorTuple7._2)) },
    { val refactorTuple21 = Code.Person -> "Needs your decision"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple21._1), (refactorTuple21._2)) },
    { val refactorTuple22 = Code.Held -> "Held by a person"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple22._1), (refactorTuple22._2)) },
    { val refactorTuple23 = Code.NotDue -> "Not due yet"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple23._1), (refactorTuple23._2)) },
    { val refactorTuple11 = Code.Running -> "Running"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple11._1), (refactorTuple11._2)) },
    { val refactorTuple12 = Code.Settled -> "Settled"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple12._1), (refactorTuple12._2)) },
    { val refactorTuple13 = Code.Paused -> "Paused"; (_root_.com.histmersion.api.automation.AgentScheduler.Waiting.Code(refactorTuple13._1), (refactorTuple13._2)) }
  )
