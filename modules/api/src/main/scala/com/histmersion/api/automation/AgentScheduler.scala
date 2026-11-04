package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object AgentScheduler:

  final case class StrategyLine(name: String, describe: String = "") derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Started(
      item: Long,
      manager: String,
      kind: String,
      jobType: String,
      target: String,
      goal: Option[String] = None,
      why: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


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
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class PoolUse(pool: String, used: Int = 0, cap: Int = 0) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class TypeLine(
      jobType: String,
      label: String = "",
      weight: Int = 0,
      priority: Int = 0,
      recent: Int = 0,
      waiting: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


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
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class SchedulerSet(strategy: String, by: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


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
        KindlingsSchema, com.histmersion.api.json.Schema


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
