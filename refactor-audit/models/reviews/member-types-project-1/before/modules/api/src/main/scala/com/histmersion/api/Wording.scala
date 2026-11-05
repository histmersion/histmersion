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

  def humanize(id: String): String =
    val words =
      id.trim.replace(':', '\u0000').replaceAll("[_-]+", " ").replace("\u0000", ": ").replaceAll("\\s+", " ").trim
    capitalize(words)

  def capitalize(s: String): String = if s.isEmpty then s else s.substring(0, 1).toUpperCase + s.substring(1)

  def value(s: String): String = Label.display(s)

  def heading(s: String): String =
    if s.trim.matches("[a-z0-9]+([-_][a-z0-9]+)+") then humanize(s) else capitalize(Label.display(s.trim))

  def status(s: Source.Status): String = s match
    case Source.Status.Foundational => "Foundational"
    case Source.Status.Current      => "Current"
    case Source.Status.Contested    => "Contested"
    case Source.Status.Superseded   => "Superseded"
    case Source.Status.Debunked     => "Debunked"
    case Source.Status.Retracted    => "Retracted"
    case Source.Status.Unassessed   => "Not assessed"

  def statusOf(wire: String): String =
    Source.Status.values.find(_.wire == wire.trim.toLowerCase).fold(humanize(wire))(status)

  def pipeline(p: Pipeline): String = p match
    case Pipeline.EnrichSource     => "Enrich a source"
    case Pipeline.ReviewArticle    => "Review an article"
    case Pipeline.PlanGoal         => "Plan a goal"
    case Pipeline.CompileArticle   => "Write an article"
    case Pipeline.CurrencyCheck    => "Check sources are current"
    case Pipeline.AcquisitionSweep => "Look for copies to fetch"
    case Pipeline.InferFilter      => "Work out an article's filter"
    case Pipeline.HintEffect       => "Turn a hint into an action"
    case Pipeline.ProposeArticles  => "Propose articles"

  def approvalKind(k: Approval.Kind): String = k match
    case Approval.Kind.StatusChange   => "Change a source's status"
    case Approval.Kind.Edge           => "Link two sources"
    case Approval.Kind.GoalPlan       => "Plan a goal"
    case Approval.Kind.ArticlePublish => "Make an article readable"
    case Approval.Kind.ArticleFilter  => "Set an article's filter"
    case Approval.Kind.ArticlePlan    => "Plan new articles"
    case Approval.Kind.HintEffect     => "Act on a hint"

  def runState(s: Run.State): String = s match
    case Run.State.Queued          => "Queued"
    case Run.State.Running         => "Running"
    case Run.State.Paused          => "Paused"
    case Run.State.WaitingApproval => "Waiting for a decision"
    case Run.State.Done            => "Done"
    case Run.State.Failed          => "Failed"
    case Run.State.Cancelled       => "Cancelled"

  def approvalState(s: Approval.State): String = s match
    case Approval.State.Open     => "Needs a decision"
    case Approval.State.Approved => "Approved"
    case Approval.State.Rejected => "Rejected"
    case Approval.State.Failed   => "Failed"

  def managerKind(k: Manager.Kind): String = k match
    case Manager.Kind.Review => "Review manager"
    case Manager.Kind.Seed   => "Seeding manager"
    case Manager.Kind.Watch  => "Watch manager"
    case Manager.Kind.Enrich => "Enrich manager"

  def manager(id: String): String =
    val (kind, rest) = id.span(_ != ':')
    Manager.Kind
      .parse(kind)
      .fold(humanize(id))(k =>
        managerKind(k) + Option(rest.drop(1)).filter(_.nonEmpty).fold("")(g => s" · ${humanize(g)}")
      )

  val schedulerJobTypes: Map[String, String] = Map(
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

  val strategies: Map[String, String] = Map(
    "type-balanced" -> "Balance the kinds of work",
    "goal-first" -> "One goal at a time",
    "default" -> "The configured strategy"
  )

  def pool(id: String): String = id.trim.toLowerCase match
    case "claude-plan"  => "Claude plan"
    case "chatgpt-plan" => "ChatGPT plan"
    case other          => humanize(other.stripSuffix("-plan")) + (if other.endsWith("-plan") then " plan" else "")

  def allowanceWindow(kind: String): String =
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

  def agentsState(autopilotOn: Boolean, draining: Boolean): String =
    if draining then "Agents: finishing current work, then pausing"
    else if autopilotOn then "Agents: working"
    else "Agents: paused"

  def decisions(open: Int): Option[String] =
    Option.when(open > 0)(if open == 1 then "1 needs your decision" else s"$open need your decision")

  def decisionsShort(open: Int): String = if open == 1 then "1 needs you" else s"$open need you"

  val Drain = "Finish current work, then pause"
  val Undrain = "Resume"

  def jobKind(k: JobInfo.Kind): String = RegisteredJobKinds.all.get(k).fold(humanize(k.value))(_.name.value)

  def jobKindOf(wire: String): String = RegisteredJobKinds.all.parse(wire).fold(humanize(wire))(_.name.value)

  def jobState(s: JobState): String = s match
    case JobState.Queued    => "Queued"
    case JobState.Running   => "Running"
    case JobState.Done      => "Done"
    case JobState.Failed    => "Failed"
    case JobState.Paused    => "Paused"
    case JobState.Cancelled => "Cancelled"

  def plugin(id: String): String =
    val families = List("ai-" -> "AI provider", "oa-" -> "Open access", "media-" -> "Pictures", "events-" -> "Events")
    pluginNames
      .get(id)
      .orElse(families.collectFirst {
        case (prefix, family) if id.startsWith(prefix) => s"$family: ${humanize(id.stripPrefix(prefix))}"
      })
      .getOrElse(humanize(id))

  private val pluginNames: Map[String, String] = Map(
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
