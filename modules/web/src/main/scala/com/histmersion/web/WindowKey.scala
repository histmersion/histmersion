package com.histmersion
package web

import com.histmersion.api.automation.Queue
import com.histmersion.api.personal.ItemKey
import com.histmersion.api.UiLabels
import com.histmersion.api.Wording
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What a window shows — the typed form of an entry of [[UiState.open]]. The wire spelling (`source:ck`, `jobs`, …) is
  * what the URL hash carries, so a reload or a shared link reopens the same windows; everything else in the UI works
  * with the enum.
  */
enum WindowKey derives CanEqual:
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: WindowKey.Wire = this match
    case Source(ck)     => WindowKey.Wire(s"source:${ck.value}")
    case Article(slug)  => WindowKey.Wire(s"article:${slug.value}")
    case Graph(ck)      => WindowKey.Wire(s"graph:${ck.value}")
    case EditSource(ck) => WindowKey.Wire(s"edit:${ck.value}")
    case Jobs           => WindowKey.Wire("jobs")
    case Upload         => WindowKey.Wire("upload")
    case Acquire        => WindowKey.Wire("acquire")
    case Tasks          => WindowKey.Wire("tasks")
    case Log            => WindowKey.Wire("log")
    case ApiKey         => WindowKey.Wire("key")
    case About          => WindowKey.Wire("about")
    case Runs           => WindowKey.Wire("runs")
    case Run(id)        => WindowKey.Wire(s"run:${id.value}")
    case Models         => WindowKey.Wire("models")
    case Approvals      => WindowKey.Wire("approvals")
    case Goals          => WindowKey.Wire("goals")
    case Goal(slug)     => WindowKey.Wire(s"goal:${slug.value}")
    case Merge          => WindowKey.Wire("merge")
    case Managers       => WindowKey.Wire("managers")
    case Manager(id)    => WindowKey.Wire(s"manager:${id.value}")

  /** The citekey or slug a window is about, if any. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def subject: Option[ItemKey] = this match
    case Source(ck)     => Some(ItemKey(ck.value))
    case Article(slug)  => Some(ItemKey(slug.value))
    case Graph(ck)      => Some(ItemKey(ck.value))
    case EditSource(ck) => Some(ItemKey(ck.value))
    case _              => None

  /** A title to show before the content has told us a better one (the item's own title: the window retitles itself once
    * it is loaded, `WindowManager.retitle`). Sentence case; never an identifier, which the title's tooltip carries.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def initialTitle: UiLabels.Text = this match
    case Source(_)     => UiLabels.Text("Source")
    case Article(_)    => UiLabels.Text("Article")
    case Graph(_)      => UiLabels.Text("Citation graph")
    case EditSource(_) => UiLabels.Text("Edit a source")
    case Jobs          => UiLabels.Text("Background jobs")
    case Upload        => UiLabels.Text("Upload for OCR")
    case Acquire       => UiLabels.Text("To acquire")
    case Tasks         => UiLabels.Text("Backlog")
    case Log           => UiLabels.Text("Worklog")
    case ApiKey        => UiLabels.Text("API key")
    case About         => UiLabels.Text("About Histmersion")
    case Runs          => UiLabels.Text("Agent runs")
    case Run(_)        => UiLabels.Text("Agent run")
    case Models        => UiLabels.Text("AI models and limits")
    case Approvals     => UiLabels.Text("Approvals")
    case Goals         => UiLabels.Text("Research goals")
    case Goal(slug)    => UiLabels.Text(s"Goal · ${Wording.humanize(Wording.Id(slug.value))}")
    case Merge         => UiLabels.Text("Merge another collection")
    case Managers      => UiLabels.Text("Agent managers")
    case Manager(id)   => UiLabels.Text(Wording.manager(Wording.Id(id.value)))

  /** The page this window mirrors, for "open in a new tab": a source's or an article's standalone page, a run's, a
    * goal's or a manager's page of the web UI.
    */
  def pageHref: Option[String] = Route.ofItem(this).map(_.path)

object WindowKey:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  object Source:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Citekey = api.library.Citekey
    val Citekey: api.library.Citekey.type = api.library.Citekey

  object Article:
    export api.research.Article.Slug

  object Graph:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Citekey = api.library.Citekey
    val Citekey: api.library.Citekey.type = api.library.Citekey

  object EditSource:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Citekey = api.library.Citekey
    val Citekey: api.library.Citekey.type = api.library.Citekey

  object Run:
    export api.automation.Agents.RunInfo.Id

  object Goal:
    export api.automation.Goal.Slug

  object Manager:
    export api.automation.Managers.ManagerInfo.Id

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(raw: Wire): Option[WindowKey] =
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
