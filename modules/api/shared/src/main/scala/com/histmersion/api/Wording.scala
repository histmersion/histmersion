package com.histmersion.api

import com.histmersion.values.{ApprovalKind, ApprovalState, ManagerKind, Pipeline, RunState, SourceStatus}

/** The kinds of background job the server runs: the `kind` of a jobs row. Every one has a name for a person
  * ([[Wording.jobKind]]); the server's own constants are these wires, so a new kind starts here. */
enum JobKind(val wire: String):
  case Ingest          extends JobKind("ingest")
  case Reocr           extends JobKind("reocr")
  case Init            extends JobKind("init")
  case MergeApply      extends JobKind("merge-apply")
  case Vacuum          extends JobKind("vacuum")
  case Agent           extends JobKind("agent")
  case AcquireRecheck  extends JobKind("acquire_recheck")
  case BasemapFetch    extends JobKind("basemap-fetch")
  case PlacesFetch     extends JobKind("places-fetch")
  case PlacesHierarchy extends JobKind("places-hierarchy")
  case EventsImport    extends JobKind("events-import")
  case EventsCandidates extends JobKind("events-candidates")
  case EventsRederive  extends JobKind("events-rederive")
  case SiteTypes       extends JobKind("site-types")
  case MediaImport     extends JobKind("media-import")
  case MediaCovers     extends JobKind("media-covers")

object JobKind:
  def parse(s: String): Option[JobKind] = values.find(_.wire == s.trim.toLowerCase)

/** THE label dictionary: every internal identifier the web UI shows — a pipeline, an approval's kind, a job's
  * kind, a state, a scheduler strategy or pool, an allowance window, a manager's kind — as plain wording in
  * sentence case ("Review an article", not `review-article`). The identifier itself stays the wire value, in
  * the URL, the filters and a tooltip, for people who use the command line. An identifier the dictionary does
  * not know (a plugin's, one added later) is never shown raw either: [[label]] spells it as words. Pure. */
object Wording:

  /** `type-balanced` / `seven_day` / `watch:bronze-age` → `Type balanced` / `Seven day` / `Watch: bronze age`:
    * the identifier as words, its first letter upper-case — what an identifier the dictionary does not know
    * reads as. */
  def humanize(id: String): String =
    val words = id.trim.replace(':', '\u0000').replaceAll("[_-]+", " ").replace("\u0000", ": ").replaceAll("\\s+", " ").trim
    capitalize(words)

  /** The first letter upper-case, the rest as it is: sentence case for a label that is already words. */
  def capitalize(s: String): String = if s.isEmpty then s else s.substring(0, 1).toUpperCase + s.substring(1)

  /** A data value (a topic, a tag, a region) as words: `bronze_age_collapse` → `bronze age collapse`. Its
    * hyphens stay (`Indo-European`); only identifiers lose theirs ([[humanize]]). */
  def value(s: String): String = Label.display(s)

  /** A heading made from a stored topic or group name: a slug-shaped one (`disputed-grand-theories`,
    * `phoenician_levantine`) as words in sentence case, anything already written as words as it is, its first
    * letter upper-case. */
  def heading(s: String): String =
    if s.trim.matches("[a-z0-9]+([-_][a-z0-9]+)+") then humanize(s) else capitalize(Label.display(s.trim))

  // ── a source's standing ──────────────────────────────────────────────────────────────────────────────

  def status(s: SourceStatus): String = s match
    case SourceStatus.Foundational => "Foundational"
    case SourceStatus.Current      => "Current"
    case SourceStatus.Contested    => "Contested"
    case SourceStatus.Superseded   => "Superseded"
    case SourceStatus.Debunked     => "Debunked"
    case SourceStatus.Retracted    => "Retracted"
    case SourceStatus.Unassessed   => "Not assessed"

  /** A status as stored (`current`); an unknown word as words. */
  def statusOf(wire: String): String =
    SourceStatus.values.find(_.wire == wire.trim.toLowerCase).fold(humanize(wire))(status)

  // ── agent work ───────────────────────────────────────────────────────────────────────────────────────

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

  /** A manager by its id (`watch:bronze-age`, `review`): its kind's name, then the goal it serves as words. */
  def manager(id: String): String =
    val (kind, rest) = id.span(_ != ':')
    ManagerKind.parse(kind).fold(humanize(id))(k =>
      managerKind(k) + Option(rest.drop(1)).filter(_.nonEmpty).fold("")(g => s" · ${humanize(g)}"))

  /** The scheduler's job types: the kind of work a manager item is. */
  val schedulerJobTypes: Map[String, String] = Map(
    "goal-plan" -> "Plan a goal", "propose" -> "Propose articles", "sweep" -> "Look for copies to fetch",
    "currency" -> "Check sources are current", "enrich" -> "Enrich a source", "compile" -> "Write an article",
    "extend" -> "Extend an article", "filter" -> "Work out an article's filter", "review" -> "Review an article")

  /** Why a manager item waits (the scheduler's codes). */
  val waitCodes: Map[String, String] = Map(
    "capacity" -> "No free place", "pool" -> "Plan is full", "manager-cap" -> "Manager is full",
    "blocked" -> "Blocked", "busy" -> "Busy", "budget" -> "Spending cap reached",
    "drain" -> "Finishing current work, then pausing",
    AgentScheduler.Code.Person -> "Needs your decision", AgentScheduler.Code.Held -> "Held by a person",
    AgentScheduler.Code.NotDue -> "Not due yet", AgentScheduler.Code.Running -> "Running",
    AgentScheduler.Code.Settled -> "Settled", AgentScheduler.Code.Paused -> "Paused")

  /** The scheduler's strategies, by name. */
  val strategies: Map[String, String] = Map(
    "type-balanced" -> "Balance the kinds of work", "goal-first" -> "One goal at a time", "default" -> "The configured strategy")

  /** A subscription plan's pool (`claude-plan`): the plan's name. */
  def pool(id: String): String = id.trim.toLowerCase match
    case "claude-plan"  => "Claude plan"
    case "chatgpt-plan" => "ChatGPT plan"
    case other          => humanize(other.stripSuffix("-plan")) + (if other.endsWith("-plan") then " plan" else "")

  /** An allowance window as a provider names it — `five_hour`, `seven_day`, `seven_day_opus`, or a length in
    * minutes (`300m`, `codex/10080m`) — as the limit it is. */
  def allowanceWindow(kind: String): String =
    val k = kind.trim.toLowerCase.split('/').last
    def span(minutes: Long): String = minutes match
      case 10080 => "This week's limit"
      case 1440  => "Today's limit"
      case m if m % 1440 == 0 => s"${m / 1440}-day limit"
      case m if m % 60 == 0   => s"${m / 60}-hour limit"
      case m                  => s"$m-minute limit"
    val minutes = """^(\d+)m$""".r
    k match
      case minutes(n)     => span(n.toLong)
      case "five_hour"    => "5-hour limit"
      case "seven_day"    => "This week's limit"
      case "one_day" | "daily" => "Today's limit"
      case s if s.startsWith("seven_day_") => s"This week's limit (${humanize(s.stripPrefix("seven_day_"))})"
      case s if s.startsWith("five_hour_") => s"5-hour limit (${humanize(s.stripPrefix("five_hour_"))})"
      case other          => humanize(other)

  // ── the agents as a whole ────────────────────────────────────────────────────────────────────────────

  /** The agents' state, as one control reads it: working; finishing current work, then pausing (draining);
    * paused (the autopilot off). */
  def agentsState(autopilotOn: Boolean, draining: Boolean): String =
    if draining then "Agents: finishing current work, then pausing"
    else if autopilotOn then "Agents: working"
    else "Agents: paused"

  val Drain   = "Finish current work, then pause"
  val Undrain = "Resume"

  // ── background jobs ──────────────────────────────────────────────────────────────────────────────────

  def jobKind(k: JobKind): String = k match
    case JobKind.Ingest           => "Bring in a source"
    case JobKind.Reocr            => "Read a source's text again"
    case JobKind.Init             => "Import reference data"
    case JobKind.MergeApply       => "Merge another collection"
    case JobKind.Vacuum           => "Clean up the database"
    case JobKind.Agent            => "Agent run"
    case JobKind.AcquireRecheck   => "Check what to acquire again"
    case JobKind.BasemapFetch     => "Download the background map"
    case JobKind.PlacesFetch      => "Download a gazetteer"
    case JobKind.PlacesHierarchy  => "Work out which places lie inside which"
    case JobKind.EventsImport     => "Import events"
    case JobKind.EventsCandidates => "Find sources for events"
    case JobKind.EventsRederive   => "Re-date events"
    case JobKind.SiteTypes        => "Look up site types"
    case JobKind.MediaImport      => "Fetch pictures"
    case JobKind.MediaCovers      => "Fetch source covers"

  def jobKindOf(wire: String): String = JobKind.parse(wire).fold(humanize(wire))(jobKind)

  def jobState(s: JobState): String = s match
    case JobState.Queued  => "Queued"
    case JobState.Running => "Running"
    case JobState.Done    => "Done"
    case JobState.Failed  => "Failed"

  // ── plugins ──────────────────────────────────────────────────────────────────────────────────────────

  /** A plugin by its id (`ai-anthropic`, `oa-unpaywall`, `media-europeana`, `events-noaa`): what it is, then
    * its name. */
  def plugin(id: String): String =
    val families = List("ai-" -> "AI provider", "oa-" -> "Open access", "media-" -> "Pictures", "events-" -> "Events")
    families.collectFirst { case (prefix, family) if id.startsWith(prefix) => s"$family: ${humanize(id.stripPrefix(prefix))}" }
      .getOrElse(humanize(id))

  // ── any identifier ───────────────────────────────────────────────────────────────────────────────────

  /** Every identifier the dictionary names, by wire. Where two vocabularies share a word, the more specific
    * function above is the one a view calls; this is what a list's filter chips and every fall-back use. */
  lazy val known: Map[String, String] =
    schedulerJobTypes ++ waitCodes ++ strategies ++
      JobKind.values.map(k => k.wire -> jobKind(k)) ++
      ApprovalKind.values.map(k => k.wire -> approvalKind(k)) ++
      Pipeline.values.map(p => p.wire -> pipeline(p)) ++
      ManagerKind.values.map(k => k.wire -> managerKind(k)) ++
      SourceStatus.values.map(s => s.wire -> status(s)) ++
      ApprovalState.values.map(s => s.wire -> approvalState(s)) ++
      JobState.values.map(s => s.wire -> jobState(s)) ++
      RunState.values.map(s => s.wire -> runState(s))

  /** An identifier as a person reads it: its name in the dictionary, else its words ([[humanize]]). */
  def label(id: String): String = known.getOrElse(id.trim, humanize(id))
