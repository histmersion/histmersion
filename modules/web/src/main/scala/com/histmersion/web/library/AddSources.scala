package com.histmersion.web.library

import com.histmersion.web.{LibrarySection, MergeView, Route, RouteLink, UploadView}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** "Add sources": the menu in Library's header, and the page each way of adding has — upload a file, add a work by its
  * DOI, merge another collection. A book bought elsewhere comes in from Calibre on the computer that holds the Calibre
  * library, by the command line: the server cannot reach it.
  */
object AddSources:

  val ways: List[LibrarySection] = List(LibrarySection.Upload, LibrarySection.Doi, LibrarySection.Merge)

  private def calibreHint: HtmlElement =
    p(
      cls := "add-calibre muted",
      "A book in your Calibre library: run ",
      code("histmersion calibre import <id>"),
      " on the computer that holds it."
    )

  /** The header's button and the menu it opens. */
  def menu: HtmlElement =
    val open = Var(false)
    div(
      cls := "add-sources",
      documentEvents(_.onClick) --> { ev =>
        val t = ev.target.asInstanceOf[dom.Element]
        if open.now() && (t == null || t.closest(".add-sources") == null) then open.set(false)
      },
      documentEvents(_.onKeyDown) --> { ev => if ev.key == "Escape" && open.now() then open.set(false) },
      button(
        typ := "button",
        cls := "btn btn--primary add-sources-btn",
        idAttr := "add-sources-btn",
        aria.expanded <-- open.signal,
        aria.controls := "add-sources-menu",
        "Add sources ▾",
        onClick --> (_ => open.update(!_))
      ),
      div(
        cls := "add-sources-menu",
        idAttr := "add-sources-menu",
        cls("is-hidden") <-- open.signal.map(!_),
        ul(
          ways.map(w => li(RouteLink(Route.Library(w), cls := "add-way", w.label, onClick --> (_ => open.set(false)))))
        ),
        calibreHint
      )
    )

  /** The page of one way of adding: the three as tabs, the way back to the lists, and its form. */
  def page(on: LibrarySection): HtmlElement =
    div(
      cls := "add-sources-page",
      RouteLink(
        Route.Library(LibrarySection.Sources),
        cls := "btn btn--ghost btn--sm add-back",
        "← Back to the library"
      ),
      h1(cls := "page-title", "Add sources"),
      navTag(
        cls := "page-tabs",
        aria.label := "Ways to add sources",
        ways.map(w =>
          RouteLink(
            Route.Library(w),
            cls := "page-tab",
            cls("on") := w == on,
            aria.current := (if w == on then "page" else "false"),
            w.label
          )
        )
      ),
      on match
        case LibrarySection.Upload => UploadView.pane
        case LibrarySection.Merge  => MergeView.pane
        case _                     => DoiForm.pane,
      calibreHint
    )
