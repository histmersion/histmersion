package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

object Autopilot:

  final case class AutopilotSet(on: AutopilotSet.On, by: Option[AutopilotSet.By] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object AutopilotSet:
    type On = On.T
    object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ManagerBrief(id: ManagerBrief.Id, kind: ManagerBrief.Kind, state: ManagerBrief.State, note: Option[ManagerBrief.Note] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ManagerBrief:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = com.histmersion.api.automation.ItemStatus.Wire
    val State: _root_.com.histmersion.api.automation.ItemStatus.Wire.type = _root_.com.histmersion.api.automation.ItemStatus.Wire
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class GoalProgress(
      slug: GoalProgress.Slug,
      title: GoalProgress.Title,
      status: GoalProgress.Status,
      parent: Option[GoalProgress.Parent] = None,
      managers: List[ManagerBrief] = Nil,
      sources: GoalProgress.Sources = _root_.com.histmersion.api.automation.Autopilot.GoalProgress.Sources(0),
      articles: GoalProgress.Articles = _root_.com.histmersion.api.automation.Autopilot.GoalProgress.Articles(0),
      openApprovals: GoalProgress.OpenApprovals = _root_.com.histmersion.api.automation.Autopilot.GoalProgress.OpenApprovals(0),
      lastActivity: Option[GoalProgress.LastActivity] = None,
      doneLast7: GoalProgress.DoneLast7 = _root_.com.histmersion.api.automation.Autopilot.GoalProgress.DoneLast7(0),
      open: GoalProgress.Open = _root_.com.histmersion.api.automation.Autopilot.GoalProgress.Open(0),
      etaDays: Option[GoalProgress.EtaDays] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object GoalProgress:
    type Slug = com.histmersion.api.research.ReviewGoal.Slug
    val Slug: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
    type Title = com.histmersion.api.automation.Goal.Title
    val Title: _root_.com.histmersion.api.automation.Goal.Title.type = _root_.com.histmersion.api.automation.Goal.Title
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Parent = com.histmersion.api.research.ReviewGoal.Slug
    val Parent: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
    type Sources = Sources.T
    object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Articles = Articles.T
    object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenApprovals = OpenApprovals.T
    object OpenApprovals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastActivity = LastActivity.T
    object LastActivity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoneLast7 = DoneLast7.T
    object DoneLast7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Open = Open.T
    object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EtaDays = EtaDays.T
    object EtaDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ApprovalRule(kind: ApprovalRule.Kind, mode: ApprovalRule.Mode, cap: ApprovalRule.Cap = _root_.com.histmersion.api.automation.Autopilot.ApprovalRule.Cap(""))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ApprovalRule:
    type Kind = com.histmersion.api.automation.Approval.Kind.Wire
    val Kind: _root_.com.histmersion.api.automation.Approval.Kind.Wire.type = _root_.com.histmersion.api.automation.Approval.Kind.Wire
    type Mode = com.histmersion.api.automation.Approval.Mode.Wire
    val Mode: _root_.com.histmersion.api.automation.Approval.Mode.Wire.type = _root_.com.histmersion.api.automation.Approval.Mode.Wire
    type Cap = Cap.T
    object Cap extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class FocusGoal(
      slug: FocusGoal.Slug,
      title: FocusGoal.Title = _root_.com.histmersion.api.automation.Goal.Title(""),
      status: FocusGoal.Status = _root_.com.histmersion.api.automation.Autopilot.FocusGoal.Status(""),
      stage: FocusGoal.Stage = _root_.com.histmersion.api.automation.Autopilot.FocusGoal.Stage(""),
      why: FocusGoal.Why = _root_.com.histmersion.api.automation.Autopilot.FocusGoal.Why(""),
      pinned: FocusGoal.Pinned = _root_.com.histmersion.api.automation.Autopilot.FocusGoal.Pinned(false),
      priority: FocusGoal.Priority = _root_.com.histmersion.api.automation.Autopilot.FocusGoal.Priority(0),
      since: Option[FocusGoal.Since] = None,
      doing: Option[FocusGoal.Doing] = None,
      open: FocusGoal.Open = _root_.com.histmersion.api.automation.Autopilot.FocusGoal.Open(0),
      doneLast7: FocusGoal.DoneLast7 = _root_.com.histmersion.api.automation.Autopilot.FocusGoal.DoneLast7(0),
      etaDays: Option[FocusGoal.EtaDays] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object FocusGoal:
    type Slug = com.histmersion.api.research.ReviewGoal.Slug
    val Slug: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
    type Title = com.histmersion.api.automation.Goal.Title
    val Title: _root_.com.histmersion.api.automation.Goal.Title.type = _root_.com.histmersion.api.automation.Goal.Title
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Stage = Stage.T
    object Stage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pinned = Pinned.T
    object Pinned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Doing = Doing.T
    object Doing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Open = Open.T
    object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoneLast7 = DoneLast7.T
    object DoneLast7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EtaDays = EtaDays.T
    object EtaDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class FocusWaiting(slug: FocusWaiting.Slug, code: FocusWaiting.Code, why: FocusWaiting.Why = _root_.com.histmersion.api.automation.Autopilot.FocusWaiting.Why(""), pinned: FocusWaiting.Pinned = _root_.com.histmersion.api.automation.Autopilot.FocusWaiting.Pinned(false))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object FocusWaiting:
    type Slug = com.histmersion.api.research.ReviewGoal.Slug
    val Slug: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pinned = Pinned.T
    object Pinned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class FocusInfo(
      on: FocusInfo.On,
      max: FocusInfo.Max = _root_.com.histmersion.api.automation.Autopilot.FocusInfo.Max(0),
      maxFrom: FocusInfo.MaxFrom = _root_.com.histmersion.api.automation.Autopilot.FocusInfo.MaxFrom("config"),
      active: List[FocusGoal] = Nil,
      queue: List[FocusGoal] = Nil,
      queued: FocusInfo.Queued = _root_.com.histmersion.api.automation.Autopilot.FocusInfo.Queued(0),
      waiting: List[FocusWaiting] = Nil,
      held: FocusInfo.Held = _root_.com.histmersion.api.automation.Autopilot.FocusInfo.Held(0),
      note: Option[FocusInfo.Note] = None,
      changes: List[FocusInfo.Changes] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object FocusInfo:
    type On = On.T
    object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Max = Max.T
    object Max extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxFrom = MaxFrom.T
    object MaxFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queued = Queued.T
    object Queued extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Held = Held.T
    object Held extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Changes = Changes.T
    object Changes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class FocusSet(
      action: FocusSet.Action,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      value: Option[FocusSet.Value] = None,
      by: Option[FocusSet.By] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object FocusSet:

    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Actions = List("pin", "unpin", "priority", "max")

  final case class DrainInfo(
      since: DrainInfo.Since,
      by: DrainInfo.By = _root_.com.histmersion.api.automation.Autopilot.DrainInfo.By(""),
      drained: DrainInfo.Drained = _root_.com.histmersion.api.automation.Autopilot.DrainInfo.Drained(false),
      drainedAt: Option[DrainInfo.DrainedAt] = None,
      inFlight: DrainInfo.InFlight = _root_.com.histmersion.api.automation.Autopilot.DrainInfo.InFlight(0)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object DrainInfo:
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Drained = Drained.T
    object Drained extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainedAt = DrainedAt.T
    object DrainedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InFlight = InFlight.T
    object InFlight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class DrainSet(on: DrainSet.On, by: Option[DrainSet.By] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object DrainSet:
    type On = On.T
    object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  /** The autopilot as the automation knows it: the switches, the approvals' rules, the caps, the goals' progress, the
    * focus and the drain. The page the server answers (`AutopilotStatus`) adds what the library knows of its upstreams
    * and of the acquisition list's re-check.
    */
  final case class EngineStatus(
      on: EngineStatus.On,
      since: Option[EngineStatus.Since] = None,
      by: Option[EngineStatus.By] = None,
      agentsEnabled: EngineStatus.AgentsEnabled = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.AgentsEnabled(false),
      managersEnabled: EngineStatus.ManagersEnabled = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.ManagersEnabled(false),
      cronsEnabled: EngineStatus.CronsEnabled = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.CronsEnabled(true),
      approvals: List[ApprovalRule] = Nil,
      ingestedToday: EngineStatus.IngestedToday = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.IngestedToday(0),
      ingestCap: EngineStatus.IngestCap = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.IngestCap(0),
      maxConcurrentRuns: EngineStatus.MaxConcurrentRuns = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.MaxConcurrentRuns(0),
      lastBackup: Option[EngineStatus.LastBackup] = None,
      lastLint: Option[EngineStatus.LastLint] = None,
      library: List[ManagerBrief] = Nil,
      goals: List[GoalProgress] = Nil,
      changes: List[EngineStatus.Changes] = Nil,
      backlog: EngineStatus.Backlog = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.Backlog(0),
      backlogDays: Option[EngineStatus.BacklogDays] = None,
      focus: Option[FocusInfo] = None,
      drain: Option[DrainInfo] = None,
      goalPlansWait: Option[EngineStatus.GoalPlansWait] = None
  )
  object EngineStatus:
    type On = On.T
    object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AgentsEnabled = AgentsEnabled.T
    object AgentsEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagersEnabled = ManagersEnabled.T
    object ManagersEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CronsEnabled = CronsEnabled.T
    object CronsEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IngestedToday = IngestedToday.T
    object IngestedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IngestCap = IngestCap.T
    object IngestCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxConcurrentRuns = MaxConcurrentRuns.T
    object MaxConcurrentRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastBackup = LastBackup.T
    object LastBackup extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastLint = LastLint.T
    object LastLint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Changes = Changes.T
    object Changes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Backlog = Backlog.T
    object Backlog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogDays = BacklogDays.T
    object BacklogDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalPlansWait = GoalPlansWait.T
    object GoalPlansWait extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val CronsOff = "the managers' crons are off (agents.scheduler.enabled = false, HISTMERSION_AGENTS_SCHEDULER): " +
      "each watcher sweeps once and never again, and no seeding manager proposes again — new sources are not looked for"
