package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window
import com.histmersion.api.UiLabels
import com.histmersion.api.Wording
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The page being shown, from the address bar: [[Route.parse]] of the path, kept in step with back/forward. Moving
  * between the web UI's pages changes the address without reloading (the map, the filters and the open windows stay);
  * the server's own pages (a source, an article) load.
  */
@atBoundary(BoundaryKind.Dom, "reads the address bar and history and keeps the page in step with them")
object Router:
  export UiState.NavigatePush as Push

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
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def plainClick(button: Int, meta: Boolean, ctrl: Boolean, shift: Boolean, alt: Boolean): Boolean =
    button == 0 && !meta && !ctrl && !shift && !alt

  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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
          UiState.NavigatePathAndSearch(to.path + (if Route.parse(path).contains(to) then window.location.search
                                                   else "")),
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def show(r: Route): Unit =
    missing.set(false)
    if current.now() != r then current.set(r)
    dom.document.title = shown"${title(r)} · Histmersion"

  /** Go to page `r`: a step in the history, unless `push` is false. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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

  private val titleBus = EventBus[(Route, UiLabels.Text)]()

  /** An item's own title, once its page or window has read it: the page's heading follows it. */
  def titles: EventStream[(Route, UiLabels.Text)] = titleBus.events

  /** The page's title in the browser's tab: the page's name; an item's own title once it is known. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def retitle(r: Route, t: UiLabels.Text): Unit =
    if current.now() == r then dom.document.title = shown"$t · Histmersion"
    titleBus.emit(r -> t)

  /** A page's name, before its content has said a better one. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def title(r: Route): UiLabels.Text = r match
    case Route.Explore    => UiLabels.Text("Explore")
    case Route.Library(s) =>
      if s == LibrarySection.Sources then UiLabels.Text("Library") else UiLabels.Text(s.label.value)
    case Route.Research(s) => if s == ResearchSection.Overview then UiLabels.Text("Research") else s.label
    case Route.Activity(t) => UiLabels.Text(t.label)
    case Route.Settings(s) => s.label
    case Route.Setup       => UiLabels.Text("Set up Histmersion")
    case Route.Goal(slug)  => UiLabels.Text(s"Goal · ${Wording.humanize(Wording.Id(slug.value))}")
    case Route.Run(_)      => UiLabels.Text("Agent run")
    case Route.Manager(id) => UiLabels.Text(Wording.manager(Wording.ManagerId(id.value)))
    case Route.Job(_)      => UiLabels.Text("Background job")
    case Route.Place(_)    => UiLabels.Text("Place")
    case Route.Event(_)    => UiLabels.Text("Event")
    case Route.Source(_)   => UiLabels.Text("Source")
    case Route.Article(_)  => UiLabels.Text("Article")
