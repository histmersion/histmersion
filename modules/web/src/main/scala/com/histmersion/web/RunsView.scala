package com.histmersion.web

import com.histmersion.api.{Paging, Plural, Wording}
import com.histmersion.api.automation.Agents.{Pipeline, RunDetail, RunInfo, RunState, TargetKind}
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.automation.{Agents, AgentsMarkdown, ListFilters}
import com.histmersion.api.library.{Facet, Label}
import com.histmersion.api.research.Article
import com.histmersion.api.UiLabels.Text

/** The embedded agent runs (docs/research-embedded-agents.md §5.3): the runs window (`GET /api/agents/runs`, filtered
  * on the server through [[UiState.runStatus]], a page at a time: [[ListPaging]]) and one window per run (`GET
  * /api/agents/runs/{id}`: steps, tokens, cost, warnings, the trace, and pause / resume / cancel behind the write
  * gate). Both POLL while something is still queued or running — quickly at first, then backing off, and not at all
  * once everything has settled or the window is closed — the same schedule as the jobs window.
  */
object RunsView:
  /** How many runs a page lists. */

  type Enabled = Enabled.T
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Limit = 100

  /** Whether the server runs agents (`/api/config`); the toolbar offers these windows only then. */
  val enabled: Var[com.histmersion.web.RunsView.Enabled] = Var(com.histmersion.web.RunsView.Enabled(false))

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** A run still moving: worth polling for. (A `waiting` run waits for a person, not for the server.) */
  private def stateWire(status: RunInfo.Status): RunState.Wire = RunState.Wire(status.value)

  def live(status: com.histmersion.api.automation.Agents.RunInfo.Status): Boolean =
    RunState.parse(stateWire(status)).exists(s => s == RunState.Queued || s == RunState.Running)

  /** The polling schedule: `None` = stop (nothing is live); a change goes back to the quick delay. */
  def nextPoll(previous: Option[com.histmersion.web.Poll.Revision], anyLive: com.histmersion.web.Poll.LiveMode, changed: com.histmersion.web.Poll.DataChanged): Option[com.histmersion.web.Poll.Revision] =
    Poll.next(previous, anyLive, changed)

  /** A run state as a person reads it — the filter buttons and the badges say `waiting for approval`, not the wire word
    * `waiting`.
    */
  def stateLabel(s: RunState): com.histmersion.api.UiLabels.Text = Text(Wording.runState(s))

  /** A pipeline as a person reads it ([[Wording.pipeline]]); its identifier stays in the tooltip. */
  def pipelineLabel(pipeline: com.histmersion.api.automation.Agents.RunInfo.Pipeline): com.histmersion.api.UiLabels.Text =
    Pipeline.parse(Pipeline.Wire(pipeline.value)).fold(Text(Wording.humanize(Wording.Id(pipeline.value))))(p => Text(Wording.pipeline(p)))

  /** What each run state means for a person, and what comes next; a tooltip on its filter button. */
  def stateMeaning(s: RunState): com.histmersion.api.UiLabels.Text = s match
    case RunState.Queued  => com.histmersion.api.UiLabels.Text("Waiting for a free place among the runs at once; it starts by itself.")
    case RunState.Running => Text("Its steps are being worked on now; the run's window shows each one.")
    case RunState.Paused  =>
      Text(
        "Stopped for now, resumable: a budget or allowance reached, a server restart, or a pause. " +
          "An allowance pause resumes by itself."
      )
    case RunState.WaitingApproval =>
      Text("Waits for a person to decide its approval; it holds no worker and is queued again once decided.")
    case RunState.Done      => com.histmersion.api.UiLabels.Text("Finished every step.")
    case RunState.Failed    => com.histmersion.api.UiLabels.Text("Ended with the error shown on its row.")
    case RunState.Cancelled => Text("Stopped for good by a person, or because its manager was stopped; it does not resume.")

  /** [[stateLabel]] of a status as stored; an unknown one as words. */
  def statusLabel(status: com.histmersion.api.automation.Agents.RunInfo.Status): com.histmersion.api.UiLabels.Text = RunState.parse(stateWire(status)).fold(Text(Wording.humanize(Wording.Id(status.value))))(stateLabel)

  /** The runs a status filter shows. */
  def visible(runs: List[RunInfo], filter: Option[RunState]): List[RunInfo] =
    filter.fold(runs)(s => runs.filter(r => stateWire(r.status) == s.wire))

  /** The runs a whole filter shows, from the rows already loaded — the state, the pipelines, the origins and the
    * manager; the rest (model, dates, text) is left to the server's page.
    */
  def visible(runs: List[RunInfo], f: ListFilters.RunFilter): List[RunInfo] =
    val n = f.normalized
    runs.filter(r =>
      (n.status.isEmpty || n.status.exists(_.value == r.status.value)) &&
        (n.pipeline.isEmpty || n.pipeline.exists(_.value == r.pipeline.value)) &&
        (n.origin.isEmpty || n.origin.contains(r.origin.getOrElse(Agents.Origin.Person.wire))) &&
        (n.manager.isEmpty || r.managerId.exists(m => n.manager.exists(_.value == m.value)))
    )

  /** What each pipeline does; a tooltip on its filter chip. */
  def pipelineMeaning(pipeline: com.histmersion.api.automation.Agents.RunInfo.Pipeline): Option[com.histmersion.api.UiLabels.Text] = pipeline.value match
    case "plan-goal" => Some(Text("Plans a research goal: sub-goals, search queries, works to read (a goal-plan approval)."))
    case "propose-articles"  => Some(Text("Proposes which articles a goal needs (an article-plan approval)."))
    case "compile-article"   => Some(Text("Writes an article draft from the corpus (an article-publish approval)."))
    case "review-article"    => Some(Text("Reviews an article: rules, cold-reader and skeptic reviewers, then a fixer."))
    case "enrich-source"     => Some(Text("Reads a newly ingested source: summary, findings, dates, places, status."))
    case "acquisition-sweep" =>
      Some(Text("Searches for new literature for a goal; ingests legal open access, queues the rest."))
    case "currency-check" => Some(Text("Checks a goal's sources for retractions and newer critiques."))
    case "infer-filter"   => Some(Text("Works out an article's coverage filter (an article-filter approval)."))
    case "hint-effect"    => Some(Text("Turns your hint to a manager into a proposed action (a hint-effect approval)."))
    case _                => None

  /** Who started a run, for a person: `ingest` is the enrichment an ingest started itself, before the enrich manager
    * took that over (16 Oct 2026); since then those runs are the manager's.
    */
  def originLabel(origin: com.histmersion.api.automation.Agents.Origin.Wire): com.histmersion.api.UiLabels.Text = origin.value match
    case "person"    => com.histmersion.api.UiLabels.Text("by a person")
    case "manager"   => com.histmersion.api.UiLabels.Text("by a manager")
    case "ingest"    => com.histmersion.api.UiLabels.Text("by an ingest (before the enrich manager)")
    case "run"       => com.histmersion.api.UiLabels.Text("by another run")
    case "scheduler" => Text("by a goal's schedule")
    case other       => Text(Label.display(Label.Text(other)).value)

  /** Who started a run, in a sentence; a tooltip on its filter chip. */
  def originMeaning(origin: com.histmersion.api.automation.Agents.Origin.Wire): Option[com.histmersion.api.UiLabels.Text] = Agents.Origin.values.find(_.wire == origin).map {
    case Agents.Origin.Person    => com.histmersion.api.UiLabels.Text("Started by a person: from this page, the CLI or an MCP tool.")
    case Agents.Origin.Scheduler =>
      Text("Started by a goal's schedule; nobody watches it, so a failure or a pause leaves a backlog item.")
    case Agents.Origin.Run =>
      Text("Started by another run, such as a compile-article run starting the review of its article.")
    case Agents.Origin.FromIngest =>
      Text("Queued by an ingest for the source it brought in (automatic enrichment), before the enrich manager took that over.")
    case Agents.Origin.Manager => com.histmersion.api.UiLabels.Text("Dispatched by a process manager for one of its work items.")
  }

  /** Who serves a model, for a person; a tooltip on the provider menu's options. A provider a plugin registered has no
    * meaning here.
    */
  def providerMeaning(provider: com.histmersion.api.automation.Agents.ProviderDoctor.Provider): Option[com.histmersion.api.UiLabels.Text] = provider.value match
    case "anthropic" => Some(Text("Anthropic's API, with an API key: metered, billed per token."))
    case "openai"    => Some(Text("OpenAI's API, with an API key: metered, billed per token."))
    case "local" => Some(Text("A model behind an OpenAI-compatible endpoint you run (llama.cpp, LM Studio, vLLM, Ollama)."))
    case "claude-code" =>
      Some(Text("Your own claude command-line tool on your Claude login: your plan's usage, not an API key."))
    case "codex"    => Some(com.histmersion.api.UiLabels.Text("Your own codex command-line tool on its ChatGPT plan login."))
    case "opencode" => Some(Text("The opencode command-line tool; its model is named as opencode names it (openai/…)."))
    case _          => None

  /** The query the window sends, from its filter fields. Pure. */
  def filterOf(
      status: Option[RunState],
      pipelines: List[ListFilters.RunFilter.Pipeline],
      origins: List[ListFilters.RunFilter.Origin],
      manager: Option[ListFilters.RunFilter.Manager],
      provider: Option[ListFilters.RunFilter.Provider],
      model: Option[ListFilters.RunFilter.Model],
      goal: Option[com.histmersion.api.automation.Goal.Slug],
      since: Option[ListFilters.RunFilter.Since],
      until: Option[ListFilters.RunFilter.Until],
      subject: Option[ListFilters.RunFilter.Subject]
  ): ListFilters.RunFilter =
    ListFilters.RunFilter(
      status.toList.map(s => ListFilters.RunFilter.Status(s.wire.value)),
      pipelines,
      manager.toList,
      goal,
      model.toList,
      provider.toList,
      origins,
      since,
      until,
      subject
    )

  def counts(runs: List[RunInfo]): Map[com.histmersion.api.automation.Agents.RunInfo.Status, com.histmersion.api.RowCount] =
    runs.groupBy(_.status).map((status, rs) => status -> com.histmersion.api.RowCount(rs.size))

  /** Is any run still queued or running — anywhere (the server's counts), not just among the rows a filter shows; the
    * rows' own states when the server sent no counts.
    */
  def anyLive(rows: List[RunInfo], page: Paging.Page): Boolean =
    if page.counts.isEmpty then rows.exists(r => live(r.status))
    else
      page.count(Paging.Page.CountState(RunState.Queued.wire.value)).value +
        page.count(Paging.Page.CountState(RunState.Running.wire.value)).value > 0

  /** What a person can do to a run in this state. */
  enum Action(val wire: Action.Wire, val label: Action.Label):
    case Pause extends Action(com.histmersion.web.RunsView.Action.Wire("pause"), com.histmersion.web.RunsView.Action.Label("pause"))
    case Resume extends Action(com.histmersion.web.RunsView.Action.Wire("resume"), com.histmersion.web.RunsView.Action.Label("resume"))
    case Cancel extends Action(com.histmersion.web.RunsView.Action.Wire("cancel"), com.histmersion.web.RunsView.Action.Label("cancel"))

  object Action:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = com.histmersion.api.UiLabels.Text
    val Label: _root_.com.histmersion.api.UiLabels.Text.type = _root_.com.histmersion.api.UiLabels.Text

  def actions(status: com.histmersion.api.automation.Agents.RunInfo.Status): List[Action] = RunState.parse(stateWire(status)) match
    case Some(RunState.Queued) | Some(RunState.Running) => List(Action.Pause, Action.Cancel)
    case Some(RunState.Paused)                          => List(Action.Resume, Action.Cancel)
    // a run waiting for an approval goes on when the approval is decided; it can only be cancelled
    case Some(RunState.WaitingApproval) => List(Action.Cancel)
    case _                              => Nil

  private val WaitingOn = """approval (\d+)""".r.unanchored

  /** The approval a waiting run waits for, from its progress line (`approve: waiting for approval 12`). */
  def waitingFor(r: RunInfo): Option[Agents.ApprovalInfo.Id] =
    if !r.isWaiting then None
    else r.progress.map(_.value).collect { case WaitingOn(id) => Agents.ApprovalInfo.Id(id.toLong) }

  /** Why a paused run stopped, from its `error` (`budget: …`, `provider: …`, `interrupted`). */
  def pausedFor(r: RunInfo): Option[Agents.PauseReason.Wire] =
    Option.when(stateWire(r.status) == RunState.Paused.wire)(
      Agents.PauseReason.Wire(r.error.map(_.value.takeWhile(_ != ':').trim).getOrElse("requested"))
    )

  /** What a paused run's window says about its pause. An allowance pause ends by itself. */
  def pausedText(r: RunInfo, why: com.histmersion.api.automation.Agents.PauseReason.Wire): com.histmersion.api.UiLabels.Text =
    Text(
      if why == Agents.PauseReason.Budget.wire then s"Paused: $why. Raise the budget to resume."
      else if why == Agents.PauseReason.Allowance.wire then
        s"Paused: every model of a role is below its allowance; ${r.resumeAt.fold(
            "it resumes once an allowance is raised"
          )(t => s"it resumes by itself at $t")}."
      else if why == Agents.PauseReason.PlanQuota.wire then
        s"Paused: a subscription is below its floor (or held), and the chain does not fall through to a paid key; " +
          s"${r.resumeAt.fold("it resumes once the hold is released or a floor is lowered")(t =>
              s"it resumes by itself at $t"
            )}."
      else s"Paused: $why."
    )

  /** A budget the person typed: a positive number of dollars, or nothing. */
  def budget(s: com.histmersion.api.UiLabels.Text): Option[com.histmersion.api.automation.Agents.RunRequest.BudgetUsd] = s.value.trim.toDoubleOption.filter(_ > 0).map(Agents.RunRequest.BudgetUsd(_))

  def tokens(u: Agents.Usage): com.histmersion.api.UiLabels.Text = Text(s"${u.input} in / ${u.output} out")

  /** The columns of a run's steps table, in order. */
  val StepColumns: List[com.histmersion.api.UiLabels.Text] =
    List("#", "step", "kind", "try", "status", "model", "tokens", "USD", "note").map(Text(_))

  /** One step's cells, each with its column's name (the label a narrow card shows). Pure. */
  def stepCells(s: Agents.StepInfo): List[(com.histmersion.api.UiLabels.Text, com.histmersion.api.UiLabels.Text)] =
    StepColumns.zip(
      List(
        s.seq.toString,
        s.name.value,
        s.kind.value,
        s.attempt.toString,
        s.status.value,
        s.modelText,
        s"${s.usage.input}/${s.usage.output}",
        AgentsMarkdown.usd(s.costUsd.value),
        (s.error.toList.map(_.value) ++ s.wouldWrite.map(w => s"${AgentsMarkdown.DryRunNote}: ${w.what}")).mkString("; ")
      ).map(Text(_))
    )

  // ── the runs window ─────────────────────────────────────────────────────────────────────────────

  /** The window's filter fields (they live in the URL hash). */
  private val filterFields: List[Field[?]] =
    List(
      UiState.runStatus,
      UiState.runPipelines,
      UiState.runOrigins,
      UiState.runManager,
      UiState.runProvider,
      UiState.runModel,
      UiState.runGoal,
      UiState.runSince,
      UiState.runUntil,
      UiState.runSubject
    )

  private def currentFilter(): ListFilters.RunFilter =
    filterOf(
      UiState.runStatus.now(),
      UiState.runPipelines.now().map(ListFilters.RunFilter.Pipeline(_)),
      UiState.runOrigins.now().map(Agents.Origin.Wire(_)),
      UiState.runManager.now().map(ListFilters.RunFilter.Manager(_)),
      UiState.runProvider.now().map(ListFilters.RunFilter.Provider(_)),
      UiState.runModel.now().map(ListFilters.RunFilter.Model(_)),
      UiState.runGoal.now().map(com.histmersion.api.automation.Goal.Slug(_)),
      UiState.runSince.now().map(ListFilters.RunFilter.Since(_)),
      UiState.runUntil.now().map(ListFilters.RunFilter.Until(_)),
      UiState.runSubject.now().map(ListFilters.RunFilter.Subject(_))
    )

  /** The filter, as a signal (every field but the state is its own), so a change restarts the list. */
  private val filterSignal: Signal[ListFilters.RunFilter] =
    UiState.signal.map(_ => currentFilter()).distinct

  /** The rows, the poll and "Show older" ([[PolledList]]). */
  private val list = PolledList[RunInfo](
    fetch = (upTo, before) => ApiClient.agentRuns(currentFilter(), Some(Paging.Limit(Limit)), upTo = upTo, before = before),
    id = r => PolledList.ItemId(r.id.value),
    live = (rows, page) => anyLive(rows, page)
  )
  private def listing = list.listing

  private def facet(name: Facet.Name): Signal[List[(Facet.Value, Facet.Count)]] = listing.signal.map(_.page.facet(name))

  def pane: HtmlElement =
    div(
      cls := "tool-pane runs-pane",
      list.lifecycle,
      filterSignal.changes --> (_ => list.restart()),
      child.maybe <-- enabled.signal.map(on =>
        Option.when(!on.value)(
          p(cls := "muted", "Embedded agents are off on this server (agents.enabled). Earlier runs are still listed.")
        )
      ),
      div(
        cls := "pane-bar",
        filterButtons,
        button(typ := "button", cls := "btn", "refresh", onClick --> (_ => list.restart())),
        button(
          typ := "button",
          cls := "btn",
          title := "each role's model chain, the allowances, the provider check",
          "models & allowances",
          onClick --> (_ => WindowManager.open(WindowKey.Models))
        )
      ),
      div(
        cls := "toolbar list-filters",
        ListFilterBar.chips(
          "pipeline",
          facet(Facet.Name("pipeline")),
          UiState.runPipelines,
          hint = v => pipelineMeaning(RunInfo.Pipeline(v)).map(_.value),
          about = "Show only runs of the pipelines picked (any of them): what kind of work the run does."
        ),
        ListFilterBar.chips(
          "origin",
          facet(Facet.Name("origin")),
          UiState.runOrigins,
          v => originLabel(Agents.Origin.Wire(v)).value,
          v => originMeaning(Agents.Origin.Wire(v)).map(_.value),
          about = "Show only runs started the ways picked (any of them)."
        ),
        div(
          cls := "btn-group",
          ListFilterBar.picker(
            "manager",
            facet(Facet.Name("manager")),
            UiState.runManager,
            show = v => Wording.manager(Wording.ManagerId(v)),
            hint = v => ManagersView.managerMeaning(com.histmersion.api.automation.Managers.ManagerInfo.Id(v)).map(_.value),
            about = "Show only the runs one process manager dispatched."
          ),
          ListFilterBar.picker(
            "provider",
            facet(Facet.Name("provider")),
            UiState.runProvider,
            hint = v => providerMeaning(Agents.ProviderDoctor.Provider(v)).map(_.value),
            about = "Show only runs that used a model of this provider: started with it, or reported by a step."
          ),
          // a model's name is its own (provider:model), not an identifier of ours
          ListFilterBar.picker(
            "model",
            facet(Facet.Name("model")),
            UiState.runModel,
            show = identity,
            about = "Show only runs that used this model (provider:model): started with it, or reported by a step."
          )
        ),
        div(
          cls := "btn-group",
          ListFilterBar.text("goal", UiState.runGoal),
          ListFilterBar.text("target: a citekey or slug", UiState.runSubject),
          ListFilterBar.day("started on or after", UiState.runSince),
          ListFilterBar.day("started on or before", UiState.runUntil),
          ListFilterBar.clearButton(filterFields)
        )
      ),
      list.errorLine(com.histmersion.api.UiLabels.Text("Could not list runs")),
      child.maybe <-- shownSignal.combineWith(list.loaded.signal).map { (l, filter, isLoaded) =>
        Option.when(isLoaded.value && l.rows.nonEmpty)(
          p(
            cls := "muted list-heading",
            ListPaging.heading(
              l,
              com.histmersion.api.Plural.Noun.Singular("run"),
              filter.map(_.wire.value),
              _.status.value,
              filter.map(f => stateLabel(f).value).toList ++
                currentFilter().normalized.pipeline.map(p => pipelineLabel(RunInfo.Pipeline(p.value)).value)
            )
          )
        )
      },
      child.maybe <-- shownSignal.combineWith(list.loaded.signal).map { (l, filter, isLoaded) =>
        if !isLoaded.value then Some(p(cls := "muted", "Loading…"))
        else
          Option.when(l.rows.isEmpty)(
            p(
              cls := "muted",
              if ListFilterBar.anySet(filterFields.drop(1)) then "No runs match the filter."
              else filter.fold("No agent runs yet.")(s => s"No runs ${stateLabel(s).value.toLowerCase}.")
            )
          )
      },
      // keyed by the row itself: an unchanged row keeps its element while new ones arrive on top
      ul(
        cls := "job-list run-list",
        aria.busy <-- list.loading,
        children <-- shownSignal.map(_._1.rows).split(identity)((r, _, _) => row(r))
      ),
      list.olderButton
    )

  /** The listing with the rows the filter shows — at once, from the rows loaded, when the filter changes; the server's
    * page replaces them a moment later.
    */
  private def shownSignal: Signal[(ListPaging.Listing[RunInfo], Option[RunState])] =
    listing.signal.combineWith(filterSignal).map((l, f) => (l.copy(rows = visible(l.rows, f)), UiState.runStatus.now()))

  private def filterButtons: HtmlElement =
    def btn(label: com.histmersion.api.UiLabels.Text, value: Option[RunState], count: Signal[Int]) =
      button(
        typ := "button",
        cls := "btn btn--toggle",
        title := value.fold("Every run, whatever its state.")(s => stateMeaning(s).value),
        aria.pressed <-- UiState.runStatus.signal.map(v => (v == value).toString),
        span(label.value),
        child.text <-- count.map(n => if n > 0 then s" ${Paging.grouped(Paging.GroupSize(n.toLong))}" else ""),
        onClick --> (_ => UiState.set(UiState.runStatus := value)())
      )
    div(
      cls := "btn-group jobs-filter",
      btn(com.histmersion.api.UiLabels.Text("All"), None, listing.signal.map(l => ListPaging.count(l, None, _.status.value))),
      RunState.values.toList.map(s =>
        btn(stateLabel(s), Some(s), listing.signal.map(l => ListPaging.count(l, Some(s.wire.value), _.status.value)))
      )
    )

  private def stateBadge(status: RunInfo.Status): HtmlElement =
    span(cls := s"job-state state-$status", title := statusLabel(status).value, statusLabel(status).value)

  /** A dry run (#992) is marked wherever the run is shown: it wrote nothing. */
  private def dryBadge(r: RunInfo): Option[HtmlElement] =
    Option.when(r.dryRun.value)(
      span(cls := "job-state state-dry-run", title := "Dry run: nothing was written to the corpus", "Dry run")
    )

  private def target(r: RunInfo): HtmlElement =
    TargetKind.parse(TargetKind.Wire(r.targetKind.value)) match
      case Some(TargetKind.Article) =>
        com.histmersion.web.ArticleLink(Article.Slug(r.target.value), r.target.value)
      case Some(TargetKind.Goal) =>
        WinLink(
          WindowKey.Goal(WindowKey.Goal.Slug(r.target.value)),
          title := r.target.value,
          s"Goal: ${Wording.humanize(Wording.Id(r.target.value))}"
        )
      case Some(TargetKind.Manager) =>
        WinLink(
          WindowKey.Manager(WindowKey.Manager.Id(r.target.value)),
          title := r.target.value,
          Wording.manager(Wording.ManagerId(r.target.value))
        )
      case _ => SourceLink(com.histmersion.api.library.Citekey(r.target.value))

  private def row(r: RunInfo): HtmlElement =
    li(
      cls := s"job-row run-row job-${r.status}",
      stateBadge(r.status),
      dryBadge(r),
      div(
        cls := "job-main",
        div(
          cls := "job-subject",
          WinLink(WindowKey.Run(WindowKey.Run.Id(r.id.value)), title := s"Open this run (${r.pipeline})", pipelineLabel(r.pipeline).value),
          " · ",
          target(r),
          Option(AgentsMarkdown.options(r)).filter(_.nonEmpty).map(o => span(cls := "muted", s" · $o"))
        ),
        r.progress.filter(_.value.nonEmpty).map(pr => div(cls := "job-progress", pr.value)),
        r.error.filter(_.value.nonEmpty).map(e => div(cls := "job-error", e.value)),
        Option.when(r.warnings.nonEmpty)(
          div(cls := "run-warn-count muted", s"${Plural.count(Plural.CountN(r.warnings.size.toLong), com.histmersion.api.Plural.CountSingular("warning"))}")
        ),
        div(
          cls := "job-meta muted",
          span(title := s"run ${r.id}", s"run ${r.id.value.take(8)}"),
          span(s" · ${tokens(r.usage)} · ${AgentsMarkdown.usd(r.costUsd.value)} of ${AgentsMarkdown.usd(r.budgetUsd.value)} USD"),
          Option(r.createdAt).filter(_.value.nonEmpty).map(c => span(title := c.value, s" · started ${ago(c.value)}"))
        )
      )
    )

  private def ago(iso: String): String =
    val t = js.Date.parse(iso)
    if iso.isEmpty || t.isNaN then "" else JobsView.ago(t, js.Date.now())

  // ── one run ─────────────────────────────────────────────────────────────────────────────────────

  /** One run's window. Its state is its own (several runs can be open at once). */
  def runPane(id: com.histmersion.api.automation.Agents.RunInfo.Id): HtmlElement =
    val busy = Var(false)
    val raise = Var("")
    // the poll, the closed-window and stale-answer guards and the error: the list windows' controller
    val run = PolledList.single[RunDetail](
      fetch = () => ApiClient.agentRun(ApiClient.RunId(id.value)),
      live = d => live(d.run.status),
      // a run that just settled (or started waiting) may have opened approvals
      after = (before, d) =>
        if before.exists(b => live(b.run.status)) && !live(d.run.status) then ApprovalsView.changed.emit(())
    )

    def refresh(changed: com.histmersion.web.Poll.DataChanged): Unit = run.refresh(changed)

    def act(a: Action): Unit =
      busy.set(true)
      val usd = if a == Action.Resume then budget(Text(raise.now())) else None
      ApiClient
        .agentControl(
          ApiClient.AgentId(id.value),
          com.histmersion.api.Api.JobControl.Action(a.wire.value),
          usd.map(b => ApiClient.BudgetUsd(b.value))
        )
        .onComplete { r =>
          busy.set(false)
          r.failed.foreach(e => run.failure.set(Some(PolledList.Problem(e.getMessage))))
          if r.isSuccess then raise.set("")
          refresh(changed = com.histmersion.web.Poll.DataChanged(true))
        }

    div(
      cls := "tool-pane run-pane",
      run.lifecycle,
      ErrorLine(run.failure.signal.map(_.map(m => ErrorLine.Problem(m.value)))),
      child <-- run.item.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(d) => runBody(d, busy.signal, raise, act)
      },
      QueueView.ofJob(Pages.JobId(id.value)),
      run.item.changes.collect { case Some(d) => d.run.pipeline } --> (pl =>
        WindowManager.retitle(
          WindowKey.Run(WindowKey.Run.Id(id.value)),
          com.histmersion.api.UiLabels.Text(s"Agent run · ${pipelineLabel(pl).value}")
        )
      )
    )

  private def runBody(d: RunDetail, busy: Signal[Boolean], raise: Var[String], act: Action => Unit): HtmlElement =
    val r = d.run
    div(
      cls := "run-detail",
      div(
        cls := "run-head",
        stateBadge(r.status),
        dryBadge(r),
        span(cls := "run-title", title := r.pipeline.value, s" ${pipelineLabel(r.pipeline).value} · "),
        target(r),
        Option(AgentsMarkdown.options(r)).filter(_.nonEmpty).map(o => span(cls := "muted", s" ($o)"))
      ),
      pausedFor(r).map(why =>
        p(
          cls := "run-paused",
          pausedText(r, why).value,
          Option
            .when(why == Agents.PauseReason.Allowance.wire)(span(" ", WinLink(WindowKey.Models, "models & allowances")))
        )
      ),
      Option.when(r.isWaiting)(
        p(
          cls := "run-waiting",
          s"Waiting for a person to decide ${waitingFor(r).fold("its approval")(id => s"approval #$id")}; it goes on by itself once decided. ",
          WinLink(WindowKey.Approvals, "open the approvals")
        )
      ),
      r.goal.map(g =>
        p(
          cls := "muted",
          "For the goal ",
          WinLink(WindowKey.Goal(WindowKey.Goal.Slug(g.value)), title := g.value, Wording.humanize(Wording.Id(g.value)))
        )
      ),
      r.error.filter(e => e.value.nonEmpty && stateWire(r.status) != RunState.Paused.wire).map(e => p(cls := "job-error", e.value)),
      r.progress.filter(_.value.nonEmpty).map(pr => p(cls := "job-progress", pr.value)),
      dl(
        cls := "run-facts",
        dt("spent"),
        dd(s"${tokens(r.usage)} · ${AgentsMarkdown.usd(r.costUsd.value)} of ${AgentsMarkdown.usd(r.budgetUsd.value)} USD"),
        dt("model"),
        dd(r.model.fold("the configured roles")(_.value)),
        dt("run"),
        dd(code(r.id.value)),
        dt("started"),
        dd(r.createdAt.value),
        dt("updated"),
        dd(r.updatedAt.value)
      ),
      controls(r, busy, raise, act),
      Option.when(r.warnings.nonEmpty)(div(cls := "run-warnings", h4("Warnings"), ul(r.warnings.map(w => li(w.value))))),
      h4("Steps"),
      table(
        cls := "run-steps",
        thead(tr(StepColumns.map(c => th(c.value)))),
        tbody(d.steps.map { s =>
          // each cell names its column: the narrow layout drops the header and shows every step as
          // a card of "column value" lines (an empty cell is left out there)
          tr(
            cls := s"step-${s.status}",
            stepCells(s).map { (col, v) =>
              td(dataAttr("label") := col.value, cls("step-note") := col.value == "note", cls("is-empty") := v.value.isEmpty, v.value)
            }
          )
        })
      ),
      Option.when(d.messages.nonEmpty)(
        div(
          cls := "run-trace",
          h4("Trace"),
          d.messages.groupBy(_.seq).toList.sortBy(t => t._1.value: Int).map { (seq, ms) =>
            val name = d.steps.find(_.seq == seq).map(_.name.value).getOrElse(s"step $seq")
            htmlTag("details")(
              htmlTag("summary")(s"$name — ${Plural.count(Plural.CountN(ms.size.toLong), Plural.CountSingular("message"))}"),
              ms.map { m =>
                div(
                  cls := s"trace-msg trace-${m.role}",
                  div(
                    cls := "trace-head muted",
                    s"${m.role}${m.toolName.fold("")(t => s" · $t")}${m.latencyMs.fold("")(l => s" · $l ms")}"
                  ),
                  pre(cls := "trace-body", m.content.map(_.value).orElse(m.toolInput.map(_.value)).orElse(m.toolResult.map(_.value)).getOrElse(""))
                )
              }
            )
          }
        )
      )
    )

  private def controls(r: RunInfo, busy: Signal[Boolean], raise: Var[String], act: Action => Unit): HtmlElement =
    val acts = actions(r.status)
    div(
      cls := "form-row run-controls",
      child <-- Auth.writable.map { w =>
        if acts.isEmpty then span()
        else if !w then Auth.keyNeeded("control this run")
        else
          div(
            cls := "form-row",
            Option.when(acts.contains(Action.Resume))(
              input(
                typ := "text",
                cls := "field run-budget",
                aria.label := "new budget in USD",
                placeholder := s"budget USD (now ${AgentsMarkdown.usd(r.budgetUsd.value)})",
                controlled(value <-- raise.signal, onInput.mapToValue --> raise.writer)
              )
            ),
            acts.map(a =>
              button(
                typ := "button",
                cls := (if a == Action.Cancel then "btn" else "btn btn--primary"),
                disabled <-- busy,
                a.label.value,
                onClick --> (_ => act(a))
              )
            )
          )
      }
    )
