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
  type ManagerId = ManagerId.T
  object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type PluginId = PluginId.T
  object PluginId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginNamesPart1 = PluginNamesPart1.T
  object PluginNamesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginNamesPart2 = PluginNamesPart2.T
  object PluginNamesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def humanize(id: com.histmersion.api.Wording.HumanizeId): com.histmersion.api.Wording.HumanizeResult =
    val words =
      id.trim.replace(':', '\u0000').replaceAll("[_-]+", " ").replace("\u0000", ": ").replaceAll("\\s+", " ").trim
    capitalize(words)

  def capitalize(s: com.histmersion.api.Wording.CapitalizeS): com.histmersion.api.Wording.CapitalizeResult = if s.isEmpty then s else s.substring(0, 1).toUpperCase + s.substring(1)

  def value(s: com.histmersion.api.Wording.ValueS): com.histmersion.api.Wording.ValueResult = Label.display(s)

  def heading(s: com.histmersion.api.Wording.HeadingS): com.histmersion.api.Wording.HeadingResult =
    if s.trim.matches("[a-z0-9]+([-_][a-z0-9]+)+") then humanize(s) else capitalize(Label.display(s.trim))

  def status(s: Source.Status): com.histmersion.api.Wording.StatusResult = s match
    case Source.Status.Foundational => "Foundational"
    case Source.Status.Current      => "Current"
    case Source.Status.Contested    => "Contested"
    case Source.Status.Superseded   => "Superseded"
    case Source.Status.Debunked     => "Debunked"
    case Source.Status.Retracted    => "Retracted"
    case Source.Status.Unassessed   => "Not assessed"

  def statusOf(wire: com.histmersion.api.Wording.StatusOfWire): com.histmersion.api.Wording.StatusOfResult =
    Source.Status.values.find(_.wire == wire.trim.toLowerCase).fold(humanize(wire))(status)

  def pipeline(p: Pipeline): com.histmersion.api.Wording.PipelineResult = p match
    case Pipeline.EnrichSource     => "Enrich a source"
    case Pipeline.ReviewArticle    => "Review an article"
    case Pipeline.PlanGoal         => "Plan a goal"
    case Pipeline.CompileArticle   => "Write an article"
    case Pipeline.CurrencyCheck    => "Check sources are current"
    case Pipeline.AcquisitionSweep => "Look for copies to fetch"
    case Pipeline.InferFilter      => "Work out an article's filter"
    case Pipeline.HintEffect       => "Turn a hint into an action"
    case Pipeline.ProposeArticles  => "Propose articles"

  def approvalKind(k: Approval.Kind): com.histmersion.api.Wording.ApprovalKindResult = k match
    case Approval.Kind.StatusChange   => "Change a source's status"
    case Approval.Kind.Edge           => "Link two sources"
    case Approval.Kind.GoalPlan       => "Plan a goal"
    case Approval.Kind.ArticlePublish => "Make an article readable"
    case Approval.Kind.ArticleFilter  => "Set an article's filter"
    case Approval.Kind.ArticlePlan    => "Plan new articles"
    case Approval.Kind.HintEffect     => "Act on a hint"

  def runState(s: Run.State): com.histmersion.api.Wording.RunStateResult = s match
    case Run.State.Queued          => "Queued"
    case Run.State.Running         => "Running"
    case Run.State.Paused          => "Paused"
    case Run.State.WaitingApproval => "Waiting for a decision"
    case Run.State.Done            => "Done"
    case Run.State.Failed          => "Failed"
    case Run.State.Cancelled       => "Cancelled"

  def approvalState(s: Approval.State): com.histmersion.api.Wording.ApprovalStateResult = s match
    case Approval.State.Open     => "Needs a decision"
    case Approval.State.Approved => "Approved"
    case Approval.State.Rejected => "Rejected"
    case Approval.State.Failed   => "Failed"

  def managerKind(k: Manager.Kind): com.histmersion.api.Wording.ManagerKindResult = k match
    case Manager.Kind.Review => "Review manager"
    case Manager.Kind.Seed   => "Seeding manager"
    case Manager.Kind.Watch  => "Watch manager"
    case Manager.Kind.Enrich => "Enrich manager"

  def manager(id: com.histmersion.api.Wording.ManagerId): com.histmersion.api.Wording.ManagerResult =
    val (kind, rest) = id.span(_ != ':')
    Manager.Kind
      .parse(kind)
      .fold(humanize(id))(k =>
        managerKind(k) + Option(rest.drop(1)).filter(_.nonEmpty).fold("")(g => s" · ${humanize(g)}")
      )

  val schedulerJobTypes: Map[com.histmersion.api.Wording.SchedulerJobTypesPart1, com.histmersion.api.Wording.SchedulerJobTypesPart2] = Map(
    "goal-plan" -> "Plan a goal",
    "propose" -> "Propose articles",
    "sweep" -> "Look for copies to fetch",
    "currency" -> "Check sources are current",
    "enrich" -> "Enrich a source",
    "compile" -> "Write an article",
    "extend" -> "Extend an article",
    "filter" -> "Work out an article's filter",
    "review" -> "Review an article"
  )

  val strategies: Map[com.histmersion.api.Wording.StrategiesPart1, com.histmersion.api.Wording.StrategiesPart2] = Map(
    "type-balanced" -> "Balance the kinds of work",
    "goal-first" -> "One goal at a time",
    "default" -> "The configured strategy"
  )

  def pool(id: com.histmersion.api.Wording.PoolId): com.histmersion.api.Wording.PoolResult = id.trim.toLowerCase match
    case "claude-plan"  => "Claude plan"
    case "chatgpt-plan" => "ChatGPT plan"
    case other          => humanize(other.stripSuffix("-plan")) + (if other.endsWith("-plan") then " plan" else "")

  def allowanceWindow(kind: com.histmersion.api.Wording.AllowanceWindowKind): com.histmersion.api.Wording.AllowanceWindowResult =
    val k = kind.trim.toLowerCase.split('/').last
    def span(minutes: Long): String = minutes match
      case 10080              => "This week's limit"
      case 1440               => "Today's limit"
      case m if m % 1440 == 0 => s"${m / 1440}-day limit"
      case m if m % 60 == 0   => s"${m / 60}-hour limit"
      case m                  => s"$m-minute limit"
    val minutes = """^(\d+)m$""".r
    k match
      case minutes(n)                      => span(n.toLong)
      case "five_hour"                     => "5-hour limit"
      case "seven_day"                     => "This week's limit"
      case "one_day" | "daily"             => "Today's limit"
      case s if s.startsWith("seven_day_") => s"This week's limit (${humanize(s.stripPrefix("seven_day_"))})"
      case s if s.startsWith("five_hour_") => s"5-hour limit (${humanize(s.stripPrefix("five_hour_"))})"
      case other                           => humanize(other)

  def agentsState(autopilotOn: com.histmersion.api.Wording.AgentsStateAutopilotOn, draining: com.histmersion.api.Wording.AgentsStateDraining): com.histmersion.api.Wording.AgentsStateResult =
    if draining then "Agents: finishing current work, then pausing"
    else if autopilotOn then "Agents: working"
    else "Agents: paused"

  def decisions(open: com.histmersion.api.Wording.DecisionsOpen): Option[com.histmersion.api.Wording.DecisionsResult] =
    Option.when(open > 0)(if open == 1 then "1 needs your decision" else s"$open need your decision")

  def decisionsShort(open: com.histmersion.api.Wording.DecisionsShortOpen): com.histmersion.api.Wording.DecisionsShortResult = if open == 1 then "1 needs you" else s"$open need you"

  val Drain = "Finish current work, then pause"
  val Undrain = "Resume"

  def jobKind(k: JobInfo.Kind): com.histmersion.api.Wording.JobKindResult = RegisteredJobKinds.all.get(k).fold(humanize(k.value))(_.name.value)

  def jobKindOf(wire: com.histmersion.api.Wording.JobKindOfWire): com.histmersion.api.Wording.JobKindOfResult = RegisteredJobKinds.all.parse(wire).fold(humanize(wire))(_.name.value)

  def jobState(s: JobState): com.histmersion.api.Wording.JobStateResult = s match
    case JobState.Queued    => "Queued"
    case JobState.Running   => "Running"
    case JobState.Done      => "Done"
    case JobState.Failed    => "Failed"
    case JobState.Paused    => "Paused"
    case JobState.Cancelled => "Cancelled"

  def plugin(id: com.histmersion.api.Wording.PluginId): com.histmersion.api.Wording.PluginResult =
    val families = List("ai-" -> "AI provider", "oa-" -> "Open access", "media-" -> "Pictures", "events-" -> "Events")
    pluginNames
      .get(id)
      .orElse(families.collectFirst {
        case (prefix, family) if id.startsWith(prefix) => s"$family: ${humanize(id.stripPrefix(prefix))}"
      })
      .getOrElse(humanize(id))

  private val pluginNames: Map[com.histmersion.api.Wording.PluginNamesPart1, com.histmersion.api.Wording.PluginNamesPart2] = Map(
    "archive-org" -> "Internet Archive",
    "oa-unpaywall" -> "Open access: Unpaywall",
    "oa-core" -> "Open access: CORE",
    "oa-openalex" -> "Open access: OpenAlex",
    "oa-landing" -> "Open access: publishers' own pages",
    "pmc" -> "PubMed Central",
    "arxiv" -> "arXiv",
    "keepassxc" -> "KeePassXC",
    "1password" -> "1Password",
    "bitwarden" -> "Bitwarden"
  )
