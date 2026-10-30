package com.histmersion.web.library

import com.histmersion.web.{ApiClient, LibrarySection, Route, Router, UiState, marks}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Library's part of the filter panel: "My library" above the filters, the shelves of the collection the
  * lists read from, and "Show these on the map" under them. Both show only beside Library's lists. */
object MyLibrary:

  private def onList(r: Route): Boolean = r match
    case Route.Library(s) => s.isList
    case _                => false

  private val shown: Signal[Boolean] = Router.route.map(onList)

  private val everything = Var(Option.empty[Int])
  private val unread     = Var(Option.empty[Int])

  /** The shelf the address names. */
  val shelf: Signal[Shelf] = Router.search.map(LibraryAddress.shelf).distinct

  private def section: LibrarySection = Router.now() match
    case Route.Library(s) if s.isList => s
    case _                            => LibrarySection.Sources

  private def count(s: Shelf): Signal[Option[Int]] = s match
    case Shelf.Everything => everything.signal
    case Shelf.Bookmarks  => marks.MyItems.bookmarks.signal.map(bs => Some(bs.size))
    case Shelf.Notes      => marks.MyItems.notes.signal.map(ns => Some(ns.size))
    case Shelf.Unread     => unread.signal
    case Shelf.Recent     => marks.MyItems.marks.signal.map(ms => Some(ms.values.count(_.openedAt.isDefined)))

  private def refresh(): Unit =
    LibraryLists.everything().foreach(n => everything.set(Some(n)))
    ApiClient.unread("source", 1, 0).zip(ApiClient.unread("article", 1, 0)).foreach((s, a) => unread.set(Some(s.total + a.total)))

  /** "My library", for the top of the filter panel. */
  def panelTop: HtmlElement =
    navTag(
      cls := "my-library",
      cls("is-hidden") <-- shown.map(!_),
      aria.label := "My library",
      shown.changes.filter(identity) --> (_ => refresh()),
      marks.MyItems.marks.signal.map(_.values.count(_.read)).changes --> (_ => if onList(Router.now()) then refresh()),
      onMountCallback(_ => if onList(Router.now()) then refresh()),
      h2(cls := "my-library-title", "My library"),
      Shelf.values.toList.map { s =>
        val on = shelf.map(_ == s)
        a(cls := "side-link", dataAttr("shelf") := s.wire,
          cls("on") <-- on,
          aria.current <-- on.map(if _ then "page" else "false"),
          href <-- Router.route.map(_ => LibraryAddress.href(section, None, s)),
          onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.go(Route.Library(section), search = LibraryAddress.search(None, s)) } },
          span(cls := "side-label", s.label),
          child.maybe <-- count(s).map(_.map(n => span(cls := "side-count", ListPage.grouped(n)))))
      })

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
