package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window

/** The page being shown, from the address bar: [[Route.parse]] of the path, kept in step with back/forward. Moving
  * between the web UI's pages changes the address without reloading (the map, the filters and the open windows stay);
  * the server's own pages (a source, an article) load.
  */
object Router:
  export UiState.{NavigatePush as Push}

  private val current = Var[Route](Route.Explore)

  /** Whether the address named no page the web UI knows (`/research/nope`). */
  private val missing = Var(false)

  /** The address's query (`?item=…`): what a page shows within itself, such as Library's chosen item. */
  private val query = Var(Pages.UrlQuery(""))

  def route: StrictSignal[Route] = current.signal
  def now(): Route = current.now()
  def missingSignal: Signal[Boolean] = missing.signal
  def search: StrictSignal[Pages.UrlQuery] = query.signal

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
    query.set(Pages.UrlQuery(window.location.search))
    Route.legacy(path, UiState.open.now()) match
      case Some(Route.Landing(to, open)) =>
        if open != UiState.open.now() then UiState.open.set(open)
        UiState.navigate(
          UiState.NavigatePathAndSearch(to.path + (if Route.parse(path).contains(to) then window.location.search else "")),
          push = Push(false)
        )
        query.set(Pages.UrlQuery(window.location.search))
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
  def go(r: Route, push: Push = Push(true), search: Pages.UrlQuery = Pages.UrlQuery("")): Unit =
    if r.serverPage then window.location.assign(r.path)
    else
      UiState.navigate(UiState.NavigatePathAndSearch(r.path + search.value), push)
      query.set(search)
      show(r)

  /** Go to a page given as an address of its own (`/research/backlog?item=3`). */
  def goHref(href: String): Unit =
    val (path, search) = href.indexOf('?') match
      case -1 => (href, "")
      case i  => (href.take(i), href.drop(i))
    Route.parse(path) match
      case Some(r) => go(r, search = Pages.UrlQuery(search))
      case None    => window.location.assign(href)

  private val titleBus = EventBus[(Route, com.histmersion.api.UiLabels.Text)]()

  /** An item's own title, once its page or window has read it: the page's heading follows it. */
  def titles: EventStream[(Route, com.histmersion.api.UiLabels.Text)] = titleBus.events

  /** The page's title in the browser's tab: the page's name; an item's own title once it is known. */
  def retitle(r: Route, t: com.histmersion.api.UiLabels.Text): Unit =
    if current.now() == r then dom.document.title = s"$t · Histmersion"
    titleBus.emit(r -> t)

  /** A page's name, before its content has said a better one. */
  def title(r: Route): com.histmersion.api.UiLabels.Text = r match
    case Route.Explore     => com.histmersion.api.UiLabels.Text("Explore")
    case Route.Library(s)  => if s == LibrarySection.Sources then com.histmersion.api.UiLabels.Text("Library") else com.histmersion.api.UiLabels.Text(s.label.value)
    case Route.Research(s) => if s == ResearchSection.Overview then com.histmersion.api.UiLabels.Text("Research") else s.label
    case Route.Activity(t) => com.histmersion.api.UiLabels.Text(t.label)
    case Route.Settings(s) => s.label
    case Route.Setup       => com.histmersion.api.UiLabels.Text("Set up Histmersion")
    case Route.Goal(slug)  => com.histmersion.api.UiLabels.Text(s"Goal · ${com.histmersion.api.Wording.humanize(com.histmersion.api.Wording.Id(slug.value))}")
    case Route.Run(_)      => com.histmersion.api.UiLabels.Text("Agent run")
    case Route.Manager(id) => com.histmersion.api.UiLabels.Text(com.histmersion.api.Wording.manager(com.histmersion.api.Wording.ManagerId(id.value)))
    case Route.Job(_)      => com.histmersion.api.UiLabels.Text("Background job")
    case Route.Place(_)    => com.histmersion.api.UiLabels.Text("Place")
    case Route.Event(_)    => com.histmersion.api.UiLabels.Text("Event")
    case Route.Source(_)   => com.histmersion.api.UiLabels.Text("Source")
    case Route.Article(_)  => com.histmersion.api.UiLabels.Text("Article")
