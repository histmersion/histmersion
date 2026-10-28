package com.histmersion.web

import com.histmersion.api.Queue

/** What a window shows — the typed form of an entry of [[UiState.open]]. The wire spelling
  * (`source:ck`, `jobs`, …) is what the URL hash carries, so a reload or a shared link reopens the
  * same windows; everything else in the UI works with the enum. */
enum WindowKey:
  /** The server-rendered page of a source. */
  case Source(citekey: String)
  /** The server-rendered page of a compiled article. */
  case Article(slug: String)
  /** The citation graph around a source. */
  case Graph(citekey: String)
  /** The metadata editor of a source. */
  case EditSource(citekey: String)
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
  case Run(id: String)
  /** The agents' model chains and allowances, and the provider check. */
  case Models
  /** Changes agent runs proposed, for a person to approve or reject. */
  case Approvals
  /** The research goals, as a tree. */
  case Goals
  /** One goal: its links, schedules, plan and runs. */
  case Goal(slug: String)
  /** The merge review screen: another collection's bundle, its plan, and the decisions. */
  case Merge
  /** The persistent process managers (docs/research-process-managers.md). */
  case Managers
  /** One manager: its state, items in flight, items, hints, events and settings. */
  case Manager(id: String)

  def wire: String = this match
    case Source(ck)     => s"source:$ck"
    case Article(slug)  => s"article:$slug"
    case Graph(ck)      => s"graph:$ck"
    case EditSource(ck) => s"edit:$ck"
    case Jobs           => "jobs"
    case Upload         => "upload"
    case Acquire        => "acquire"
    case Tasks          => "tasks"
    case Log            => "log"
    case ApiKey         => "key"
    case About          => "about"
    case Runs           => "runs"
    case Run(id)        => s"run:$id"
    case Models         => "models"
    case Approvals      => "approvals"
    case Goals          => "goals"
    case Goal(slug)     => s"goal:$slug"
    case Merge          => "merge"
    case Managers       => "managers"
    case Manager(id)    => s"manager:$id"

  /** The citekey or slug a window is about, if any. */
  def subject: Option[String] = this match
    case Source(ck)     => Some(ck)
    case Article(slug)  => Some(slug)
    case Graph(ck)      => Some(ck)
    case EditSource(ck) => Some(ck)
    case _              => None

  /** A title to show before the content has told us a better one (the item's own title: the window retitles
    * itself once it is loaded, `WindowManager.retitle`). Sentence case; never an identifier, which the title's
    * tooltip carries. */
  def initialTitle: String = this match
    case Source(_)      => "Source"
    case Article(_)     => "Article"
    case Graph(_)       => "Citation graph"
    case EditSource(_)  => "Edit a source"
    case Jobs           => "Background jobs"
    case Upload         => "Upload for OCR"
    case Acquire        => "To acquire"
    case Tasks          => "Backlog"
    case Log            => "Worklog"
    case ApiKey         => "API key"
    case About          => "About Histmersion"
    case Runs           => "Agent runs"
    case Run(_)         => "Agent run"
    case Models         => "AI models and limits"
    case Approvals      => "Approvals"
    case Goals          => "Research goals"
    case Goal(slug)     => s"Goal · ${com.histmersion.api.Wording.humanize(slug)}"
    case Merge          => "Merge another collection"
    case Managers       => "Agent managers"
    case Manager(id)    => com.histmersion.api.Wording.manager(id)

  /** The standalone page this window mirrors, for "open in a new tab". */
  def pageHref: Option[String] = this match
    case Source(ck)    => Some(SourceLink.href(ck))
    case Article(slug) => Some(s"/article/${com.histmersion.api.HtmlAtoms.segment(slug)}")
    case _             => None

object WindowKey:
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

  /** Pure: the window a page path stands for — `/acquire`, `/tasks`, `/log`, `/merge` — if any. */
  def ofPath(path: String): Option[WindowKey] =
    if path == "/merge" then Some(Merge)
    else Queue.Kind.values.find(k => path == s"/${k.label}").map(ofQueue)

  private val Keyed = "^(source|article|graph|edit|run|goal|manager):(.+)$".r

  /** Pure: a hash entry back into a key; anything unknown is `None` (dropped, not guessed). */
  def parse(raw: String): Option[WindowKey] =
    Option(raw).map(_.trim).getOrElse("") match
      case Keyed("source", id)  => Some(Source(id))
      case Keyed("article", id) => Some(Article(id))
      case Keyed("graph", id)   => Some(Graph(id))
      case Keyed("edit", id)    => Some(EditSource(id))
      case Keyed("run", id)     => Some(Run(id))
      case Keyed("goal", slug)  => Some(Goal(slug))
      case Keyed("manager", id) => Some(Manager(id))
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
