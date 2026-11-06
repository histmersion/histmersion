package com.histmersion.api
import com.histmersion.api.jobs.JobInfo

import com.histmersion.api.automation.Pipeline
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.Label
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Run
import com.histmersion.api.library.Source

object Wording:

  type AllowanceWindowSpanResult = AllowanceWindowSpanResult.T
  object AllowanceWindowSpanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllowanceWindowSpanMinutes = AllowanceWindowSpanMinutes.T
  object AllowanceWindowSpanMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type HumanizeResult = HumanizeResult.T
  object HumanizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HumanizeId = HumanizeId.T
  object HumanizeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapitalizeResult = CapitalizeResult.T
  object CapitalizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapitalizeS = CapitalizeS.T
  object CapitalizeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueResult = ValueResult.T
  object ValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueS = ValueS.T
  object ValueS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingResult = HeadingResult.T
  object HeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingS = HeadingS.T
  object HeadingS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusResult = StatusResult.T
  object StatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusOfResult = StatusOfResult.T
  object StatusOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusOfWire = StatusOfWire.T
  object StatusOfWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PipelineResult = PipelineResult.T
  object PipelineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalKindResult = ApprovalKindResult.T
  object ApprovalKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStateResult = RunStateResult.T
  object RunStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalStateResult = ApprovalStateResult.T
  object ApprovalStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerKindResult = ManagerKindResult.T
  object ManagerKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerResult = ManagerResult.T
  object ManagerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerId = _root_.com.histmersion.api.Wording.HumanizeId
  val ManagerId: _root_.com.histmersion.api.Wording.HumanizeId.type = _root_.com.histmersion.api.Wording.HumanizeId
  type SchedulerJobTypesPart1 = SchedulerJobTypesPart1.T
  object SchedulerJobTypesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulerJobTypesPart2 = SchedulerJobTypesPart2.T
  object SchedulerJobTypesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategiesPart1 = StrategiesPart1.T
  object StrategiesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategiesPart2 = StrategiesPart2.T
  object StrategiesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolResult = PoolResult.T
  object PoolResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolId = PoolId.T
  object PoolId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllowanceWindowResult = AllowanceWindowResult.T
  object AllowanceWindowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllowanceWindowKind = AllowanceWindowKind.T
  object AllowanceWindowKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentsStateResult = AgentsStateResult.T
  object AgentsStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentsStateAutopilotOn = AgentsStateAutopilotOn.T
  object AgentsStateAutopilotOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentsStateDraining = AgentsStateDraining.T
  object AgentsStateDraining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecisionsResult = DecisionsResult.T
  object DecisionsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecisionsOpen = DecisionsOpen.T
  object DecisionsOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecisionsShortResult = DecisionsShortResult.T
  object DecisionsShortResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecisionsShortOpen = DecisionsShortOpen.T
  object DecisionsShortOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobKindResult = JobKindResult.T
  object JobKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobKindOfResult = JobKindOfResult.T
  object JobKindOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobKindOfWire = JobKindOfWire.T
  object JobKindOfWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobStateResult = JobStateResult.T
  object JobStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginResult = PluginResult.T
  object PluginResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginId = _root_.com.histmersion.api.Wording.HumanizeId
  val PluginId: _root_.com.histmersion.api.Wording.HumanizeId.type = _root_.com.histmersion.api.Wording.HumanizeId
  type PluginNamesPart1 = PluginNamesPart1.T
  object PluginNamesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginNamesPart2 = PluginNamesPart2.T
  object PluginNamesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def humanize(id: com.histmersion.api.Wording.HumanizeId): com.histmersion.api.Wording.HumanizeResult =
    val words =
      (id).value.trim.replace(':', '\u0000').replaceAll("[_-]+", " ").replace("\u0000", ": ").replaceAll("\\s+", " ").trim
    _root_.com.histmersion.api.Wording.HumanizeResult((capitalize(_root_.com.histmersion.api.Wording.CapitalizeS(words))).value)

  def capitalize(s: com.histmersion.api.Wording.CapitalizeS): com.histmersion.api.Wording.CapitalizeResult = if (s).value.isEmpty then _root_.com.histmersion.api.Wording.CapitalizeResult((s).value) else _root_.com.histmersion.api.Wording.CapitalizeResult((s).value.substring(0, 1).toUpperCase + (s).value.substring(1))

  def value(s: com.histmersion.api.Wording.ValueS): com.histmersion.api.Wording.ValueResult = Label.display(s)

  def heading(s: com.histmersion.api.Wording.HeadingS): com.histmersion.api.Wording.HeadingResult =
    if (s).value.trim.matches("[a-z0-9]+([-_][a-z0-9]+)+") then _root_.com.histmersion.api.Wording.HeadingResult((humanize(_root_.com.histmersion.api.Wording.HumanizeId((s).value))).value) else capitalize(Label.display(_root_.com.histmersion.api.library.Label.DisplayS((s).value.trim)))

  def status(s: Source.Status): com.histmersion.api.Wording.StatusResult = s match
    case Source.Status.Foundational => _root_.com.histmersion.api.Wording.StatusResult("Foundational")
    case Source.Status.Current      => _root_.com.histmersion.api.Wording.StatusResult("Current")
    case Source.Status.Contested    => _root_.com.histmersion.api.Wording.StatusResult("Contested")
    case Source.Status.Superseded   => _root_.com.histmersion.api.Wording.StatusResult("Superseded")
    case Source.Status.Debunked     => _root_.com.histmersion.api.Wording.StatusResult("Debunked")
    case Source.Status.Retracted    => _root_.com.histmersion.api.Wording.StatusResult("Retracted")
    case Source.Status.Unassessed   => _root_.com.histmersion.api.Wording.StatusResult("Not assessed")

  def statusOf(wire: com.histmersion.api.Wording.StatusOfWire): com.histmersion.api.Wording.StatusOfResult =
    Source.Status.values.find(_.wire == _root_.com.histmersion.api.library.Source.Status.WireResult((wire).value.trim.toLowerCase)).fold(humanize(_root_.com.histmersion.api.Wording.HumanizeId((wire).value)))(status)

  def pipeline(p: Pipeline): com.histmersion.api.Wording.PipelineResult = p match
    case Pipeline.EnrichSource     => _root_.com.histmersion.api.Wording.PipelineResult("Enrich a source")
    case Pipeline.ReviewArticle    => _root_.com.histmersion.api.Wording.PipelineResult("Review an article")
    case Pipeline.PlanGoal         => _root_.com.histmersion.api.Wording.PipelineResult("Plan a goal")
    case Pipeline.CompileArticle   => _root_.com.histmersion.api.Wording.PipelineResult("Write an article")
    case Pipeline.CurrencyCheck    => _root_.com.histmersion.api.Wording.PipelineResult("Check sources are current")
    case Pipeline.AcquisitionSweep => _root_.com.histmersion.api.Wording.PipelineResult("Look for copies to fetch")
    case Pipeline.InferFilter      => _root_.com.histmersion.api.Wording.PipelineResult("Work out an article's filter")
    case Pipeline.HintEffect       => _root_.com.histmersion.api.Wording.PipelineResult("Turn a hint into an action")
    case Pipeline.ProposeArticles  => _root_.com.histmersion.api.Wording.PipelineResult("Propose articles")

  def approvalKind(k: Approval.Kind): com.histmersion.api.Wording.ApprovalKindResult = k match
    case Approval.Kind.StatusChange   => _root_.com.histmersion.api.Wording.ApprovalKindResult("Change a source's status")
    case Approval.Kind.Edge           => _root_.com.histmersion.api.Wording.ApprovalKindResult("Link two sources")
    case Approval.Kind.GoalPlan       => _root_.com.histmersion.api.Wording.ApprovalKindResult("Plan a goal")
    case Approval.Kind.ArticlePublish => _root_.com.histmersion.api.Wording.ApprovalKindResult("Make an article readable")
    case Approval.Kind.ArticleFilter  => _root_.com.histmersion.api.Wording.ApprovalKindResult("Set an article's filter")
    case Approval.Kind.ArticlePlan    => _root_.com.histmersion.api.Wording.ApprovalKindResult("Plan new articles")
    case Approval.Kind.HintEffect     => _root_.com.histmersion.api.Wording.ApprovalKindResult("Act on a hint")

  def runState(s: Run.State): com.histmersion.api.Wording.RunStateResult = s match
    case Run.State.Queued          => _root_.com.histmersion.api.Wording.RunStateResult("Queued")
    case Run.State.Running         => _root_.com.histmersion.api.Wording.RunStateResult("Running")
    case Run.State.Paused          => _root_.com.histmersion.api.Wording.RunStateResult("Paused")
    case Run.State.WaitingApproval => _root_.com.histmersion.api.Wording.RunStateResult("Waiting for a decision")
    case Run.State.Done            => _root_.com.histmersion.api.Wording.RunStateResult("Done")
    case Run.State.Failed          => _root_.com.histmersion.api.Wording.RunStateResult("Failed")
    case Run.State.Cancelled       => _root_.com.histmersion.api.Wording.RunStateResult("Cancelled")

  def approvalState(s: Approval.State): com.histmersion.api.Wording.ApprovalStateResult = s match
    case Approval.State.Open     => _root_.com.histmersion.api.Wording.ApprovalStateResult("Needs a decision")
    case Approval.State.Approved => _root_.com.histmersion.api.Wording.ApprovalStateResult("Approved")
    case Approval.State.Rejected => _root_.com.histmersion.api.Wording.ApprovalStateResult("Rejected")
    case Approval.State.Failed   => _root_.com.histmersion.api.Wording.ApprovalStateResult("Failed")

  def managerKind(k: Manager.Kind): com.histmersion.api.Wording.ManagerKindResult = k match
    case Manager.Kind.Review => _root_.com.histmersion.api.Wording.ManagerKindResult("Review manager")
    case Manager.Kind.Seed   => _root_.com.histmersion.api.Wording.ManagerKindResult("Seeding manager")
    case Manager.Kind.Watch  => _root_.com.histmersion.api.Wording.ManagerKindResult("Watch manager")
    case Manager.Kind.Enrich => _root_.com.histmersion.api.Wording.ManagerKindResult("Enrich manager")

  def manager(id: com.histmersion.api.Wording.ManagerId): com.histmersion.api.Wording.ManagerResult =
    val (kind, rest) = (id).value.span(_ != ':')
    Manager.Kind
      .parse(_root_.com.histmersion.api.automation.Manager.Kind.ParseS(kind))
      .fold(humanize(id))(k =>
        (managerKind(k)).value + Option(rest.drop(1)).filter(_.nonEmpty).fold("")(g => s" · ${humanize(_root_.com.histmersion.api.Wording.HumanizeId(g))}")
      )

  val schedulerJobTypes: Map[com.histmersion.api.Wording.SchedulerJobTypesPart1, com.histmersion.api.Wording.SchedulerJobTypesPart2] = Map(
    { val refactorTuple23 = "goal-plan" -> "Plan a goal"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple23._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple23._2)) },
    { val refactorTuple2 = "propose" -> "Propose articles"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple2._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple2._2)) },
    { val refactorTuple25 = "sweep" -> "Look for copies to fetch"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple25._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple25._2)) },
    { val refactorTuple26 = "currency" -> "Check sources are current"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple26._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple26._2)) },
    { val refactorTuple5 = "enrich" -> "Enrich a source"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple5._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple5._2)) },
    { val refactorTuple6 = "compile" -> "Write an article"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple6._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple6._2)) },
    { val refactorTuple29 = "extend" -> "Extend an article"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple29._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple29._2)) },
    { val refactorTuple8 = "filter" -> "Work out an article's filter"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple8._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple8._2)) },
    { val refactorTuple9 = "review" -> "Review an article"; (_root_.com.histmersion.api.Wording.SchedulerJobTypesPart1(refactorTuple9._1), _root_.com.histmersion.api.Wording.SchedulerJobTypesPart2(refactorTuple9._2)) }
  )

  val strategies: Map[com.histmersion.api.Wording.StrategiesPart1, com.histmersion.api.Wording.StrategiesPart2] = Map(
    { val refactorTuple10 = "type-balanced" -> "Balance the kinds of work"; (_root_.com.histmersion.api.Wording.StrategiesPart1(refactorTuple10._1), _root_.com.histmersion.api.Wording.StrategiesPart2(refactorTuple10._2)) },
    { val refactorTuple11 = "goal-first" -> "One goal at a time"; (_root_.com.histmersion.api.Wording.StrategiesPart1(refactorTuple11._1), _root_.com.histmersion.api.Wording.StrategiesPart2(refactorTuple11._2)) },
    { val refactorTuple12 = "default" -> "The configured strategy"; (_root_.com.histmersion.api.Wording.StrategiesPart1(refactorTuple12._1), _root_.com.histmersion.api.Wording.StrategiesPart2(refactorTuple12._2)) }
  )

  def pool(id: com.histmersion.api.Wording.PoolId): com.histmersion.api.Wording.PoolResult = (id).value.trim.toLowerCase match
    case "claude-plan"  => _root_.com.histmersion.api.Wording.PoolResult("Claude plan")
    case "chatgpt-plan" => _root_.com.histmersion.api.Wording.PoolResult("ChatGPT plan")
    case other          => _root_.com.histmersion.api.Wording.PoolResult((humanize(_root_.com.histmersion.api.Wording.HumanizeId(other.stripSuffix("-plan")))).value + (if other.endsWith("-plan") then " plan" else ""))

  def allowanceWindow(kind: com.histmersion.api.Wording.AllowanceWindowKind): com.histmersion.api.Wording.AllowanceWindowResult =
    val k = (kind).value.trim.toLowerCase.split('/').last
    def span(minutes: com.histmersion.api.Wording.AllowanceWindowSpanMinutes): com.histmersion.api.Wording.AllowanceWindowSpanResult = minutes match
      case 10080              => _root_.com.histmersion.api.Wording.AllowanceWindowSpanResult("This week's limit")
      case 1440               => _root_.com.histmersion.api.Wording.AllowanceWindowSpanResult("Today's limit")
      case m if (m).value % 1440 == 0 => _root_.com.histmersion.api.Wording.AllowanceWindowSpanResult(s"${(m).value / 1440}-day limit")
      case m if (m).value % 60 == 0   => _root_.com.histmersion.api.Wording.AllowanceWindowSpanResult(s"${(m).value / 60}-hour limit")
      case m                  => _root_.com.histmersion.api.Wording.AllowanceWindowSpanResult(s"$m-minute limit")
    val minutes = """^(\d+)m$""".r
    k match
      case minutes(n)                      => span(_root_.com.histmersion.api.Wording.AllowanceWindowSpanMinutes(n.toLong))
      case "five_hour"                     => _root_.com.histmersion.api.Wording.AllowanceWindowResult("5-hour limit")
      case "seven_day"                     => _root_.com.histmersion.api.Wording.AllowanceWindowResult("This week's limit")
      case "one_day" | "daily"             => _root_.com.histmersion.api.Wording.AllowanceWindowResult("Today's limit")
      case s if s.startsWith("seven_day_") => _root_.com.histmersion.api.Wording.AllowanceWindowResult(s"This week's limit (${humanize(_root_.com.histmersion.api.Wording.HumanizeId(s.stripPrefix("seven_day_")))})")
      case s if s.startsWith("five_hour_") => _root_.com.histmersion.api.Wording.AllowanceWindowResult(s"5-hour limit (${humanize(_root_.com.histmersion.api.Wording.HumanizeId(s.stripPrefix("five_hour_")))})")
      case other                           => _root_.com.histmersion.api.Wording.AllowanceWindowResult((humanize(_root_.com.histmersion.api.Wording.HumanizeId(other))).value)

  def agentsState(autopilotOn: com.histmersion.api.Wording.AgentsStateAutopilotOn, draining: com.histmersion.api.Wording.AgentsStateDraining): com.histmersion.api.Wording.AgentsStateResult =
    if (draining).value then _root_.com.histmersion.api.Wording.AgentsStateResult("Agents: finishing current work, then pausing")
    else if (autopilotOn).value then _root_.com.histmersion.api.Wording.AgentsStateResult("Agents: working")
    else _root_.com.histmersion.api.Wording.AgentsStateResult("Agents: paused")

  def decisions(open: com.histmersion.api.Wording.DecisionsOpen): Option[com.histmersion.api.Wording.DecisionsResult] =
    Option.when((open).value > 0)(if open == _root_.com.histmersion.api.Wording.DecisionsOpen(1) then _root_.com.histmersion.api.Wording.DecisionsResult("1 needs your decision") else _root_.com.histmersion.api.Wording.DecisionsResult(s"$open need your decision"))

  def decisionsShort(open: com.histmersion.api.Wording.DecisionsShortOpen): com.histmersion.api.Wording.DecisionsShortResult = if open == _root_.com.histmersion.api.Wording.DecisionsShortOpen(1) then _root_.com.histmersion.api.Wording.DecisionsShortResult("1 needs you") else _root_.com.histmersion.api.Wording.DecisionsShortResult(s"$open need you")

  val Drain = "Finish current work, then pause"
  val Undrain = "Resume"

  def jobKind(k: JobInfo.Kind): com.histmersion.api.Wording.JobKindResult = RegisteredJobKinds.all.get(k).fold(humanize(k.value))(_.name.value)

  def jobKindOf(wire: com.histmersion.api.Wording.JobKindOfWire): com.histmersion.api.Wording.JobKindOfResult = RegisteredJobKinds.all.parse(wire).fold(humanize(wire))(_.name.value)

  def jobState(s: JobState): com.histmersion.api.Wording.JobStateResult = s match
    case JobState.Queued    => _root_.com.histmersion.api.Wording.JobStateResult("Queued")
    case JobState.Running   => _root_.com.histmersion.api.Wording.JobStateResult("Running")
    case JobState.Done      => _root_.com.histmersion.api.Wording.JobStateResult("Done")
    case JobState.Failed    => _root_.com.histmersion.api.Wording.JobStateResult("Failed")
    case JobState.Paused    => _root_.com.histmersion.api.Wording.JobStateResult("Paused")
    case JobState.Cancelled => _root_.com.histmersion.api.Wording.JobStateResult("Cancelled")

  def plugin(id: com.histmersion.api.Wording.PluginId): com.histmersion.api.Wording.PluginResult =
    val families = List("ai-" -> "AI provider", "oa-" -> "Open access", "media-" -> "Pictures", "events-" -> "Events")
    pluginNames
      .get(_root_.com.histmersion.api.Wording.PluginNamesPart1((id).value))
      .orElse(families.collectFirst {
        case (prefix, family) if (id).value.startsWith(prefix) => s"$family: ${humanize((id).value.stripPrefix(prefix))}"
      })
      .getOrElse(humanize(id))

  private val pluginNames: Map[com.histmersion.api.Wording.PluginNamesPart1, com.histmersion.api.Wording.PluginNamesPart2] = Map(
    { val refactorTuple35 = "archive-org" -> "Internet Archive"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple35._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple35._2)) },
    { val refactorTuple14 = "oa-unpaywall" -> "Open access: Unpaywall"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple14._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple14._2)) },
    { val refactorTuple15 = "oa-core" -> "Open access: CORE"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple15._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple15._2)) },
    { val refactorTuple38 = "oa-openalex" -> "Open access: OpenAlex"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple38._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple38._2)) },
    { val refactorTuple17 = "oa-landing" -> "Open access: publishers' own pages"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple17._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple17._2)) },
    { val refactorTuple18 = "pmc" -> "PubMed Central"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple18._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple18._2)) },
    { val refactorTuple41 = "arxiv" -> "arXiv"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple41._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple41._2)) },
    { val refactorTuple42 = "keepassxc" -> "KeePassXC"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple42._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple42._2)) },
    { val refactorTuple43 = "1password" -> "1Password"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple43._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple43._2)) },
    { val refactorTuple44 = "bitwarden" -> "Bitwarden"; (_root_.com.histmersion.api.Wording.PluginNamesPart1(refactorTuple44._1), _root_.com.histmersion.api.Wording.PluginNamesPart2(refactorTuple44._2)) }
  )
