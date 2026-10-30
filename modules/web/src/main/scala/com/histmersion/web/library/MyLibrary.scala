package com.histmersion.web.library

import com.histmersion.web.{LibrarySection, Route, RouteLink, Router, UiState}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Library's part of the filter panel: "My library" above the filters, the shelves of the collection the
  * lists read from, and "Show these on the map" under them. Both show only beside Library's lists. */
object MyLibrary:

  /** A shelf of "My library". Bookmarks, notes, read marks and recently opened items join it once the
    * server keeps them per person; until then a shelf that could not list anything is not offered. */
  enum Shelf(val label: String):
    case Everything extends Shelf("Everything")

  private def onList(r: Route): Boolean = r match
    case Route.Library(s) => s.isList
    case _                => false

  private val shown: Signal[Boolean] = Router.route.map(onList)

  private val everything = Var(Option.empty[Int])

  /** "My library", for the top of the filter panel. */
  def panelTop: HtmlElement =
    navTag(
      cls := "my-library",
      cls("is-hidden") <-- shown.map(!_),
      aria.label := "My library",
      shown.changes.filter(identity) --> (_ => LibraryLists.everything().foreach(n => everything.set(Some(n)))),
      onMountCallback(_ => if onList(Router.now()) then LibraryLists.everything().foreach(n => everything.set(Some(n)))),
      h2(cls := "my-library-title", "My library"),
      Shelf.values.toList.map(shelf =>
        RouteLink(Route.Library(LibrarySection.Sources), cls := "side-link on", aria.current := "page",
          span(cls := "side-label", shelf.label),
          child.maybe <-- everything.signal.map(_.map(n => span(cls := "side-count", ListPage.grouped(n)))))))

  /** "Show these on the map", for the foot of the filter panel: Explore with the same filters. */
  def panelFoot: HtmlElement =
    a(
      cls := "btn btn--block show-on-map",
      cls("is-hidden") <-- shown.map(!_),
      href <-- UiState.signal.map { _ =>
        val hash = UiState.hashString
        Route.Explore.path + (if hash.nonEmpty then s"#$hash" else "")
      },
      "Show these on the map",
      onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.go(Route.Explore) } }
    )
