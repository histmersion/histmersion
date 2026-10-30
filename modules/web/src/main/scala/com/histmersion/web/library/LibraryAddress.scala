package com.histmersion.web.library

import com.histmersion.web.{LibrarySection, Route}

import scala.scalajs.js

/** What a Library list shows and has chosen, in the address: `/library?item=<citekey>`,
  * `/library/articles?item=<slug>`, a shelf of My library (`/library?shelf=bookmarks`) — so the choice survives a
  * reload, a shared link and back/forward. Pure. */
object LibraryAddress:

  private val Key      = "item"
  private val ShelfKey = "shelf"

  private def param(search: String, name: String): Option[String] =
    search.stripPrefix("?").split('&').toList
      .map(_.split("=", 2).toList)
      .collectFirst { case List(`name`, v) => decode(v) }
      .map(_.trim).filter(_.nonEmpty)

  /** The chosen item of an address's query (`?item=…`, with or without its `?`). */
  def item(search: String): Option[String] = param(search, Key)

  /** The shelf of My library an address's query names; none is "Everything". */
  def shelf(search: String): Shelf = param(search, ShelfKey).flatMap(Shelf.parse).getOrElse(Shelf.Everything)

  /** The query that chooses `item` on `shelf` (empty for none on Everything). */
  def search(item: Option[String], shelf: Shelf = Shelf.Everything): String =
    val parts = Option.when(shelf != Shelf.Everything)(s"$ShelfKey=${shelf.wire}").toList ++
      item.map(_.trim).filter(_.nonEmpty).map(i => s"$Key=${js.URIUtils.encodeURIComponent(i)}").toList
    if parts.isEmpty then "" else parts.mkString("?", "&", "")

  /** The address of a list with `item` chosen. */
  def href(section: LibrarySection, item: Option[String], shelf: Shelf = Shelf.Everything): String =
    Route.Library(section).path + search(item, shelf)

  private def decode(v: String): String =
    try js.URIUtils.decodeURIComponent(v.replace('+', ' ')) catch case _: Throwable => v
