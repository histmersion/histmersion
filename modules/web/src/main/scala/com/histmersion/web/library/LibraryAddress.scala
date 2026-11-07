package com.histmersion.web.library

import com.histmersion.web.{LibrarySection, Route}

import scala.scalajs.js

/** What a Library list shows and has chosen, in the address: `/library?item=<citekey>`,
  * `/library/articles?item=<slug>`, a shelf of My library (`/library?shelf=bookmarks`) — so the choice survives a
  * reload, a shared link and back/forward. Pure.
  */
object LibraryAddress:
  type DecodedText = DecodedText.T
  object DecodedText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodedText = EncodedText.T
  object EncodedText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemKey = ItemKey.T
  object ItemKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParamText = ParamText.T
  object ParamText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParamName = ParamName.T
  object ParamName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemSearch = _root_.com.histmersion.web.library.LibraryAddress.SearchQuery
  val ItemSearch: _root_.com.histmersion.web.library.LibraryAddress.SearchQuery.type = _root_.com.histmersion.web.library.LibraryAddress.SearchQuery
  type ShelfSearch = _root_.com.histmersion.web.library.LibraryAddress.SearchQuery
  val ShelfSearch: _root_.com.histmersion.web.library.LibraryAddress.SearchQuery.type = _root_.com.histmersion.web.library.LibraryAddress.SearchQuery
  type HrefItem = _root_.com.histmersion.web.library.LibraryAddress.ItemKey
  val HrefItem: _root_.com.histmersion.web.library.LibraryAddress.ItemKey.type = _root_.com.histmersion.web.library.LibraryAddress.ItemKey

  private val Key = "item"
  private val ShelfKey = "shelf"

  private def param(search: com.histmersion.web.library.LibraryAddress.SearchQuery, name: com.histmersion.web.library.LibraryAddress.ParamName): Option[com.histmersion.web.library.LibraryAddress.ParamText] =
    search
      .stripPrefix("?")
      .split('&')
      .toList
      .map(_.split("=", 2).toList)
      .collectFirst { case List(`name`, v) => decode(v) }
      .map(_.trim)
      .filter(_.nonEmpty)

  /** The chosen item of an address's query (`?item=…`, with or without its `?`). */
  def item(search: com.histmersion.web.library.LibraryAddress.ItemSearch): Option[com.histmersion.web.library.LibraryAddress.ItemKey] = param(search, Key)

  /** The shelf of My library an address's query names; none is "Everything". */
  def shelf(search: com.histmersion.web.library.LibraryAddress.ShelfSearch): Shelf = param(search, ShelfKey).flatMap(Shelf.parse).getOrElse(Shelf.Everything)

  /** The query that chooses `item` on `shelf` (empty for none on Everything). */
  def search(item: Option[com.histmersion.web.library.LibraryAddress.ItemKey], shelf: Shelf = Shelf.Everything): com.histmersion.web.library.LibraryAddress.SearchQuery =
    val parts = Option.when(shelf != Shelf.Everything)(s"$ShelfKey=${shelf.wire}").toList ++
      item.map(_.trim).filter(_.nonEmpty).map(i => s"$Key=${js.URIUtils.encodeURIComponent(i)}").toList
    if parts.isEmpty then com.histmersion.web.library.LibraryAddress.SearchQuery("") else parts.mkString("?", "&", "")

  /** The address of a list with `item` chosen. */
  def href(section: LibrarySection, item: Option[com.histmersion.web.library.LibraryAddress.HrefItem], shelf: Shelf = Shelf.Everything): com.histmersion.web.library.LibraryAddress.Href =
    Route.Library(section).path + search(item, shelf)

  private def decode(v: com.histmersion.web.library.LibraryAddress.EncodedText): com.histmersion.web.library.LibraryAddress.DecodedText =
    try js.URIUtils.decodeURIComponent(v.replace('+', ' '))
    catch case _: Throwable => v
