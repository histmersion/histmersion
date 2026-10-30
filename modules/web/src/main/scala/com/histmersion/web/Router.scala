package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window

/** The page being shown, from the address bar: [[Route.parse]] of the path, kept in step with back/forward. Moving
  * between the web UI's pages changes the address without reloading (the map, the filters and the open windows stay);
  * the server's own pages (a source, an article) load.
  */
object Router:

  private val current = Var[Route](Route.Explore)

  /** Whether the address named no page the web UI knows (`/research/nope`). */
  private val missing = Var(false)

  /** The address's query (`?item=…`): what a page shows within itself, such as Library's chosen item. */
  private val query = Var("")

  def route: StrictSignal[Route] = current.signal
  def now(): Route = current.now()
  def missingSignal: Signal[Boolean] = missing.signal
  def search: StrictSignal[String] = query.signal

  /** A click a link should handle itself: the primary button with no modifier. ⌘/Ctrl/Shift/Alt and the middle button
    * are the browser's (a new tab or window), so the handler leaves them alone. Pure.
    */
  def plainClick(button: Int, meta: Boolean, ctrl: Boolean, shift: Boolean, alt: Boolean): Boolean =
    button == 0 && !meta && !ctrl && !shift && !alt

  def plain(ev: dom.MouseEvent): Boolean = plainClick(ev.button, ev.metaKey, ev.ctrlKey, ev.shiftKey, ev.altKey)

  /** Once, after [[UiState.init]] read the hash: an old address goes to its new page, the address becomes the page's,
    * and back/forward follow.
    */
  def init(): Unit =
    settle()
    window.addEventListener("popstate", (_: dom.PopStateEvent) => settle())

  /** Read the address: an old one ([[Route.legacy]]) is replaced by its new page in place (no history step). */
  private def settle(): Unit =
    val path = window.location.pathname
    query.set(window.location.search)
    Route.legacy(path, UiState.open.now()) match
      case Some(Route.Landing(to, open)) =>
        if open != UiState.open.now() then UiState.open.set(open)
        UiState.navigate(
          to.path + (if Route.parse(path).contains(to) then window.location.search else ""),
          push = false
        )
        query.set(window.location.search)
        show(to)
      case None =>
        Route.parse(path) match
          case Some(r) => show(r)
          case None    =>
            missing.set(true)
            current.set(Route.Explore)

  private def show(r: Route): Unit =
    missing.set(false)
    if current.now() != r then current.set(r)
    dom.document.title = s"${title(r)} · Histmersion"

  /** Go to page `r`: a step in the history, unless `push` is false. */
  def go(r: Route, push: Boolean = true, search: String = ""): Unit =
    if r.serverPage then window.location.assign(r.path)
    else
      UiState.navigate(r.path + search, push)
      query.set(search)
      show(r)

  /** Go to a page given as an address of its own (`/research/backlog?item=3`). */
  def goHref(href: String): Unit =
    val (path, search) = href.indexOf('?') match
      case -1 => (href, "")
      case i  => (href.take(i), href.drop(i))
    Route.parse(path) match
      case Some(r) => go(r, search = search)
      case None    => window.location.assign(href)

  private val titleBus = EventBus[(Route, String)]()

  /** An item's own title, once its page or window has read it: the page's heading follows it. */
  def titles: EventStream[(Route, String)] = titleBus.events

  /** The page's title in the browser's tab: the page's name; an item's own title once it is known. */
  def retitle(r: Route, t: String): Unit =
    if current.now() == r then dom.document.title = s"$t · Histmersion"
    titleBus.emit(r -> t)

  /** A page's name, before its content has said a better one. */
  def title(r: Route): String = r match
    case Route.Explore     => "Explore"
    case Route.Library(s)  => if s == LibrarySection.Sources then "Library" else s.label
    case Route.Research(s) => if s == ResearchSection.Overview then "Research" else s.label
    case Route.Activity(t) => t.label
    case Route.Settings(s) => s.label
    case Route.Setup       => "Set up Histmersion"
    case Route.Goal(slug)  => s"Goal · ${com.histmersion.api.Wording.humanize(slug)}"
    case Route.Run(_)      => "Agent run"
    case Route.Manager(id) => com.histmersion.api.Wording.manager(id)
    case Route.Job(_)      => "Background job"
    case Route.Place(_)    => "Place"
    case Route.Event(_)    => "Event"
    case Route.Source(_)   => "Source"
    case Route.Article(_)  => "Article"

/** A link to a page of the web UI: a real `<a href>`, so ⌘/Ctrl/Shift/middle click opens it in a new tab; a plain click
  * moves there without reloading.
  */
object RouteLink:
  def apply(r: Route, mods: Modifier[HtmlElement]*): HtmlElement =
    a(href := r.path, mods, onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.go(r) } })

/** A link to something that opens as a window (a run, a goal, a manager) or as a page that took over an old toolbar
  * window (the backlog, the approvals): the address of its page, so it can be opened in a new tab, and a plain click
  * does what it always did — open the window, or show the page.
  */
object WinLink:
  def apply(key: WindowKey, mods: Modifier[HtmlElement]*): HtmlElement =
    a(
      cls := "link-btn",
      href := Route.hrefOf(key),
      mods,
      onClick --> { ev =>
        if Router.plain(ev) then { ev.preventDefault(); ev.stopPropagation(); WindowManager.open(key) }
      }
    )

/** A link to one item of a list (a backlog item, something to acquire): its list's page, scrolled to it. */
object QueueItemLink:
  def apply(kind: com.histmersion.api.Queue.Kind, id: Int, mods: Modifier[HtmlElement]*): HtmlElement =
    val to = Route.queueItem(kind, id)
    a(
      cls := "link-btn",
      href := to,
      mods,
      onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); ev.stopPropagation(); Router.goHref(to) } }
    )
