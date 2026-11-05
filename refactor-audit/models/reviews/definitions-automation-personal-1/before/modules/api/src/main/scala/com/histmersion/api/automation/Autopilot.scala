package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object Autopilot:

  final case class AutopilotSet(on: Boolean, by: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class ManagerBrief(id: String, kind: String, state: String, note: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class GoalProgress(
      slug: String,
      title: String,
      status: String,
      parent: Option[String] = None,
      managers: List[ManagerBrief] = Nil,
      sources: Int = 0,
      articles: Int = 0,
      openApprovals: Int = 0,
      lastActivity: Option[String] = None,
      doneLast7: Int = 0,
      open: Int = 0,
      etaDays: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class ApprovalRule(kind: String, mode: String, cap: String = "")
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class FocusGoal(
      slug: String,
      title: String = "",
      status: String = "",
      stage: String = "",
      why: String = "",
      pinned: Boolean = false,
      priority: Int = 0,
      since: Option[String] = None,
      doing: Option[String] = None,
      open: Int = 0,
      doneLast7: Int = 0,
      etaDays: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class FocusWaiting(slug: String, code: String, why: String = "", pinned: Boolean = false)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class FocusInfo(
      on: Boolean,
      max: Int = 0,
      maxFrom: String = "config",
      active: List[FocusGoal] = Nil,
      queue: List[FocusGoal] = Nil,
      queued: Int = 0,
      waiting: List[FocusWaiting] = Nil,
      held: Int = 0,
      note: Option[String] = None,
      changes: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class FocusSet(
      action: String,
      goal: Option[String] = None,
      value: Option[Int] = None,
      by: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object FocusSet:

    val Actions = List("pin", "unpin", "priority", "max")

  final case class DrainInfo(
      since: String,
      by: String = "",
      drained: Boolean = false,
      drainedAt: Option[String] = None,
      inFlight: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class DrainSet(on: Boolean, by: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  /** The autopilot as the automation knows it: the switches, the approvals' rules, the caps, the goals' progress, the
    * focus and the drain. The page the server answers (`AutopilotStatus`) adds what the library knows of its upstreams
    * and of the acquisition list's re-check.
    */
  final case class EngineStatus(
      on: Boolean,
      since: Option[String] = None,
      by: Option[String] = None,
      agentsEnabled: Boolean = false,
      managersEnabled: Boolean = false,
      cronsEnabled: Boolean = true,
      approvals: List[ApprovalRule] = Nil,
      ingestedToday: Int = 0,
      ingestCap: Int = 0,
      maxConcurrentRuns: Int = 0,
      lastBackup: Option[String] = None,
      lastLint: Option[String] = None,
      library: List[ManagerBrief] = Nil,
      goals: List[GoalProgress] = Nil,
      changes: List[String] = Nil,
      backlog: Int = 0,
      backlogDays: Option[Int] = None,
      focus: Option[FocusInfo] = None,
      drain: Option[DrainInfo] = None,
      goalPlansWait: Option[String] = None
  )
  object EngineStatus:
    val CronsOff = "the managers' crons are off (agents.scheduler.enabled = false, HISTMERSION_AGENTS_SCHEDULER): " +
      "each watcher sweeps once and never again, and no seeding manager proposes again — new sources are not looked for"
