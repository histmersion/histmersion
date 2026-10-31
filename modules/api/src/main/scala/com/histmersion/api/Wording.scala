package com.histmersion.api

import com.histmersion.values.{ApprovalKind, ApprovalState, JobKind, ManagerKind, Pipeline, RunState, SourceStatus}

object Wording:

  def humanize(id: String): String =
    val words =
      id.trim.replace(':', '\u0000').replaceAll("[_-]+", " ").replace("\u0000", ": ").replaceAll("\\s+", " ").trim
    capitalize(words)

  def capitalize(s: String): String = if s.isEmpty then s else s.substring(0, 1).toUpperCase + s.substring(1)

  def value(s: String): String = Label.display(s)

  def heading(s: String): String =
    if s.trim.matches("[a-z0-9]+([-_][a-z0-9]+)+") then humanize(s) else capitalize(Label.display(s.trim))

  def status(s: SourceStatus): String = s match
    case SourceStatus.Foundational => "Foundational"
    case SourceStatus.Current      => "Current"
    case SourceStatus.Contested    => "Contested"
    case SourceStatus.Superseded   => "Superseded"
    case SourceStatus.Debunked     => "Debunked"
    case SourceStatus.Retracted    => "Retracted"
    case SourceStatus.Unassessed   => "Not assessed"

  def statusOf(wire: String): String =
    SourceStatus.values.find(_.wire == wire.trim.toLowerCase).fold(humanize(wire))(status)

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

  def approvalKind(k: ApprovalKind): String = k match
    case ApprovalKind.StatusChange   => "Change a source's status"
    case ApprovalKind.Edge           => "Link two sources"
    case ApprovalKind.GoalPlan       => "Plan a goal"
    case ApprovalKind.ArticlePublish => "Make an article readable"
    case ApprovalKind.ArticleFilter  => "Set an article's filter"
    case ApprovalKind.ArticlePlan    => "Plan new articles"
    case ApprovalKind.HintEffect     => "Act on a hint"

  def runState(s: RunState): String = s match
    case RunState.Queued          => "Queued"
    case RunState.Running         => "Running"
    case RunState.Paused          => "Paused"
    case RunState.WaitingApproval => "Waiting for a decision"
    case RunState.Done            => "Done"
    case RunState.Failed          => "Failed"
    case RunState.Cancelled       => "Cancelled"

  def approvalState(s: ApprovalState): String = s match
    case ApprovalState.Open     => "Needs a decision"
    case ApprovalState.Approved => "Approved"
    case ApprovalState.Rejected => "Rejected"
    case ApprovalState.Failed   => "Failed"

  def managerKind(k: ManagerKind): String = k match
    case ManagerKind.Review => "Review manager"
    case ManagerKind.Seed   => "Seeding manager"
    case ManagerKind.Watch  => "Watch manager"
    case ManagerKind.Enrich => "Enrich manager"

  def manager(id: String): String =
    val (kind, rest) = id.span(_ != ':')
    ManagerKind
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

  val waitCodes: Map[String, String] = Map(
    "capacity" -> "No free place",
    "pool" -> "Plan is full",
    "manager-cap" -> "Manager is full",
    "blocked" -> "Blocked",
    "busy" -> "Busy",
    "budget" -> "Spending cap reached",
    "drain" -> "Finishing current work, then pausing",
    AgentScheduler.Code.Person -> "Needs your decision",
    AgentScheduler.Code.Held -> "Held by a person",
    AgentScheduler.Code.NotDue -> "Not due yet",
    AgentScheduler.Code.Running -> "Running",
    AgentScheduler.Code.Settled -> "Settled",
    AgentScheduler.Code.Paused -> "Paused"
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

  def agentsGlance(a: UiNotices.AgentsGlance): String =
    def runs(n: Int) = if n == 1 then "1 run" else s"$n runs"
    if !a.on || (a.draining && a.running == 0) then "Agents paused"
    else if a.draining then s"Agents finishing current work · ${runs(a.running)}"
    else if a.running > 0 then s"Agents working · ${runs(a.running)}"
    else if a.queued > 0 then s"Agents working · ${a.queued} waiting"
    else "Agents on · nothing to do"

  def decisions(open: Int): Option[String] =
    Option.when(open > 0)(if open == 1 then "1 needs your decision" else s"$open need your decision")

  def decisionsShort(open: Int): String = if open == 1 then "1 needs you" else s"$open need you"

  def agentMode(m: AgentMode): String = m match
    case AgentMode.Working   => "Working"
    case AgentMode.Finishing => "Finish current work, then pause"
    case AgentMode.Paused    => "Pause now"

  def agentsHeading(m: AgentMode): String = m match
    case AgentMode.Working   => "Research agents are working"
    case AgentMode.Finishing => "Research agents are finishing current work, then pausing"
    case AgentMode.Paused    => "Research agents are paused"

  def activityKind(k: Activity.EntryKind): String = k match
    case Activity.EntryKind.Run      => "Agent work"
    case Activity.EntryKind.Job      => "Background job"
    case Activity.EntryKind.Log      => "Note"
    case Activity.EntryKind.Decision => "You decided"

  val Drain = "Finish current work, then pause"
  val Undrain = "Resume"

  def jobKind(k: JobKind): String = JobKindsAdapter.all.get(k).fold(humanize(k.value))(_.name.value)

  def jobKindOf(wire: String): String = JobKindsAdapter.all.parse(wire).fold(humanize(wire))(_.name.value)

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

  lazy val known: Map[String, String] =
    schedulerJobTypes ++ waitCodes ++ strategies ++
      JobKindsAdapter.all.entries.map(e => e.kind.value -> e.name.value) ++
      ApprovalKind.values.map(k => k.wire -> approvalKind(k)) ++
      Pipeline.values.map(p => p.wire -> pipeline(p)) ++
      ManagerKind.values.map(k => k.wire -> managerKind(k)) ++
      SourceStatus.values.map(s => s.wire -> status(s)) ++
      ApprovalState.values.map(s => s.wire -> approvalState(s)) ++
      JobState.values.map(s => s.wire -> jobState(s)) ++
      RunState.values.map(s => s.wire -> runState(s))

  def label(id: String): String = known.getOrElse(id.trim, humanize(id))
