package com.histmersion.web

import com.histmersion.core.Queue

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

  /** The citekey or slug a window is about, if any. */
  def subject: Option[String] = this match
    case Source(ck)     => Some(ck)
    case Article(slug)  => Some(slug)
    case Graph(ck)      => Some(ck)
    case EditSource(ck) => Some(ck)
    case _              => None

  /** A title to show before the content has told us a better one. */
  def initialTitle: String = this match
    case Graph(ck)      => s"graph · $ck"
    case EditSource(ck) => s"edit · $ck"
    case Jobs           => "background jobs"
    case Upload         => "upload for OCR"
    case Acquire        => "acquisition queue"
    case Tasks          => "backlog"
    case Log            => "worklog"
    case ApiKey         => "API key"
    case About          => "about Histmersion"
    case _              => "loading…"

  /** The standalone page this window mirrors, for "open in a new tab". */
  def pageHref: Option[String] = this match
    case Source(ck)    => Some(SourceLink.href(ck))
    case Article(slug) => Some(s"/article/${com.histmersion.core.HtmlAtoms.segment(slug)}")
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

  /** Pure: the window a page path stands for — `/acquire`, `/tasks`, `/log` — if any. */
  def ofPath(path: String): Option[WindowKey] =
    Queue.Kind.values.find(k => path == s"/${k.label}").map(ofQueue)

  private val Keyed = "^(source|article|graph|edit):(.+)$".r

  /** Pure: a hash entry back into a key; anything unknown is `None` (dropped, not guessed). */
  def parse(raw: String): Option[WindowKey] =
    Option(raw).map(_.trim).getOrElse("") match
      case Keyed("source", id)  => Some(Source(id))
      case Keyed("article", id) => Some(Article(id))
      case Keyed("graph", id)   => Some(Graph(id))
      case Keyed("edit", id)    => Some(EditSource(id))
      case "jobs"               => Some(Jobs)
      case "upload"             => Some(Upload)
      case "acquire"            => Some(Acquire)
      case "tasks"              => Some(Tasks)
      case "log"                => Some(Log)
      case "key"                => Some(ApiKey)
      case "about"              => Some(About)
      case _                    => None
