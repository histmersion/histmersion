package com.histmersion.web

import com.histmersion.api.HtmlAtoms
import com.histmersion.api.automation.Queue

import scala.scalajs.js

/** A page of the web UI, by its address. Every page and every item has a real path, so each can be bookmarked, shared,
  * and opened in a new tab; the open windows and the filters ride along in the hash ([[UiState]]). `/source/…` and
  * `/article/…` are the server's own standalone pages. Pure.
  */
enum Route:
  case Explore
  case Library(section: LibrarySection)
  case Research(section: ResearchSection)
  case Activity(tab: ActivityTab)
  case Goal(slug: Goal.Slug)
  case Settings(section: SettingsSection)

  /** The first-run setup, run again from Settings. */
  case Setup
  case Source(citekey: Source.Citekey)
  case Article(slug: Article.Slug)
  case Place(id: Place.Id)
  case Event(id: Event.Id)
  case Run(id: Run.Id)
  case Manager(id: Manager.Id)
  case Job(id: Job.Id)

  /** The address of the page — without the hash, which carries the filters and the open windows. */
  def path: com.histmersion.web.Route.PathResult = this match
    case Explore       => "/explore"
    case Library(s)    => sub("/library", s.slug)
    case Research(s)   => sub("/research", s.slug)
    case Activity(t)   => sub("/research/activity", t.slug)
    case Goal(slug)    => s"/research/goals/${HtmlAtoms.segment(slug)}"
    case Settings(s)   => sub("/settings", s.slug)
    case Setup         => "/setup"
    case Source(ck)    => SourceLink.href(ck)
    case Article(slug) => ArticleLink.href(slug)
    case Place(id)     => s"/place/${HtmlAtoms.segment(id)}"
    case Event(id)     => s"/event/${HtmlAtoms.segment(id)}"
    case Run(id)       => s"/run/${HtmlAtoms.segment(id)}"
    case Manager(id)   => s"/manager/${HtmlAtoms.segment(id)}"
    case Job(id)       => s"/job/${HtmlAtoms.segment(id)}"

  private def sub(base: com.histmersion.web.Route.SubBase, slug: com.histmersion.web.Route.SubSlug): com.histmersion.web.Route.SubResult = if slug.isEmpty then base else s"$base/$slug"

  /** The top bar's tab this page sits under. */
  def place: PlaceTab = this match
    case Explore | Place(_) | Event(_)                                      => PlaceTab.Explore
    case Library(_) | Source(_) | Article(_)                                => PlaceTab.Library
    case Research(_) | Activity(_) | Goal(_) | Run(_) | Manager(_) | Job(_) => PlaceTab.Research
    case Settings(_) | Setup                                                => PlaceTab.Settings

  /** Whether the server renders this page itself (a full page load), rather than the web UI. */
  def serverPage: com.histmersion.web.Route.ServerPageResult = this match
    case Source(_) | Article(_) => true
    case _                      => false

  /** The section of the place's side navigation this page is under, if it has one. */
  def researchSection: Option[ResearchSection] = this match
    case Research(s)                   => Some(s)
    case Activity(_) | Run(_) | Job(_) => Some(ResearchSection.Activity)
    case Manager(_)                    => Some(ResearchSection.Overview)
    case Goal(_)                       => Some(ResearchSection.Goals)
    case _                             => None

object Route:

  /** The front page of each place. */
  object Goal:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Source:
    type Citekey = Citekey.T
    object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Article:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Place:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Event:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Run:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Manager:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Job:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PathResult = PathResult.T
  object PathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubResult = SubResult.T
  object SubResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubBase = SubBase.T
  object SubBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubSlug = SubSlug.T
  object SubSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServerPageResult = ServerPageResult.T
  object ServerPageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentsResult = SegmentsResult.T
  object SegmentsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentsPath = SegmentsPath.T
  object SegmentsPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResult = DecodeResult.T
  object DecodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeS = DecodeS.T
  object DecodeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParsePath = ParsePath.T
  object ParsePath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HrefOfResult = HrefOfResult.T
  object HrefOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueItemResult = QueueItemResult.T
  object QueueItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueItemId = QueueItemId.T
  object QueueItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyPath = LegacyPath.T
  object LegacyPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAppPathResult = IsAppPathResult.T
  object IsAppPathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAppPathPath = IsAppPathPath.T
  object IsAppPathPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def front(p: PlaceTab): Route = p match
    case PlaceTab.Explore  => Explore
    case PlaceTab.Library  => Library(LibrarySection.Sources)
    case PlaceTab.Research => Research(ResearchSection.Overview)
    case PlaceTab.Settings => Settings(SettingsSection.General)

  private def segments(path: com.histmersion.web.Route.SegmentsPath): List[com.histmersion.web.Route.SegmentsResult] =
    Option(path).getOrElse("").split('/').toList.filter(_.nonEmpty).map(decode)

  private def decode(s: com.histmersion.web.Route.DecodeS): com.histmersion.web.Route.DecodeResult =
    try js.URIUtils.decodeURIComponent(s)
    catch case _: Throwable => s

  /** The page an address stands for; `None` for one the web UI does not know. The root and the old addresses of the
    * lists (`/acquire`, `/tasks`, `/log`, `/merge`) are [[legacy]]'s.
    */
  def parse(path: com.histmersion.web.Route.ParsePath): Option[Route] =
    segments(path) match
      case List("explore")   => Some(Explore)
      case "library" :: rest =>
        rest match
          case Nil     => Some(Library(LibrarySection.Sources))
          case List(s) => LibrarySection.values.find(x => x.slug == s && s.nonEmpty).map(Library(_))
          case _       => None
      case List("research", "goals", slug)  => Some(Goal(slug))
      case "research" :: "activity" :: rest =>
        rest match
          case Nil     => Some(Activity(ActivityTab.Everything))
          case List(t) => ActivityTab.values.find(x => x.slug == t && t.nonEmpty).map(Activity(_))
          case _       => None
      case "research" :: rest =>
        rest match
          case Nil     => Some(Research(ResearchSection.Overview))
          case List(s) =>
            ResearchSection.values
              .find(x => x.slug == s && s.nonEmpty && x != ResearchSection.Activity)
              .map(Research(_))
          case _ => None
      case "settings" :: rest =>
        rest match
          case Nil     => Some(Settings(SettingsSection.General))
          case List(s) => SettingsSection.values.find(_.slug == s).map(Settings(_))
          case _       => None
      case List("setup")         => Some(Setup)
      case List("source", ck)    => Some(Source(ck))
      case List("article", slug) => Some(Article(slug))
      case List("place", id)     => Some(Place(id))
      case List("event", id)     => Some(Event(id))
      case List("run", id)       => Some(Run(id))
      case List("manager", id)   => Some(Manager(id))
      case List("job", id)       => Some(Job(id))
      case _                     => None

  /** The page that took over a window of the old toolbar; `None` for a window of one item (a run, a goal, a source…),
    * which still opens as a window.
    */
  def ofWindow(key: WindowKey): Option[Route] = key match
    case WindowKey.Goals     => Some(Research(ResearchSection.Goals))
    case WindowKey.Approvals => Some(Research(ResearchSection.Decisions))
    case WindowKey.Managers  => Some(Research(ResearchSection.Overview))
    case WindowKey.Runs      => Some(Activity(ActivityTab.Runs))
    case WindowKey.Models    => Some(Research(ResearchSection.Models))
    case WindowKey.Tasks     => Some(Research(ResearchSection.Backlog))
    case WindowKey.Log       => Some(Activity(ActivityTab.Worklog))
    case WindowKey.Jobs      => Some(Activity(ActivityTab.JobList))
    case WindowKey.Acquire   => Some(Research(ResearchSection.Acquire))
    case WindowKey.Upload    => Some(Library(LibrarySection.Upload))
    case WindowKey.Merge     => Some(Library(LibrarySection.Merge))
    case WindowKey.About     => Some(Settings(SettingsSection.About))
    // the key's window stays: it is the prompt a write opens where it is needed (its home is Settings › Keys)
    case _ => None

  /** The page of an item that also opens as a window — what a link to it points at, for a new tab. */
  def ofItem(key: WindowKey): Option[Route] = key match
    case WindowKey.Source(ck)    => Some(Source(ck))
    case WindowKey.Article(slug) => Some(Article(slug))
    case WindowKey.Run(id)       => Some(Run(id))
    case WindowKey.Goal(slug)    => Some(Goal(slug))
    case WindowKey.Manager(id)   => Some(Manager(id))
    case _                       => None

  /** The window this page also opens as ("pin as window"), if it has one. */
  def window(r: Route): Option[WindowKey] = r match
    case Run(id)     => Some(WindowKey.Run(id))
    case Goal(slug)  => Some(WindowKey.Goal(slug))
    case Manager(id) => Some(WindowKey.Manager(id))
    case _           => None

  /** The watched list a page shows ([[Notices]]: opening it marks its news read), as its old window. */
  def board(r: Route): Option[WindowKey] = r match
    case Research(ResearchSection.Goals)     => Some(WindowKey.Goals)
    case Research(ResearchSection.Decisions) => Some(WindowKey.Approvals)
    case Research(ResearchSection.Backlog)   => Some(WindowKey.Tasks)
    case Research(ResearchSection.Acquire)   => Some(WindowKey.Acquire)
    case Activity(ActivityTab.Worklog)       => Some(WindowKey.Log)
    case _                                   => None

  /** Where a link to `key` points: the item's own page, the page that took over its window, else the map with that
    * window open (a citation graph, a source's editor).
    */
  def hrefOf(key: WindowKey): com.histmersion.web.Route.HrefOfResult =
    ofItem(key)
      .orElse(ofWindow(key))
      .orElse(Option.when(key == WindowKey.ApiKey)(Settings(SettingsSection.Keys)))
      .map(_.path)
      .getOrElse(s"${Explore.path}#open=${js.URIUtils.encodeURIComponent(key.wire)}")

  /** The page of a list item: the list, scrolled to the item and marked (`?item=`). */
  def queueItem(kind: Queue.Kind, id: com.histmersion.web.Route.QueueItemId): com.histmersion.web.Route.QueueItemResult =
    val list = kind match
      case Queue.Kind.Acquire => Research(ResearchSection.Acquire)
      case Queue.Kind.Tasks   => Research(ResearchSection.Backlog)
      case Queue.Kind.Log     => Activity(ActivityTab.Worklog)
    s"${list.path}?item=$id"

  /** Where an address of the old web UI goes now. */
  final case class Landing(route: Route, open: List[WindowKey])

  /** An old address on its way to the new one: the root is Explore; `/acquire`, `/tasks`, `/log` and `/merge` are their
    * lists' pages; and a toolbar window in the hash (`#open=goals`) becomes its page — the first one wins, the others
    * are dropped, and the windows of single items stay open over it. `None` when the address is already a page of its
    * own and names no toolbar window. Pure.
    */
  def legacy(path: com.histmersion.web.Route.LegacyPath, open: List[WindowKey]): Option[Landing] =
    val byPath: Option[Route] = segments(path) match
      case Nil           => Some(Explore)
      case List("merge") => Some(Library(LibrarySection.Merge))
      case List("about") => Some(Settings(SettingsSection.About))
      case List("storage") | List("settings", "storage") | List("settings", "map-regions") =>
        Some(Settings(SettingsSection.Data))
      case List(list) => Queue.Kind.values.find(_.label == list).flatMap(k => ofWindow(WindowKey.ofQueue(k)))
      case _          => None
    val (toolWindows, items) = open.partition(ofWindow(_).isDefined)
    if byPath.isEmpty && toolWindows.isEmpty then None
    else
      val current = byPath.orElse(parse(path)).getOrElse(Explore)
      // a tool window in the hash names the page that was being looked at; an old list address does too
      val target = if byPath.exists(_ != Explore) then current
      else toolWindows.headOption.flatMap(ofWindow).getOrElse(current)
      Some(Landing(target, items))

  /** Is a path one the web UI's page answers (the server serves it the app)? Pure. */
  def isAppPath(path: com.histmersion.web.Route.IsAppPathPath): com.histmersion.web.Route.IsAppPathResult = parse(path).exists(!_.serverPage) || legacy(path, Nil).isDefined
