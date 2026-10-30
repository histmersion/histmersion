package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The autopilot's wire types (docs/research-process-managers.md "As built: the autopilot"): the switch (`POST
  * /api/agents/autopilot`), and what `GET /api/agents/autopilot` reports — per research goal its stage, managers,
  * sources and articles, open approvals, last activity and the pace of the last week. The rules are in `core`
  * ([[AutopilotPolicy]]), the work in the server's supervisor.
  */
object Autopilot:

  /** `POST /api/agents/autopilot` — turn it on or off (idempotent); `by` = who (default `person`). */
  final case class AutopilotSet(on: Boolean, by: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object AutopilotSet:
    given Schema[AutopilotSet] = summon[KindlingsSchema[AutopilotSet]].schema

  /** One manager, in a line. */
  final case class ManagerBrief(id: String, kind: String, state: String, note: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerBrief:
    given Schema[ManagerBrief] = summon[KindlingsSchema[ManagerBrief]].schema

  /** One research goal: its status (the stage), its managers, the sources and articles linked to it, the approvals open
    * about it (its plans and its articles' drafts), when one of its managers last did something, the items its managers
    * settled in the last 7 days, the items still open, and the days those would take at that pace (None: nothing open,
    * or nothing done to measure by).
    */
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
        KindlingsSchema
  object GoalProgress:
    given Schema[GoalProgress] = summon[KindlingsSchema[GoalProgress]].schema

  /** How an approval kind is decided now: `person`, or `checked` (the supervisor's code checks). */
  final case class ApprovalRule(kind: String, mode: String, cap: String = "")
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ApprovalRule:
    given Schema[ApprovalRule] = summon[KindlingsSchema[ApprovalRule]].schema

  /** One goal of the focus (docs/research-process-managers.md "As built: the focus"): active — why it was chosen, what
    * it is doing or waiting for, its pace (items settled in 7 days, open, days at that pace) — or queued — why it
    * stands where it does. `stage` = reviewing | compiling | ready | gathering.
    */
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
        KindlingsSchema
  object FocusGoal:
    given Schema[FocusGoal] = summon[KindlingsSchema[FocusGoal]].schema

  /** A goal that cannot use a slot now: `code` = children (its sub-goals first) | person (an approval only a person
    * decides) | held (a person's hold) | stuck (its items stopped) | idle (nothing to do until its cron or new sources) |
    * plans (enough goal plans wait for a person already).
    */
  final case class FocusWaiting(slug: String, code: String, why: String = "", pinned: Boolean = false)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object FocusWaiting:
    given Schema[FocusWaiting] = summon[KindlingsSchema[FocusWaiting]].schema

  /** `GET /api/agents/autopilot/focus`: whether the focus is in force (the autopilot on and a cap above 0), the cap and
    * where it comes from (`config` = agents.autopilot.max-active-goals, `person` = set with `focus max`), the active
    * goals, the next `queue` of the `queued` candidates (in the order they become active), the goals that cannot use a
    * slot now, how many items it holds, and what the last change did.
    */
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
        KindlingsSchema
  object FocusInfo:
    given Schema[FocusInfo] = summon[KindlingsSchema[FocusInfo]].schema

  /** `POST /api/agents/autopilot/focus`: `pin` / `unpin` a goal (a pinned goal is always active), its `priority`
    * (`value`; higher first, after the stage and the tree), or `max` (`value`: the cap, 0 = none; absent: back to
    * agents.autopilot.max-active-goals). `by` = who.
    */
  final case class FocusSet(
      action: String,
      goal: Option[String] = None,
      value: Option[Int] = None,
      by: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object FocusSet:
    given Schema[FocusSet] = summon[KindlingsSchema[FocusSet]].schema
    val Actions = List("pin", "unpin", "priority", "max")

  /** Drain mode (#3762 proposal 4): since when and by whom; `drained` = no agent run is queued or running (safe to stop
    * the server: what waits is checkpointed), since `drainedAt`; `inFlight` = runs queued or running now.
    */
  final case class DrainInfo(
      since: String,
      by: String = "",
      drained: Boolean = false,
      drainedAt: Option[String] = None,
      inFlight: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object DrainInfo:
    given Schema[DrainInfo] = summon[KindlingsSchema[DrainInfo]].schema

  /** `POST /api/agents/autopilot/drain {on}`: start draining (nothing new starts; the runs in flight finish) or lift
    * it.
    */
  final case class DrainSet(on: Boolean, by: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object DrainSet:
    given Schema[DrainSet] = summon[KindlingsSchema[DrainSet]].schema

  /** `GET /api/agents/autopilot`. `since`/`by` = when and by whom it was last switched; `changes` = what the switch
    * just did (an on/off answer only); `ingestedToday` / `ingestCap` = the legal open-access ingests of all watchers
    * today and their shared cap (0 = none); `backlog` / `backlogDays` = the legal open-access works waiting in the
    * watchers' ingest backlogs and the days they take to drain at the current allowance (0 = today; None = they do not
    * drain); the last housekeeping runs; `goalPlansWait` = why no goal plan is offered now (too many unfinished goals),
    * if none is.
    */
  final case class AutopilotStatus(
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
      goalPlansWait: Option[String] = None,
      /** The metered upstreams' budgets today (CORE: the watchers' searches and the ingests' PDF lookups). */
      upstreams: List[UpstreamQuota] = Nil,
      /** The acquisition list's legal re-check (absent from an older server). */
      acquireRecheck: Option[AcquireChecks.RecheckStatus] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object AutopilotStatus:
    given Schema[AutopilotStatus] = summon[KindlingsSchema[AutopilotStatus]].schema

    /** What the status says while `agents.scheduler.enabled` is false. */
    val CronsOff = "the managers' crons are off (agents.scheduler.enabled = false, HISTMERSION_AGENTS_SCHEDULER): " +
      "each watcher sweeps once and never again, and no seeding manager proposes again — new sources are not looked for"
