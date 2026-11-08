package com.histmersion
package api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

object Autopilot:

  final case class AutopilotSet(on: AutopilotSet.On, by: Option[AutopilotSet.By] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object AutopilotSet:
    type On = On.T
    object On extends Newtype[Boolean, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]


  final case class ManagerBrief(id: ManagerBrief.Id, kind: ManagerBrief.Kind, state: ManagerBrief.State, note: Option[ManagerBrief.Note] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerBrief:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    export ItemStatus.{Wire as State}
    type Note = Note.T
    object Note extends Newtype[String, Pure]


  final case class GoalProgress(
      slug: GoalProgress.Slug,
      title: GoalProgress.Title,
      status: GoalProgress.Status,
      parent: Option[GoalProgress.Parent] = None,
      managers: List[ManagerBrief] = Nil,
      sources: GoalProgress.Sources = GoalProgress.Sources(0),
      articles: GoalProgress.Articles = GoalProgress.Articles(0),
      openApprovals: GoalProgress.OpenApprovals = GoalProgress.OpenApprovals(0),
      lastActivity: Option[GoalProgress.LastActivity] = None,
      doneLast7: GoalProgress.DoneLast7 = GoalProgress.DoneLast7(0),
      open: GoalProgress.Open = GoalProgress.Open(0),
      etaDays: Option[GoalProgress.EtaDays] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object GoalProgress:
    export com.histmersion.api.research.ReviewGoal.Slug
    export Goal.Title
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    export com.histmersion.api.research.ReviewGoal.{Slug as Parent}
    type Sources = Sources.T
    object Sources extends Newtype[Int, Pure]
    type Articles = Articles.T
    object Articles extends Newtype[Int, Pure]
    type OpenApprovals = OpenApprovals.T
    object OpenApprovals extends Newtype[Int, Pure]
    type LastActivity = LastActivity.T
    object LastActivity extends Newtype[String, Pure]
    type DoneLast7 = DoneLast7.T
    object DoneLast7 extends Newtype[Int, Pure]
    type Open = Open.T
    object Open extends Newtype[Int, Pure]
    type EtaDays = EtaDays.T
    object EtaDays extends Newtype[Int, Pure]


  final case class ApprovalRule(kind: ApprovalRule.Kind, mode: ApprovalRule.Mode, cap: ApprovalRule.Cap = ApprovalRule.Cap(""))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ApprovalRule:
    export Approval.Kind.{Wire as Kind}
    export Approval.Mode.{Wire as Mode}
    type Cap = Cap.T
    object Cap extends Newtype[String, Pure]


  final case class FocusGoal(
      slug: FocusGoal.Slug,
      title: FocusGoal.Title = Goal.Title(""),
      status: FocusGoal.Status = FocusGoal.Status(""),
      stage: FocusGoal.Stage = FocusGoal.Stage(""),
      why: FocusGoal.Why = FocusGoal.Why(""),
      pinned: FocusGoal.Pinned = FocusGoal.Pinned(false),
      priority: FocusGoal.Priority = FocusGoal.Priority(0),
      since: Option[FocusGoal.Since] = None,
      doing: Option[FocusGoal.Doing] = None,
      open: FocusGoal.Open = FocusGoal.Open(0),
      doneLast7: FocusGoal.DoneLast7 = FocusGoal.DoneLast7(0),
      etaDays: Option[FocusGoal.EtaDays] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object FocusGoal:
    export com.histmersion.api.research.ReviewGoal.Slug
    export Goal.Title
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type Stage = Stage.T
    object Stage extends Newtype[String, Pure]
    type Why = Why.T
    object Why extends Newtype[String, Pure]
    type Pinned = Pinned.T
    object Pinned extends Newtype[Boolean, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]
    type Since = Since.T
    object Since extends Newtype[String, Pure]
    type Doing = Doing.T
    object Doing extends Newtype[String, Pure]
    type Open = Open.T
    object Open extends Newtype[Int, Pure]
    type DoneLast7 = DoneLast7.T
    object DoneLast7 extends Newtype[Int, Pure]
    type EtaDays = EtaDays.T
    object EtaDays extends Newtype[Int, Pure]


  final case class FocusWaiting(slug: FocusWaiting.Slug, code: FocusWaiting.Code, why: FocusWaiting.Why = FocusWaiting.Why(""), pinned: FocusWaiting.Pinned = FocusWaiting.Pinned(false))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object FocusWaiting:
    export com.histmersion.api.research.ReviewGoal.Slug
    type Code = Code.T
    object Code extends Newtype[String, Pure]
    type Why = Why.T
    object Why extends Newtype[String, Pure]
    type Pinned = Pinned.T
    object Pinned extends Newtype[Boolean, Pure]


  final case class FocusInfo(
      on: FocusInfo.On,
      max: FocusInfo.Max = FocusInfo.Max(0),
      maxFrom: FocusInfo.MaxFrom = FocusInfo.MaxFrom("config"),
      active: List[FocusGoal] = Nil,
      queue: List[FocusGoal] = Nil,
      queued: FocusInfo.Queued = FocusInfo.Queued(0),
      waiting: List[FocusWaiting] = Nil,
      held: FocusInfo.Held = FocusInfo.Held(0),
      note: Option[FocusInfo.Note] = None,
      changes: List[FocusInfo.Changes] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object FocusInfo:
    type On = On.T
    object On extends Newtype[Boolean, Pure]
    type Max = Max.T
    object Max extends Newtype[Int, Pure]
    type MaxFrom = MaxFrom.T
    object MaxFrom extends Newtype[String, Pure]
    type Queued = Queued.T
    object Queued extends Newtype[Int, Pure]
    type Held = Held.T
    object Held extends Newtype[Int, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]
    type Changes = Changes.T
    object Changes extends Newtype[String, Pure]


  final case class FocusSet(
      action: FocusSet.Action,
      goal: Option[Goal.Slug] = None,
      value: Option[FocusSet.Value] = None,
      by: Option[FocusSet.By] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object FocusSet:

    type Action = Action.T
    object Action extends Newtype[String, Pure]
    type Value = Value.T
    object Value extends Newtype[Int, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]

    val Actions = List("pin", "unpin", "priority", "max")

  final case class DrainInfo(
      since: DrainInfo.Since,
      by: DrainInfo.By = DrainInfo.By(""),
      drained: DrainInfo.Drained = DrainInfo.Drained(false),
      drainedAt: Option[DrainInfo.DrainedAt] = None,
      inFlight: DrainInfo.InFlight = DrainInfo.InFlight(0)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object DrainInfo:
    type Since = Since.T
    object Since extends Newtype[String, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]
    type Drained = Drained.T
    object Drained extends Newtype[Boolean, Pure]
    type DrainedAt = DrainedAt.T
    object DrainedAt extends Newtype[String, Pure]
    type InFlight = InFlight.T
    object InFlight extends Newtype[Int, Pure]


  final case class DrainSet(on: DrainSet.On, by: Option[DrainSet.By] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object DrainSet:
    type On = On.T
    object On extends Newtype[Boolean, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]


  /** The autopilot as the automation knows it: the switches, the approvals' rules, the caps, the goals' progress, the
    * focus and the drain. The page the server answers (`AutopilotStatus`) adds what the library knows of its upstreams
    * and of the acquisition list's re-check.
    */
  final case class EngineStatus(
      on: EngineStatus.On,
      since: Option[EngineStatus.Since] = None,
      by: Option[EngineStatus.By] = None,
      agentsEnabled: EngineStatus.AgentsEnabled = EngineStatus.AgentsEnabled(false),
      managersEnabled: EngineStatus.ManagersEnabled = EngineStatus.ManagersEnabled(false),
      cronsEnabled: EngineStatus.CronsEnabled = EngineStatus.CronsEnabled(true),
      approvals: List[ApprovalRule] = Nil,
      ingestedToday: EngineStatus.IngestedToday = EngineStatus.IngestedToday(0),
      ingestCap: EngineStatus.IngestCap = EngineStatus.IngestCap(0),
      maxConcurrentRuns: EngineStatus.MaxConcurrentRuns = EngineStatus.MaxConcurrentRuns(0),
      lastBackup: Option[EngineStatus.LastBackup] = None,
      lastLint: Option[EngineStatus.LastLint] = None,
      library: List[ManagerBrief] = Nil,
      goals: List[GoalProgress] = Nil,
      changes: List[EngineStatus.Changes] = Nil,
      backlog: EngineStatus.Backlog = EngineStatus.Backlog(0),
      backlogDays: Option[EngineStatus.BacklogDays] = None,
      focus: Option[FocusInfo] = None,
      drain: Option[DrainInfo] = None,
      goalPlansWait: Option[EngineStatus.GoalPlansWait] = None
  )
  object EngineStatus:
    type On = On.T
    object On extends Newtype[Boolean, Pure]
    type Since = Since.T
    object Since extends Newtype[String, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]
    type AgentsEnabled = AgentsEnabled.T
    object AgentsEnabled extends Newtype[Boolean, Pure]
    type ManagersEnabled = ManagersEnabled.T
    object ManagersEnabled extends Newtype[Boolean, Pure]
    type CronsEnabled = CronsEnabled.T
    object CronsEnabled extends Newtype[Boolean, Pure]
    type IngestedToday = IngestedToday.T
    object IngestedToday extends Newtype[Int, Pure]
    type IngestCap = IngestCap.T
    object IngestCap extends Newtype[Int, Pure]
    type MaxConcurrentRuns = MaxConcurrentRuns.T
    object MaxConcurrentRuns extends Newtype[Int, Pure]
    type LastBackup = LastBackup.T
    object LastBackup extends Newtype[String, Pure]
    type LastLint = LastLint.T
    object LastLint extends Newtype[String, Pure]
    type Changes = Changes.T
    object Changes extends Newtype[String, Pure]
    type Backlog = Backlog.T
    object Backlog extends Newtype[Int, Pure]
    type BacklogDays = BacklogDays.T
    object BacklogDays extends Newtype[Int, Pure]
    type GoalPlansWait = GoalPlansWait.T
    object GoalPlansWait extends Newtype[String, Pure]

    val CronsOff = "the managers' crons are off (agents.scheduler.enabled = false, HISTMERSION_AGENTS_SCHEDULER): " +
      "each watcher sweeps once and never again, and no seeding manager proposes again — new sources are not looked for"
