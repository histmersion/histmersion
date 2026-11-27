package com.histmersion
package api.automation

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.lint.{tolerated, LintRule}

object AgentScheduler:
  /** Why the scheduler started, keeps waiting or holds an item: the narration of its decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  final case class StrategyLine(name: StrategyLine.Name, describe: StrategyLine.Describe = StrategyLine.Describe(""))
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object StrategyLine:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Describe = Describe.T
    object Describe extends Newtype[String, Pure]

  final case class Started(
      item: Started.Item,
      manager: Started.Manager,
      kind: Started.Kind,
      jobType: Started.JobType,
      target: Started.Target,
      goal: Option[Goal.Slug] = None,
      why: Rationale = Rationale("")
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Started:
    type Item = Item.T
    object Item extends Newtype[Long, Pure]
    type Manager = Manager.T
    object Manager extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    export JobKinds.Wire as JobType
    type Target = Target.T
    object Target extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  final case class Waiting(
      item: Waiting.Item,
      manager: Waiting.Manager,
      kind: Waiting.Kind,
      jobType: Waiting.JobType,
      target: Waiting.Target,
      goal: Option[Goal.Slug] = None,
      priority: Waiting.Priority = Waiting.Priority(0),
      waitedMinutes: Waiting.WaitedMinutes = Waiting.WaitedMinutes((0).toLong),
      code: Waiting.Code = Waiting.Code(""),
      why: Rationale = Rationale(""),
      behind: List[Waiting.Behind] = Nil,
      position: Waiting.Position = Waiting.Position(0)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Waiting:
    type Item = Item.T
    object Item extends Newtype[Long, Pure]
    type Manager = Manager.T
    object Manager extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    export JobKinds.Wire as JobType
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]
    type WaitedMinutes = WaitedMinutes.T
    object WaitedMinutes extends Newtype[Long, Pure]
    type Code = Code.T
    object Code extends Newtype[String, Pure]
    type Behind = Behind.T
    object Behind extends Newtype[String, Pure]
    type Position = Position.T
    object Position extends Newtype[Int, Pure]

  final case class PoolUse(pool: PoolUse.Pool, used: PoolUse.Used = PoolUse.Used(0), cap: PoolUse.Cap = PoolUse.Cap(0))
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object PoolUse:
    type Pool = Pool.T
    object Pool extends Newtype[String, Pure]
    type Used = Used.T
    object Used extends Newtype[Int, Pure]
    type Cap = Cap.T
    object Cap extends Newtype[Int, Pure]

  final case class TypeLine(
      jobType: TypeLine.JobType,
      label: TypeLine.Label = TypeLine.Label(""),
      weight: TypeLine.Weight = TypeLine.Weight(0),
      priority: TypeLine.Priority = TypeLine.Priority(0),
      recent: TypeLine.Recent = TypeLine.Recent(0),
      waiting: TypeLine.Waiting = TypeLine.Waiting(0)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object TypeLine:
    export JobKinds.Wire as JobType
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Weight = Weight.T
    object Weight extends Newtype[Int, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]
    type Recent = Recent.T
    object Recent extends Newtype[Int, Pure]
    type Waiting = Waiting.T
    object Waiting extends Newtype[Int, Pure]

  final case class SchedulerStatus(
      strategy: StrategyLine.Name,
      strategyFrom: SchedulerStatus.StrategyFrom = SchedulerStatus.StrategyFrom("config"),
      strategies: List[StrategyLine] = Nil,
      at: Option[SchedulerStatus.At] = None,
      dry: SchedulerStatus.Dry = SchedulerStatus.Dry(false),
      maxRuns: SchedulerStatus.MaxRuns = SchedulerStatus.MaxRuns(0),
      occupied: SchedulerStatus.Occupied = SchedulerStatus.Occupied(0),
      pools: List[PoolUse] = Nil,
      types: List[TypeLine] = Nil,
      started: List[Started] = Nil,
      waiting: List[Waiting] = Nil,
      waitingTotal: SchedulerStatus.WaitingTotal = SchedulerStatus.WaitingTotal(0),
      forPerson: SchedulerStatus.ForPerson = SchedulerStatus.ForPerson(0),
      stop: Option[SchedulerStatus.Stop] = None,
      settings: List[SchedulerStatus.Settings] = Nil,
      changes: List[SchedulerStatus.Changes] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object SchedulerStatus:
    type StrategyFrom = StrategyFrom.T
    object StrategyFrom extends Newtype[String, Pure]
    type At = At.T
    object At extends Newtype[String, Pure]
    type Dry = Dry.T
    object Dry extends Newtype[Boolean, Pure]
    type MaxRuns = MaxRuns.T
    object MaxRuns extends Newtype[Int, Pure]
    type Occupied = Occupied.T
    object Occupied extends Newtype[Int, Pure]
    type WaitingTotal = WaitingTotal.T
    object WaitingTotal extends Newtype[Int, Pure]
    type ForPerson = ForPerson.T
    object ForPerson extends Newtype[Int, Pure]
    type Stop = Stop.T
    object Stop extends Newtype[String, Pure]
    type Settings = Settings.T
    object Settings extends Newtype[String, Pure]
    type Changes = Changes.T
    object Changes extends Newtype[String, Pure]

  final case class SchedulerSet(strategy: SchedulerSet.Strategy, by: Option[SchedulerSet.By] = None)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object SchedulerSet:
    type Strategy = Strategy.T
    object Strategy extends Newtype[String, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]

  final case class WhyInfo(
      item: WhyInfo.Item,
      manager: WhyInfo.Manager = WhyInfo.Manager(""),
      kind: WhyInfo.Kind = WhyInfo.Kind(""),
      target: WhyInfo.Target = WhyInfo.Target(""),
      status: WhyInfo.Status = WhyInfo.Status(""),
      code: WhyInfo.Code = WhyInfo.Code(""),
      why: Rationale = Rationale(""),
      behind: List[WhyInfo.Behind] = Nil,
      position: WhyInfo.Position = WhyInfo.Position(0),
      run: Option[WhyInfo.Run] = None,
      approval: Option[WhyInfo.Approval] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object WhyInfo:
    type Item = Item.T
    object Item extends Newtype[Long, Pure]
    type Manager = Manager.T
    object Manager extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type Code = Code.T
    object Code extends Newtype[String, Pure]
    type Behind = Behind.T
    object Behind extends Newtype[String, Pure]
    type Position = Position.T
    object Position extends Newtype[Int, Pure]
    type Run = Run.T
    object Run extends Newtype[String, Pure]
    type Approval = Approval.T
    object Approval extends Newtype[Long, Pure]

  object Code:
    val Person = "person"
    val Held = "held"
    val NotDue = "not-due"
    val Running = "running"
    val Settled = "settled"
    val Paused = "paused"

  /** What a scheduler's wait code means, as the managers' view says it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val waitCodes: Map[Waiting.Code, String] = Map(
    { val refactorTuple14 = "capacity" -> "No free place"; (Waiting.Code(refactorTuple14._1), (refactorTuple14._2)) },
    { val refactorTuple15 = "pool" -> "Plan is full"; (Waiting.Code(refactorTuple15._1), (refactorTuple15._2)) },
    { val refactorTuple3 = "manager-cap" -> "Manager is full"; (Waiting.Code(refactorTuple3._1), (refactorTuple3._2)) },
    { val refactorTuple4 = "blocked" -> "Blocked"; (Waiting.Code(refactorTuple4._1), (refactorTuple4._2)) },
    { val refactorTuple5 = "busy" -> "Busy"; (Waiting.Code(refactorTuple5._1), (refactorTuple5._2)) },
    { val refactorTuple6 = "budget" -> "Spending cap reached"; (Waiting.Code(refactorTuple6._1), (refactorTuple6._2)) }, {
      val refactorTuple7 = "drain" -> "Finishing current work, then pausing";
      (Waiting.Code(refactorTuple7._1), (refactorTuple7._2))
    }, {
      val refactorTuple21 = Code.Person -> "Needs your decision";
      (Waiting.Code(refactorTuple21._1), (refactorTuple21._2))
    },
    { val refactorTuple22 = Code.Held -> "Held by a person"; (Waiting.Code(refactorTuple22._1), (refactorTuple22._2)) },
    { val refactorTuple23 = Code.NotDue -> "Not due yet"; (Waiting.Code(refactorTuple23._1), (refactorTuple23._2)) },
    { val refactorTuple11 = Code.Running -> "Running"; (Waiting.Code(refactorTuple11._1), (refactorTuple11._2)) },
    { val refactorTuple12 = Code.Settled -> "Settled"; (Waiting.Code(refactorTuple12._1), (refactorTuple12._2)) },
    { val refactorTuple13 = Code.Paused -> "Paused"; (Waiting.Code(refactorTuple13._1), (refactorTuple13._2)) }
  )
