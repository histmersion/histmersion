package com.histmersion.web

import com.histmersion.api.automation.Queue

/** What a window shows — the typed form of an entry of [[UiState.open]]. The wire spelling (`source:ck`, `jobs`, …) is
  * what the URL hash carries, so a reload or a shared link reopens the same windows; everything else in the UI works
  * with the enum.
  */
enum WindowKey:
  /** The server-rendered page of a source. */
  case Source(citekey: WindowKey.Source.Citekey)

  /** The server-rendered page of a compiled article. */
  case Article(slug: WindowKey.Article.Slug)

  /** The citation graph around a source. */
  case Graph(citekey: WindowKey.Graph.Citekey)

  /** The metadata editor of a source. */
  case EditSource(citekey: WindowKey.EditSource.Citekey)

  /** The background (OCR) jobs. */
  case Jobs

  /** Upload a document for OCR ingest. */
  case Upload

  /** The acquisition queue — what to buy, borrow or download. */
  case Acquire

  /** The agent's backlog — the `tasks` queue. */
  case Tasks

  /** The dated worklog — the `log` queue. */
  case Log

  /** Enter the API key that writes need. */
  case ApiKey

  /** The server's build, health, job queue, last backup and store counts. */
  case About

  /** The embedded agent runs (only offered when the server runs agents). */
  case Runs

  /** One agent run: its steps, tokens, cost, trace and controls. */
  case Run(id: WindowKey.Run.Id)

  /** The agents' model chains and allowances, and the provider check. */
  case Models

  /** Changes agent runs proposed, for a person to approve or reject. */
  case Approvals

  /** The research goals, as a tree. */
  case Goals

  /** One goal: its links, schedules, plan and runs. */
  case Goal(slug: WindowKey.Goal.Slug)

  /** The merge review screen: another collection's bundle, its plan, and the decisions. */
  case Merge

  /** The persistent process managers (docs/research-process-managers.md). */
  case Managers

  /** One manager: its state, items in flight, items, hints, events and settings. */
  case Manager(id: WindowKey.Manager.Id)

  def wire: com.histmersion.web.WindowKey.Wire = this match
    case Source(ck)     => com.histmersion.web.WindowKey.Wire(s"source:${ck.value}")
    case Article(slug)  => com.histmersion.web.WindowKey.Wire(s"article:${slug.value}")
    case Graph(ck)      => com.histmersion.web.WindowKey.Wire(s"graph:${ck.value}")
    case EditSource(ck) => com.histmersion.web.WindowKey.Wire(s"edit:${ck.value}")
    case Jobs           => com.histmersion.web.WindowKey.Wire("jobs")
    case Upload         => com.histmersion.web.WindowKey.Wire("upload")
    case Acquire        => com.histmersion.web.WindowKey.Wire("acquire")
    case Tasks          => com.histmersion.web.WindowKey.Wire("tasks")
    case Log            => com.histmersion.web.WindowKey.Wire("log")
    case ApiKey         => com.histmersion.web.WindowKey.Wire("key")
    case About          => com.histmersion.web.WindowKey.Wire("about")
    case Runs           => com.histmersion.web.WindowKey.Wire("runs")
    case Run(id)        => com.histmersion.web.WindowKey.Wire(s"run:${id.value}")
    case Models         => com.histmersion.web.WindowKey.Wire("models")
    case Approvals      => com.histmersion.web.WindowKey.Wire("approvals")
    case Goals          => com.histmersion.web.WindowKey.Wire("goals")
    case Goal(slug)     => com.histmersion.web.WindowKey.Wire(s"goal:${slug.value}")
    case Merge          => com.histmersion.web.WindowKey.Wire("merge")
    case Managers       => com.histmersion.web.WindowKey.Wire("managers")
    case Manager(id)    => com.histmersion.web.WindowKey.Wire(s"manager:${id.value}")

  /** The citekey or slug a window is about, if any. */
  def subject: Option[com.histmersion.api.personal.ItemKey] = this match
    case Source(ck)     => Some(com.histmersion.api.personal.ItemKey(ck.value))
    case Article(slug)  => Some(com.histmersion.api.personal.ItemKey(slug.value))
    case Graph(ck)      => Some(com.histmersion.api.personal.ItemKey(ck.value))
    case EditSource(ck) => Some(com.histmersion.api.personal.ItemKey(ck.value))
    case _              => None

  /** A title to show before the content has told us a better one (the item's own title: the window retitles itself once
    * it is loaded, `WindowManager.retitle`). Sentence case; never an identifier, which the title's tooltip carries.
    */
  def initialTitle: com.histmersion.api.UiLabels.Text = this match
    case Source(_)     => com.histmersion.api.UiLabels.Text("Source")
    case Article(_)    => com.histmersion.api.UiLabels.Text("Article")
    case Graph(_)      => com.histmersion.api.UiLabels.Text("Citation graph")
    case EditSource(_) => com.histmersion.api.UiLabels.Text("Edit a source")
    case Jobs          => com.histmersion.api.UiLabels.Text("Background jobs")
    case Upload        => com.histmersion.api.UiLabels.Text("Upload for OCR")
    case Acquire       => com.histmersion.api.UiLabels.Text("To acquire")
    case Tasks         => com.histmersion.api.UiLabels.Text("Backlog")
    case Log           => com.histmersion.api.UiLabels.Text("Worklog")
    case ApiKey        => com.histmersion.api.UiLabels.Text("API key")
    case About         => com.histmersion.api.UiLabels.Text("About Histmersion")
    case Runs          => com.histmersion.api.UiLabels.Text("Agent runs")
    case Run(_)        => com.histmersion.api.UiLabels.Text("Agent run")
    case Models        => com.histmersion.api.UiLabels.Text("AI models and limits")
    case Approvals     => com.histmersion.api.UiLabels.Text("Approvals")
    case Goals         => com.histmersion.api.UiLabels.Text("Research goals")
    case Goal(slug)    => com.histmersion.api.UiLabels.Text(s"Goal · ${com.histmersion.api.Wording.humanize(com.histmersion.api.Wording.Id(slug.value))}")
    case Merge         => com.histmersion.api.UiLabels.Text("Merge another collection")
    case Managers      => com.histmersion.api.UiLabels.Text("Agent managers")
    case Manager(id)   => com.histmersion.api.UiLabels.Text(com.histmersion.api.Wording.manager(com.histmersion.api.Wording.Id(id.value)))

  /** The page this window mirrors, for "open in a new tab": a source's or an article's standalone page, a run's, a
    * goal's or a manager's page of the web UI.
    */
  def pageHref: Option[String] = Route.ofItem(this).map(_.path)

object WindowKey:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Source:
    type Citekey = com.histmersion.api.library.Citekey
    val Citekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey

  object Article:
    export com.histmersion.api.research.Article.Slug

  object Graph:
    type Citekey = com.histmersion.api.library.Citekey
    val Citekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey

  object EditSource:
    type Citekey = com.histmersion.api.library.Citekey
    val Citekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey

  object Run:
    export com.histmersion.api.automation.Agents.RunInfo.Id

  object Goal:
    export com.histmersion.api.automation.Goal.Slug

  object Manager:
    export com.histmersion.api.automation.Managers.ManagerInfo.Id

  /** The window of each bookkeeping list, and back. */
  def ofQueue(kind: Queue.Kind): WindowKey = kind match
    case Queue.Kind.Acquire => Acquire
    case Queue.Kind.Tasks   => Tasks
    case Queue.Kind.Log     => Log

  /** The bookkeeping list a window shows, if it shows one. */
  def queueOf(key: WindowKey): Option[Queue.Kind] = key match
    case Acquire => Some(Queue.Kind.Acquire)
    case Tasks   => Some(Queue.Kind.Tasks)
    case Log     => Some(Queue.Kind.Log)
    case _       => None

  private val Keyed = "^(source|article|graph|edit|run|goal|manager):(.+)$".r

  /** Pure: a hash entry back into a key; anything unknown is `None` (dropped, not guessed). */
  def parse(raw: com.histmersion.web.WindowKey.Wire): Option[WindowKey] =
    Option(raw).map(_.value.trim).getOrElse("") match
      case Keyed("source", id)  => Some(Source(Source.Citekey(id)))
      case Keyed("article", id) => Some(Article(Article.Slug(id)))
      case Keyed("graph", id)   => Some(Graph(Graph.Citekey(id)))
      case Keyed("edit", id)    => Some(EditSource(EditSource.Citekey(id)))
      case Keyed("run", id)     => Some(Run(Run.Id(id)))
      case Keyed("goal", slug)  => Some(Goal(Goal.Slug(slug)))
      case Keyed("manager", id) => Some(Manager(Manager.Id(id)))
      case "jobs"               => Some(Jobs)
      case "upload"             => Some(Upload)
      case "acquire"            => Some(Acquire)
      case "tasks"              => Some(Tasks)
      case "log"                => Some(Log)
      case "key"                => Some(ApiKey)
      case "about"              => Some(About)
      case "runs"               => Some(Runs)
      case "models"             => Some(Models)
      case "approvals"          => Some(Approvals)
      case "goals"              => Some(Goals)
      case "merge"              => Some(Merge)
      case "managers"           => Some(Managers)
      case _                    => None
