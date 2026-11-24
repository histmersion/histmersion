package com.histmersion
package api
import com.histmersion.api.jobs.JobInfo

import com.histmersion.api.automation.Pipeline
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.Label
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Run
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.automation.AgentMode
import com.histmersion.api.automation.ProviderKind
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@movesTo(FeaturePlace.UtilCommon)
object Wording:
  object Decisions:
    type Open = Open.T
    object Open extends Newtype[Int, Pure]
  type Id = Id.T
  object Id extends Newtype[String, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ManagerId = Id
  val ManagerId: Id.type = Id
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type PluginId = Id
  val PluginId: Id.type = Id

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def humanize(id: Id): String =
    val words =
      (id).value.trim
        .replace(':', '\u0000')
        .replaceAll("[_-]+", " ")
        .replace("\u0000", ": ")
        .replaceAll("\\s+", " ")
        .trim
    ((capitalize((words))))

  def capitalize(s: String): String =
    if (s).isEmpty then ((s)) else ((s).substring(0, 1).toUpperCase + (s).substring(1))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def value(s: String): String = Label.display(Label.Text(s)).value

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def heading(s: String): String =
    if (s).trim.matches("[a-z0-9]+([-_][a-z0-9]+)+") then ((humanize(Id((s)))))
    else capitalize(Label.display(Label.Text((s).trim)).value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def status(s: Source.Status): String = s match
    case Source.Status.Foundational => ("Foundational")
    case Source.Status.Current      => ("Current")
    case Source.Status.Contested    => ("Contested")
    case Source.Status.Superseded   => ("Superseded")
    case Source.Status.Debunked     => ("Debunked")
    case Source.Status.Retracted    => ("Retracted")
    case Source.Status.Unassessed   => ("Not assessed")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statusOf(wire: Api.GoalQuery.Status): String =
    Source.Status.values
      .find(_.wire == Source.Status.Wire((wire).value.trim.toLowerCase))
      .fold(humanize(Id((wire).value)))(status)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def approvalKind(k: Approval.Kind): String = k match
    case Approval.Kind.StatusChange   => ("Change a source's status")
    case Approval.Kind.Edge           => ("Link two sources")
    case Approval.Kind.GoalPlan       => ("Plan a goal")
    case Approval.Kind.ArticlePublish => ("Make an article readable")
    case Approval.Kind.ArticleFilter  => ("Set an article's filter")
    case Approval.Kind.ArticlePlan    => ("Plan new articles")
    case Approval.Kind.HintEffect     => ("Act on a hint")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def runState(s: Run.State): String = s match
    case Run.State.Queued          => ("Queued")
    case Run.State.Running         => ("Running")
    case Run.State.Paused          => ("Paused")
    case Run.State.WaitingApproval => ("Waiting for a decision")
    case Run.State.Done            => ("Done")
    case Run.State.Failed          => ("Failed")
    case Run.State.Cancelled       => ("Cancelled")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def approvalState(s: Approval.State): String = s match
    case Approval.State.Open     => ("Needs a decision")
    case Approval.State.Approved => ("Approved")
    case Approval.State.Rejected => ("Rejected")
    case Approval.State.Failed   => ("Failed")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def managerKind(k: Manager.Kind): String = k match
    case Manager.Kind.Review => ("Review manager")
    case Manager.Kind.Seed   => ("Seeding manager")
    case Manager.Kind.Watch  => ("Watch manager")
    case Manager.Kind.Enrich => ("Enrich manager")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def manager(id: ManagerId): String =
    val (kind, rest) = (id).value.span(_ != ':')
    Manager.Kind
      .parse(Manager.Kind.Wire(kind))
      .fold(humanize(id))(k =>
        (managerKind(k)) + Option(rest.drop(1)).filter(_.nonEmpty).fold("")(g => s" · ${humanize(Id(g))}")
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val schedulerJobTypes: Map[JobKinds.Wire, String] = Map(
    { val refactorTuple23 = "goal-plan" -> "Plan a goal"; (JobKinds.Wire(refactorTuple23._1), (refactorTuple23._2)) },
    { val refactorTuple2 = "propose" -> "Propose articles"; (JobKinds.Wire(refactorTuple2._1), (refactorTuple2._2)) }, {
      val refactorTuple25 = "sweep" -> "Look for copies to fetch";
      (JobKinds.Wire(refactorTuple25._1), (refactorTuple25._2))
    }, {
      val refactorTuple26 = "currency" -> "Check sources are current";
      (JobKinds.Wire(refactorTuple26._1), (refactorTuple26._2))
    },
    { val refactorTuple5 = "enrich" -> "Enrich a source"; (JobKinds.Wire(refactorTuple5._1), (refactorTuple5._2)) },
    { val refactorTuple6 = "compile" -> "Write an article"; (JobKinds.Wire(refactorTuple6._1), (refactorTuple6._2)) }, {
      val refactorTuple29 = "extend" -> "Extend an article"; (JobKinds.Wire(refactorTuple29._1), (refactorTuple29._2))
    }, {
      val refactorTuple8 = "filter" -> "Work out an article's filter";
      (JobKinds.Wire(refactorTuple8._1), (refactorTuple8._2))
    },
    { val refactorTuple9 = "review" -> "Review an article"; (JobKinds.Wire(refactorTuple9._1), (refactorTuple9._2)) }
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val strategies: Map[AgentScheduler.StrategyLine.Name, String] = Map(
    {
      val refactorTuple10 = "type-balanced" -> "Balance the kinds of work";
      (AgentScheduler.StrategyLine.Name(refactorTuple10._1), (refactorTuple10._2))
    }, {
      val refactorTuple11 = "goal-first" -> "One goal at a time";
      (AgentScheduler.StrategyLine.Name(refactorTuple11._1), (refactorTuple11._2))
    }, {
      val refactorTuple12 = "default" -> "The configured strategy";
      (AgentScheduler.StrategyLine.Name(refactorTuple12._1), (refactorTuple12._2))
    }
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pool(id: AgentScheduler.PoolUse.Pool): String = (id).value.trim.toLowerCase match
    case "claude-plan"  => ("Claude plan")
    case "chatgpt-plan" => ("ChatGPT plan")
    case other => ((humanize(Id(other.stripSuffix("-plan")))) + (if other.endsWith("-plan") then " plan" else ""))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def allowanceWindow(kind: PlanQuota.WindowStatus.Kind): String =
    val k = (kind).value.trim.toLowerCase.split('/').last
    def span(minutes: AllowanceWindow.Minutes): String = (minutes.value: Long) match
      case 10080              => ("This week's limit")
      case 1440               => ("Today's limit")
      case m if m % 1440 == 0 => (s"${m / 1440}-day limit")
      case m if m % 60 == 0   => (s"${m / 60}-hour limit")
      case m                  => (s"$m-minute limit")
    val minutes = """^(\d+)m$""".r
    k match
      case minutes(n)                      => span(AllowanceWindow.Minutes(n.toLong))
      case "five_hour"                     => ("5-hour limit")
      case "seven_day"                     => ("This week's limit")
      case "one_day" | "daily"             => ("Today's limit")
      case s if s.startsWith("seven_day_") => (s"This week's limit (${humanize(Id(s.stripPrefix("seven_day_")))})")
      case s if s.startsWith("five_hour_") => (s"5-hour limit (${humanize(Id(s.stripPrefix("five_hour_")))})")
      case other                           => ((humanize(Id(other))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def agentsState(autopilotOn: AgentMode.Step.Autopilot.On, draining: AgentMode.Step.Drain.On): String =
    if (draining).value then ("Agents: finishing current work, then pausing")
    else if (autopilotOn).value then ("Agents: working")
    else ("Agents: paused")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decisions(open: Decisions.Open): Option[String] =
    Option.when((open).value > 0)(if open == Decisions.Open(1) then ("1 needs your decision")
    else (s"$open need your decision"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decisionsShort(open: Decisions.Open): String =
    if open == Decisions.Open(1) then ("1 needs you") else (s"$open need you")

  val Drain = "Finish current work, then pause"
  val Undrain = "Resume"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def jobKind(k: JobInfo.Kind): String = RegisteredJobKinds.all.get(k).fold(humanize(Id(k.value)))(_.name.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def jobKindOf(wire: JobKinds.Wire): String =
    RegisteredJobKinds.all.parse(wire).fold(humanize(Id(wire.value)))(_.name.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def jobState(s: JobState): String = s match
    case JobState.Queued    => ("Queued")
    case JobState.Running   => ("Running")
    case JobState.Done      => ("Done")
    case JobState.Failed    => ("Failed")
    case JobState.Paused    => ("Paused")
    case JobState.Cancelled => ("Cancelled")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def plugin(id: PluginId): String =
    val families = List("ai-" -> "AI provider", "oa-" -> "Open access", "media-" -> "Pictures", "events-" -> "Events")
    pluginNames
      .get(ProviderKind.Plugin.Id((id).value))
      .orElse(families.collectFirst {
        case (prefix, family) if (id).value.startsWith(prefix) =>
          s"$family: ${humanize(Id((id).value.stripPrefix(prefix)))}"
      })
      .getOrElse(humanize(id))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val pluginNames: Map[ProviderKind.Plugin.Id, String] = Map(
    {
      val refactorTuple35 = "archive-org" -> "Internet Archive";
      (ProviderKind.Plugin.Id(refactorTuple35._1), (refactorTuple35._2))
    }, {
      val refactorTuple14 = "oa-unpaywall" -> "Open access: Unpaywall";
      (ProviderKind.Plugin.Id(refactorTuple14._1), (refactorTuple14._2))
    }, {
      val refactorTuple15 = "oa-core" -> "Open access: CORE";
      (ProviderKind.Plugin.Id(refactorTuple15._1), (refactorTuple15._2))
    }, {
      val refactorTuple38 = "oa-openalex" -> "Open access: OpenAlex";
      (ProviderKind.Plugin.Id(refactorTuple38._1), (refactorTuple38._2))
    }, {
      val refactorTuple17 = "oa-landing" -> "Open access: publishers' own pages";
      (ProviderKind.Plugin.Id(refactorTuple17._1), (refactorTuple17._2))
    }, {
      val refactorTuple18 = "pmc" -> "PubMed Central";
      (ProviderKind.Plugin.Id(refactorTuple18._1), (refactorTuple18._2))
    },
    { val refactorTuple41 = "arxiv" -> "arXiv"; (ProviderKind.Plugin.Id(refactorTuple41._1), (refactorTuple41._2)) }, {
      val refactorTuple42 = "keepassxc" -> "KeePassXC";
      (ProviderKind.Plugin.Id(refactorTuple42._1), (refactorTuple42._2))
    }, {
      val refactorTuple43 = "1password" -> "1Password";
      (ProviderKind.Plugin.Id(refactorTuple43._1), (refactorTuple43._2))
    }, {
      val refactorTuple44 = "bitwarden" -> "Bitwarden";
      (ProviderKind.Plugin.Id(refactorTuple44._1), (refactorTuple44._2))
    }
  )
