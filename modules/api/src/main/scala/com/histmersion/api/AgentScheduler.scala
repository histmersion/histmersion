package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The central scheduler's wire types (docs/research-goal-advancement.md "As built"): what `GET /api/agents/scheduler`
  * reports — the strategy, the resources, the last pass's starts, the queue with why each item waits and what it waits
  * behind — the live switch, and one item's explanation. The rules are in `core` ([[SchedulerPolicy]]), the work in the
  * server's supervisor.
  */
object AgentScheduler:

  /** A strategy and what it does. */
  final case class StrategyLine(name: String, describe: String = "") derives KindlingsCodecAsObject, KindlingsSchema
  object StrategyLine:
    given Schema[StrategyLine] = summon[KindlingsSchema[StrategyLine]].schema

  /** An item the last pass started, and why it was chosen. */
  final case class Started(
      item: Long,
      manager: String,
      kind: String,
      jobType: String,
      target: String,
      goal: Option[String] = None,
      why: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Started:
    given Schema[Started] = summon[KindlingsSchema[Started]].schema

  /** An item that waits: its effective priority (goal priority + aging), how long it waited, why (`code`: capacity |
    * pool | manager-cap | blocked | busy | stopped | drain | person …), the items it waits behind, and its place in the
    * order (1 = next; 0 = not in the order: blocked, or waiting for a person).
    */
  final case class Waiting(
      item: Long,
      manager: String,
      kind: String,
      jobType: String,
      target: String,
      goal: Option[String] = None,
      priority: Int = 0,
      waitedMinutes: Long = 0,
      code: String = "",
      why: String = "",
      behind: List[String] = Nil,
      position: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Waiting:
    given Schema[Waiting] = summon[KindlingsSchema[Waiting]].schema

  /** A plan pool: the runs drawing on it now and how many it takes (max-calls-in-flight × pool-overcommit). */
  final case class PoolUse(pool: String, used: Int = 0, cap: Int = 0) derives KindlingsCodecAsObject, KindlingsSchema
  object PoolUse:
    given Schema[PoolUse] = summon[KindlingsSchema[PoolUse]].schema

  /** A job type: its weight and priority, its starts among the recent ones, the items of it waiting. */
  final case class TypeLine(
      jobType: String,
      label: String = "",
      weight: Int = 0,
      priority: Int = 0,
      recent: Int = 0,
      waiting: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object TypeLine:
    given Schema[TypeLine] = summon[KindlingsSchema[TypeLine]].schema

  /** `GET /api/agents/scheduler`. `strategyFrom` = config | person; `at` = when the pass shown ran (`dry` = no pass ran
    * yet: what one would do now, nothing started); `maxRuns`/`occupied` = agents.max-concurrent-runs and the runs
    * queued or running; `waitingTotal` = every item waiting (the list is the first `limit`); `forPerson` = items
    * waiting for a person (an approval or a person's hold), which hold no place; `stop` = why nothing starts at all
    * (draining, a budget); `settings` = the knobs in effect, one line each.
    */
  final case class SchedulerStatus(
      strategy: String,
      strategyFrom: String = "config",
      strategies: List[StrategyLine] = Nil,
      at: Option[String] = None,
      dry: Boolean = false,
      maxRuns: Int = 0,
      occupied: Int = 0,
      pools: List[PoolUse] = Nil,
      types: List[TypeLine] = Nil,
      started: List[Started] = Nil,
      waiting: List[Waiting] = Nil,
      waitingTotal: Int = 0,
      forPerson: Int = 0,
      stop: Option[String] = None,
      settings: List[String] = Nil,
      changes: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object SchedulerStatus:
    given Schema[SchedulerStatus] = summon[KindlingsSchema[SchedulerStatus]].schema

  /** `POST /api/agents/scheduler`: switch the strategy live (`default` = back to agents.scheduler.strategy). */
  final case class SchedulerSet(strategy: String, by: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object SchedulerSet:
    given Schema[SchedulerSet] = summon[KindlingsSchema[SchedulerSet]].schema

  /** `GET /api/agents/scheduler/why/{item}`: one item (by id, or by target among the open items) — its status, why it
    * is where it is, what it waits behind, its place in the order.
    */
  final case class WhyInfo(
      item: Long,
      manager: String = "",
      kind: String = "",
      target: String = "",
      status: String = "",
      code: String = "",
      why: String = "",
      behind: List[String] = Nil,
      position: Int = 0,
      run: Option[String] = None,
      approval: Option[Long] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object WhyInfo:
    given Schema[WhyInfo] = summon[KindlingsSchema[WhyInfo]].schema

  /** The codes a waiting item carries (the pass's, and the ones read from an item that is not a candidate). */
  object Code:
    val Person = "person"
    val Held = "held"
    val NotDue = "not-due"
    val Running = "running"
    val Settled = "settled"
    val Paused = "paused"
