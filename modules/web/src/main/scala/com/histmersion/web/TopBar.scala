package com.histmersion.web

import com.histmersion.api.Wording
import com.histmersion.api.instance.{UiConfig, UiNotices}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import org.scalajs.dom

import scala.scalajs.js

/** The one bar at the top of every page: the logo's place, the four places (Explore · Library · Research · Settings) as
  * labelled links, one search field, what the research agents are doing, how many of their proposals wait for a person
  * (only when there are any), the theme, and the user guide. On a narrow screen the places keep their labels; the
  * search and the pills shrink first.
  */
object TopBar:
  /** Where the bar is: in the web UI (its links move between pages without a reload), or on one of the server's
    * standalone pages (a source, an article), whose links load the web UI.
    */

  enum Host:
    case App
    case Standalone(here: PlaceTab)

  private val currentAttr = htmlAttr("aria-current", StringAsIsCodec)

  def element(ui: UiConfig, host: Host = Host.App): HtmlElement =
    val inApp = host == Host.App
    val here: Signal[PlaceTab] = host match
      case Host.App           => Router.route.map(_.place)
      case Host.Standalone(p) => Val(p)
    headerTag(
      cls := "top-bar",
      link(
        Route.Explore,
        inApp,
        cls := "brand",
        aria.label := "Histmersion — explore",
        span(cls := "brand-mark", aria.hidden := true, "H"),
        span(cls := "brand-name", "Histmersion")
      ),
      navTag(
        cls := "places",
        aria.label := "Main",
        PlaceTab.values.toList.map { p =>
          link(
            Route.front(p),
            inApp,
            cls := "place-tab",
            dataAttr("nav") := p.toString.toLowerCase,
            cls("on") <-- here.map(_ == p),
            currentAttr <-- here.map(h => if h == p then "page" else "false"),
            p.label.value
          )
        }
      ),
      search(inApp),
      div(
        cls := "top-status",
        agentsPill(inApp),
        decisionsPill(inApp),
        Theme.toggle,
        Option.when(ui.docsUrl.value.trim.nonEmpty)(
          a(cls := "btn btn--ghost help-link", href := ui.docsUrl.value, target := "_blank", rel := "noopener", "Help")
        )
      )
    )

  /** A link to a page: in the app it moves without a reload on a plain click; on a standalone page it is an ordinary
    * link (the web UI loads there).
    */
  private def link(r: Route, inApp: Boolean, mods: Modifier[HtmlElement]*): HtmlElement =
    if inApp then RouteLink(r, mods*) else a(href := r.path, mods)

  /** The search field: the full-text filter every list and the map follow. Typing on Explore or in Library filters at
    * once; Enter anywhere else shows the results in Library. `/` puts the cursor in it from anywhere on the page.
    */
  private def search(inApp: Boolean): HtmlElement =
    div(
      cls := "top-search",
      role := "search",
      svg.svg(
        svg.cls := "icon top-search-icon",
        svg.viewBox := "0 0 24 24",
        aria.hidden := true,
        svg.circle(svg.cx := "11", svg.cy := "11", svg.r := "7"),
        svg.path(svg.d := "m20 20-4-4")
      ),
      label(forId := "f-q", cls := "sr-only", "Search everything"),
      if inApp then
        input(
          typ := "search",
          cls := "field top-search-field",
          idAttr := "f-q",
          placeholder := "Search sources, articles, places, events…",
          autoFocus := true,
          // controlled by UiState.q: "clear all", back/forward and a shared link all show up here
          controlled(
            value <-- UiState.q.signal.map(_.getOrElse("")),
            onInput.mapToValue --> (v => UiState.setSearch(com.histmersion.api.Api.Search.Text(v)))
          ),
          onKeyDown --> { e =>
            if e.key == "Enter" then
              e.preventDefault()
              if showsResults(Router.now()).isEmpty then Router.go(Route.Library(LibrarySection.Sources))
          }
        )
      else
        input(
          typ := "search",
          cls := "field top-search-field",
          idAttr := "f-q",
          placeholder := "Search sources, articles, places, events…",
          onKeyDown --> { e =>
            if e.key == "Enter" then
              val q = e.target.asInstanceOf[dom.HTMLInputElement].value.trim
              dom.window.location.assign(
                Route.Library(LibrarySection.Sources).path +
                  (if q.nonEmpty then "#q=" + js.URIUtils.encodeURIComponent(q) else "")
              )
          }
        )
      ,
      span(cls := "kbd", aria.hidden := true, "/")
    )

  /** The pages whose results follow the search as it is typed (the map and its lists, Library's lists). */
  def showsResults(r: Route): Option[Route] = r match
    case Route.Explore                                                   => Some(r)
    case Route.Library(LibrarySection.Sources | LibrarySection.Articles) => Some(r)
    case _                                                               => None

  /** What the agents are doing, linked to their activity; nothing where the server runs no agents. */
  private def agentsPill(inApp: Boolean): Modifier[HtmlElement] =
    child.maybe <-- Notices.agents.map(_.map { g =>
      link(
        Route.Activity(ActivityTab.Everything),
        inApp,
        cls := "pill agent-pill",
        cls("is-working") := g.on.value && g.running.value > 0,
        cls("is-paused") := !g.on.value || (g.draining && g.running.value == 0),
        title := g.wording,
        span(cls := "pill-dot", aria.hidden := true),
        span(cls := "pill-text", g.wording)
      )
    })

  /** The only standing notice: the proposals that wait for a person, shown while there is one. */
  private def decisionsPill(inApp: Boolean): Modifier[HtmlElement] =
    child.maybe <-- Notices.counter(UiNotices.Board.Approvals).map(_.flatMap(_.count).map(_.value).getOrElse(0)).distinct.map { n =>
      Wording.decisions(Wording.Decisions.Open(n)).map { text =>
        link(
          Route.Research(ResearchSection.Decisions),
          inApp,
          cls := "pill pill--attn decisions-pill",
          title := text,
          aria.label := text,
          span(cls := "pill-long", text),
          span(cls := "pill-short", aria.hidden := true, Wording.decisionsShort(Wording.Decisions.Open(n))),
          span(cls := "pill-count", aria.hidden := true, n.toString)
        )
      }
    }

  /** `/` focuses the search from anywhere, unless the reader is typing somewhere already. Installed once. */
  def installShortcut(): Unit =
    dom.document.addEventListener(
      "keydown",
      (e: dom.KeyboardEvent) =>
        if e.key == "/" && !e.metaKey && !e.ctrlKey && !e.altKey && !e.defaultPrevented && !WindowManager.typingIn(
            e.target
          )
        then
          Option(dom.document.getElementById("f-q")).foreach { el =>
            e.preventDefault()
            el.asInstanceOf[dom.HTMLElement].focus()
          }
    )
