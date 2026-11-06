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
  object Decisions:
    type Open = Open.T
    object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type ManagerId = _root_.com.histmersion.api.Wording.Id
  val ManagerId: _root_.com.histmersion.api.Wording.Id.type = _root_.com.histmersion.api.Wording.Id
  type PluginId = _root_.com.histmersion.api.Wording.Id
  val PluginId: _root_.com.histmersion.api.Wording.Id.type = _root_.com.histmersion.api.Wording.Id

  def humanize(id: com.histmersion.api.Wording.Id): String =
    val words =
      (id).value.trim.replace(':', '\u0000').replaceAll("[_-]+", " ").replace("\u0000", ": ").replaceAll("\\s+", " ").trim
    ((capitalize((words))))

  def capitalize(s: String): String = if (s).isEmpty then ((s)) else ((s).substring(0, 1).toUpperCase + (s).substring(1))

  def value(s: String): String = Label.display(s)

  def heading(s: String): String =
    if (s).trim.matches("[a-z0-9]+([-_][a-z0-9]+)+") then ((humanize(_root_.com.histmersion.api.Wording.Id((s))))) else capitalize(Label.display(_root_.com.histmersion.api.library.Label.Text((s).trim)))

  def status(s: Source.Status): String = s match
    case Source.Status.Foundational => ("Foundational")
    case Source.Status.Current      => ("Current")
    case Source.Status.Contested    => ("Contested")
    case Source.Status.Superseded   => ("Superseded")
    case Source.Status.Debunked     => ("Debunked")
    case Source.Status.Retracted    => ("Retracted")
    case Source.Status.Unassessed   => ("Not assessed")

  def statusOf(wire: com.histmersion.api.Api.GoalQuery.Status.Wire): String =
    Source.Status.values.find(_.wire == _root_.com.histmersion.api.library.Source.Status.Wire((wire).value.trim.toLowerCase)).fold(humanize(_root_.com.histmersion.api.Wording.Id((wire).value)))(status)

  def pipeline(p: Pipeline): String = p match
    case Pipeline.EnrichSource     => ("Enrich a source")
    case Pipeline.ReviewArticle    => ("Review an article")
    case Pipeline.PlanGoal         => ("Plan a goal")
    case Pipeline.CompileArticle   => ("Write an article")
    case Pipeline.CurrencyCheck    => ("Check sources are current")
    case Pipeline.AcquisitionSweep => ("Look for copies to fetch")
    case Pipeline.InferFilter      => ("Work out an article's filter")
    case Pipeline.HintEffect       => ("Turn a hint into an action")
    case Pipeline.ProposeArticles  => ("Propose articles")

  def approvalKind(k: Approval.Kind): String = k match
    case Approval.Kind.StatusChange   => ("Change a source's status")
    case Approval.Kind.Edge           => ("Link two sources")
    case Approval.Kind.GoalPlan       => ("Plan a goal")
    case Approval.Kind.ArticlePublish => ("Make an article readable")
    case Approval.Kind.ArticleFilter  => ("Set an article's filter")
    case Approval.Kind.ArticlePlan    => ("Plan new articles")
    case Approval.Kind.HintEffect     => ("Act on a hint")

  def runState(s: Run.State): String = s match
    case Run.State.Queued          => ("Queued")
    case Run.State.Running         => ("Running")
    case Run.State.Paused          => ("Paused")
    case Run.State.WaitingApproval => ("Waiting for a decision")
    case Run.State.Done            => ("Done")
    case Run.State.Failed          => ("Failed")
    case Run.State.Cancelled       => ("Cancelled")

  def approvalState(s: Approval.State): String = s match
    case Approval.State.Open     => ("Needs a decision")
    case Approval.State.Approved => ("Approved")
    case Approval.State.Rejected => ("Rejected")
    case Approval.State.Failed   => ("Failed")

  def managerKind(k: Manager.Kind): String = k match
    case Manager.Kind.Review => ("Review manager")
    case Manager.Kind.Seed   => ("Seeding manager")
    case Manager.Kind.Watch  => ("Watch manager")
    case Manager.Kind.Enrich => ("Enrich manager")

  def manager(id: com.histmersion.api.Wording.ManagerId): String =
    val (kind, rest) = (id).value.span(_ != ':')
    Manager.Kind
      .parse(_root_.com.histmersion.api.automation.Manager.Kind.Wire(kind))
      .fold(humanize(id))(k =>
        (managerKind(k)) + Option(rest.drop(1)).filter(_.nonEmpty).fold("")(g => s" · ${humanize(_root_.com.histmersion.api.Wording.Id(g))}")
      )

  val schedulerJobTypes: Map[com.histmersion.api.jobs.JobKinds.Wire, String] = Map(
    { val refactorTuple23 = "goal-plan" -> "Plan a goal"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple23._1), (refactorTuple23._2)) },
    { val refactorTuple2 = "propose" -> "Propose articles"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple2._1), (refactorTuple2._2)) },
    { val refactorTuple25 = "sweep" -> "Look for copies to fetch"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple25._1), (refactorTuple25._2)) },
    { val refactorTuple26 = "currency" -> "Check sources are current"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple26._1), (refactorTuple26._2)) },
    { val refactorTuple5 = "enrich" -> "Enrich a source"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple5._1), (refactorTuple5._2)) },
    { val refactorTuple6 = "compile" -> "Write an article"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple6._1), (refactorTuple6._2)) },
    { val refactorTuple29 = "extend" -> "Extend an article"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple29._1), (refactorTuple29._2)) },
    { val refactorTuple8 = "filter" -> "Work out an article's filter"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple8._1), (refactorTuple8._2)) },
    { val refactorTuple9 = "review" -> "Review an article"; (_root_.com.histmersion.api.jobs.JobKinds.Wire(refactorTuple9._1), (refactorTuple9._2)) }
  )

  val strategies: Map[com.histmersion.api.automation.AgentScheduler.StrategyLine.Name, String] = Map(
    { val refactorTuple10 = "type-balanced" -> "Balance the kinds of work"; (_root_.com.histmersion.api.automation.AgentScheduler.StrategyLine.Name(refactorTuple10._1), (refactorTuple10._2)) },
    { val refactorTuple11 = "goal-first" -> "One goal at a time"; (_root_.com.histmersion.api.automation.AgentScheduler.StrategyLine.Name(refactorTuple11._1), (refactorTuple11._2)) },
    { val refactorTuple12 = "default" -> "The configured strategy"; (_root_.com.histmersion.api.automation.AgentScheduler.StrategyLine.Name(refactorTuple12._1), (refactorTuple12._2)) }
  )

  def pool(id: com.histmersion.api.automation.AgentScheduler.PoolUse.Pool): String = (id).value.trim.toLowerCase match
    case "claude-plan"  => ("Claude plan")
    case "chatgpt-plan" => ("ChatGPT plan")
    case other          => ((humanize(_root_.com.histmersion.api.Wording.Id(other.stripSuffix("-plan")))) + (if other.endsWith("-plan") then " plan" else ""))

  def allowanceWindow(kind: com.histmersion.api.Api.ArticleQuery.Kind.Wire): String =
    val k = (kind).value.trim.toLowerCase.split('/').last
    def span(minutes: com.histmersion.api.AllowanceWindow.Minutes): String = minutes match
      case 10080              => ("This week's limit")
      case 1440               => ("Today's limit")
      case m if (m).value % 1440 == 0 => (s"${(m).value / 1440}-day limit")
      case m if (m).value % 60 == 0   => (s"${(m).value / 60}-hour limit")
      case m                  => (s"$m-minute limit")
    val minutes = """^(\d+)m$""".r
    k match
      case minutes(n)                      => span(_root_.com.histmersion.api.AllowanceWindow.Minutes(n.toLong))
      case "five_hour"                     => ("5-hour limit")
      case "seven_day"                     => ("This week's limit")
      case "one_day" | "daily"             => ("Today's limit")
      case s if s.startsWith("seven_day_") => (s"This week's limit (${humanize(_root_.com.histmersion.api.Wording.Id(s.stripPrefix("seven_day_")))})")
      case s if s.startsWith("five_hour_") => (s"5-hour limit (${humanize(_root_.com.histmersion.api.Wording.Id(s.stripPrefix("five_hour_")))})")
      case other                           => ((humanize(_root_.com.histmersion.api.Wording.Id(other))))

  def agentsState(autopilotOn: com.histmersion.api.automation.AgentMode.Step.Autopilot.On, draining: com.histmersion.api.automation.AgentMode.Step.Drain.On): String =
    if (draining).value then ("Agents: finishing current work, then pausing")
    else if (autopilotOn).value then ("Agents: working")
    else ("Agents: paused")

  def decisions(open: com.histmersion.api.Wording.Decisions.Open): Option[String] =
    Option.when((open).value > 0)(if open == _root_.com.histmersion.api.Wording.Decisions.Open(1) then ("1 needs your decision") else (s"$open need your decision"))

  def decisionsShort(open: com.histmersion.api.Wording.Decisions.Open): String = if open == _root_.com.histmersion.api.Wording.Decisions.Open(1) then ("1 needs you") else (s"$open need you")

  val Drain = "Finish current work, then pause"
  val Undrain = "Resume"

  def jobKind(k: JobInfo.Kind): String = RegisteredJobKinds.all.get(k).fold(humanize(k.value))(_.name.value)

  def jobKindOf(wire: com.histmersion.api.jobs.JobKinds.Wire): String = RegisteredJobKinds.all.parse(wire).fold(humanize(wire))(_.name.value)

  def jobState(s: JobState): String = s match
    case JobState.Queued    => ("Queued")
    case JobState.Running   => ("Running")
    case JobState.Done      => ("Done")
    case JobState.Failed    => ("Failed")
    case JobState.Paused    => ("Paused")
    case JobState.Cancelled => ("Cancelled")

  def plugin(id: com.histmersion.api.Wording.PluginId): String =
    val families = List("ai-" -> "AI provider", "oa-" -> "Open access", "media-" -> "Pictures", "events-" -> "Events")
    pluginNames
      .get(_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id((id).value))
      .orElse(families.collectFirst {
        case (prefix, family) if (id).value.startsWith(prefix) => s"$family: ${humanize(_root_.com.histmersion.api.Wording.Id((id).value.stripPrefix(prefix)))}"
      })
      .getOrElse(humanize(id))

  private val pluginNames: Map[com.histmersion.api.automation.ProviderKind.Plugin.Id, String] = Map(
    { val refactorTuple35 = "archive-org" -> "Internet Archive"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple35._1), (refactorTuple35._2)) },
    { val refactorTuple14 = "oa-unpaywall" -> "Open access: Unpaywall"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple14._1), (refactorTuple14._2)) },
    { val refactorTuple15 = "oa-core" -> "Open access: CORE"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple15._1), (refactorTuple15._2)) },
    { val refactorTuple38 = "oa-openalex" -> "Open access: OpenAlex"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple38._1), (refactorTuple38._2)) },
    { val refactorTuple17 = "oa-landing" -> "Open access: publishers' own pages"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple17._1), (refactorTuple17._2)) },
    { val refactorTuple18 = "pmc" -> "PubMed Central"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple18._1), (refactorTuple18._2)) },
    { val refactorTuple41 = "arxiv" -> "arXiv"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple41._1), (refactorTuple41._2)) },
    { val refactorTuple42 = "keepassxc" -> "KeePassXC"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple42._1), (refactorTuple42._2)) },
    { val refactorTuple43 = "1password" -> "1Password"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple43._1), (refactorTuple43._2)) },
    { val refactorTuple44 = "bitwarden" -> "Bitwarden"; (_root_.com.histmersion.api.automation.ProviderKind.Plugin.Id(refactorTuple44._1), (refactorTuple44._2)) }
  )
