package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object AgentScheduler:

  final case class StrategyLine(name: String, describe: String = "") derives KindlingsCodecAsObject, KindlingsSchema
  object StrategyLine:
    given Schema[StrategyLine] = summon[KindlingsSchema[StrategyLine]].schema

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

  final case class PoolUse(pool: String, used: Int = 0, cap: Int = 0) derives KindlingsCodecAsObject, KindlingsSchema
  object PoolUse:
    given Schema[PoolUse] = summon[KindlingsSchema[PoolUse]].schema

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

  final case class SchedulerSet(strategy: String, by: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object SchedulerSet:
    given Schema[SchedulerSet] = summon[KindlingsSchema[SchedulerSet]].schema

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

  object Code:
    val Person = "person"
    val Held = "held"
    val NotDue = "not-due"
    val Running = "running"
    val Settled = "settled"
    val Paused = "paused"
