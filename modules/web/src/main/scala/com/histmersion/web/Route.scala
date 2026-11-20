package com.histmersion
package web

import com.histmersion.api.Html.Atoms
import com.histmersion.api.automation.Queue

import scala.scalajs.js
import scala.util.control.NonFatal
import io.github.iltotore.iron.constraint.any.Pure

/** A page of the web UI, by its address. Every page and every item has a real path, so each can be bookmarked, shared,
  * and opened in a new tab; the open windows and the filters ride along in the hash ([[UiState]]). `/source/…` and
  * `/article/…` are the server's own standalone pages. Pure.
  */
enum Route derives CanEqual:
  case Explore
  case Library(section: LibrarySection)
  case Research(section: ResearchSection)
  case Activity(tab: ActivityTab)
  case Goal(slug: Route.Goal.Slug)
  case Settings(section: SettingsSection)

  /** The first-run setup, run again from Settings. */
  case Setup
  case Source(citekey: Route.Source.Citekey)
  case Article(slug: Route.Article.Slug)
  case Place(id: Route.Place.Id)
  case Event(id: Route.Event.Id)
  case Run(id: Route.Run.Id)
  case Manager(id: Route.Manager.Id)
  case Job(id: Route.Job.Id)

  /** The address of the page — without the hash, which carries the filters and the open windows. */
  def path: String = this match
    case Explore       => "/explore"
    case Library(s)    => sub("/library", s.slug.value)
    case Research(s)   => sub("/research", s.slug.value)
    case Activity(t)   => sub("/research/activity", t.slug)
    case Goal(slug)    => s"/research/goals/${Atoms.segment(slug.value)}"
    case Settings(s)   => sub("/settings", s.slug.value)
    case Setup         => "/setup"
    case Source(ck)    => SourceLink.href(ck)
    case Article(slug) => ArticleLink.href(api.research.Article.Slug(slug.value))
    case Place(id)     => s"/place/${Atoms.segment(id.value)}"
    case Event(id)     => s"/event/${Atoms.segment(id.value)}"
    case Run(id)       => s"/run/${Atoms.segment(id.value)}"
    case Manager(id)   => s"/manager/${Atoms.segment(id.value)}"
    case Job(id)       => s"/job/${Atoms.segment(id.value)}"

  private def sub(base: String, slug: String): String = if slug.isEmpty then base else s"$base/$slug"

  /** The top bar's tab this page sits under. */
  def place: PlaceTab = this match
    case Explore | Place(_) | Event(_)                                      => PlaceTab.Explore
    case Library(_) | Source(_) | Article(_)                                => PlaceTab.Library
    case Research(_) | Activity(_) | Goal(_) | Run(_) | Manager(_) | Job(_) => PlaceTab.Research
    case Settings(_) | Setup                                                => PlaceTab.Settings

  /** Whether the server renders this page itself (a full page load), rather than the web UI. */
  def serverPage: Boolean = this match
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
  type ManagerId = ManagerId.T
  object ManagerId extends Newtype[String, Pure]
  type RunId = RunId.T
  object RunId extends Newtype[String, Pure]

  /** The front page of each place. */
  object Goal:
    export api.automation.Goal.Slug

  object Source:
    type Citekey = Preview.Target.Source.Citekey
    val Citekey: Preview.Target.Source.Citekey.type = Preview.Target.Source.Citekey

  object Article:
    export api.research.Article.Slug

  object Place:
    export Pages.PlaceId as Id

  object Event:
    export Pages.EventId as Id

  object Run:
    export Route.RunId as Id

  object Manager:
    export Route.ManagerId as Id

  object Job:
    export Pages.JobId as Id

  type ParsePath = String
  type LegacyPath = String
  type IsAppPathPath = String

  def front(p: PlaceTab): Route = p match
    case PlaceTab.Explore  => Explore
    case PlaceTab.Library  => Library(LibrarySection.Sources)
    case PlaceTab.Research => Research(ResearchSection.Overview)
    case PlaceTab.Settings => Settings(SettingsSection.General)

  private def segments(path: String): List[String] =
    Option(path).getOrElse("").split('/').toList.filter(_.nonEmpty).map(decode)

  private def decode(s: String): String =
    try js.URIUtils.decodeURIComponent(s)
    catch case NonFatal(_) => s

  /** The page an address stands for; `None` for one the web UI does not know. The root and the old addresses of the
    * lists (`/acquire`, `/tasks`, `/log`, `/merge`) are [[legacy]]'s.
    */
  def parse(path: ParsePath): Option[Route] =
    segments(path) match
      case List("explore")   => Some(Explore)
      case "library" :: rest =>
        rest match
          case Nil     => Some(Library(LibrarySection.Sources))
          case List(s) => LibrarySection.values.find(x => x.slug.value == s && s.nonEmpty).map(Library(_))
          case _       => None
      case List("research", "goals", slug)  => Some(Goal(api.automation.Goal.Slug(slug)))
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
              .find(x => x.slug.value == s && s.nonEmpty && x != ResearchSection.Activity)
              .map(Research(_))
          case _ => None
      case "settings" :: rest =>
        rest match
          case Nil     => Some(Settings(SettingsSection.General))
          case List(s) => SettingsSection.values.find(_.slug.value == s).map(Settings(_))
          case _       => None
      case List("setup")         => Some(Setup)
      case List("source", ck)    => Some(Source(Preview.Target.Source.Citekey(ck)))
      case List("article", slug) => Some(Article(Preview.Item.Slug(slug)))
      case List("place", id)     => Some(Place(Pages.PlaceId(id)))
      case List("event", id)     => Some(Event(Pages.EventId(id)))
      case List("run", id)       => Some(Run(RunId(id)))
      case List("manager", id)   => Some(Manager(ManagerId(id)))
      case List("job", id)       => Some(Job(Pages.JobId(id)))
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
    case WindowKey.Source(ck)    => Some(Source(Preview.Target.Source.Citekey(ck.value)))
    case WindowKey.Article(slug) => Some(Article(Preview.Item.Slug(slug.value)))
    case WindowKey.Run(id)       => Some(Run(RunId(id.value)))
    case WindowKey.Goal(slug)    => Some(Goal(api.automation.Goal.Slug(slug.value)))
    case WindowKey.Manager(id)   => Some(Manager(ManagerId(id.value)))
    case _                       => None

  /** The window this page also opens as ("pin as window"), if it has one. */
  def window(r: Route): Option[WindowKey] = r match
    case Run(id)     => Some(WindowKey.Run(WindowKey.Run.Id(id.value)))
    case Goal(slug)  => Some(WindowKey.Goal(WindowKey.Goal.Slug(slug.value)))
    case Manager(id) => Some(WindowKey.Manager(WindowKey.Manager.Id(id.value)))
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
  def hrefOf(key: WindowKey): String =
    ofItem(key)
      .orElse(ofWindow(key))
      .orElse(Option.when(key == WindowKey.ApiKey)(Settings(SettingsSection.Keys)))
      .map(_.path)
      .getOrElse(s"${Explore.path}#open=${js.URIUtils.encodeURIComponent(key.wire.value)}")

  /** The page of a list item: the list, scrolled to the item and marked (`?item=`). */
  def queueItem(kind: Queue.Kind, id: api.automation.Goal.Plans.GoalLink.QueueNumber): String =
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
  def legacy(path: LegacyPath, open: List[WindowKey]): Option[Landing] =
    val byPath: Option[Route] = segments(path) match
      case Nil           => Some(Explore)
      case List("merge") => Some(Library(LibrarySection.Merge))
      case List("about") => Some(Settings(SettingsSection.About))
      case List("storage") | List("settings", "storage") | List("settings", "map-regions") =>
        Some(Settings(SettingsSection.Data))
      case List(list) => Queue.Kind.values.find(_.label.value == list).flatMap(k => ofWindow(WindowKey.ofQueue(k)))
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
  def isAppPath(path: IsAppPathPath): Boolean = parse(path).exists(!_.serverPage) || legacy(path, Nil).isDefined
