package com.histmersion.web.library

import com.histmersion.web.{LibrarySection, Route}

import scala.scalajs.js

/** What a Library list shows and has chosen, in the address: `/library?item=<citekey>`,
  * `/library/articles?item=<slug>`, a shelf of My library (`/library?shelf=bookmarks`) — so the choice survives a
  * reload, a shared link and back/forward. Pure.
  */
object LibraryAddress:

  type ParamResult = ParamResult.T
  object ParamResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParamSearch = ParamSearch.T
  object ParamSearch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParamName = ParamName.T
  object ParamName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemResult = ItemResult.T
  object ItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemSearch = ItemSearch.T
  object ItemSearch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShelfSearch = ShelfSearch.T
  object ShelfSearch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResult = SearchResult.T
  object SearchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchItem = SearchItem.T
  object SearchItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HrefResult = HrefResult.T
  object HrefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HrefItem = HrefItem.T
  object HrefItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResult = DecodeResult.T
  object DecodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeV = DecodeV.T
  object DecodeV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Key = "item"
  private val ShelfKey = "shelf"

  private def param(search: com.histmersion.web.library.LibraryAddress.ParamSearch, name: com.histmersion.web.library.LibraryAddress.ParamName): Option[com.histmersion.web.library.LibraryAddress.ParamResult] =
    search
      .stripPrefix("?")
      .split('&')
      .toList
      .map(_.split("=", 2).toList)
      .collectFirst { case List(`name`, v) => decode(v) }
      .map(_.trim)
      .filter(_.nonEmpty)

  /** The chosen item of an address's query (`?item=…`, with or without its `?`). */
  def item(search: com.histmersion.web.library.LibraryAddress.ItemSearch): Option[com.histmersion.web.library.LibraryAddress.ItemResult] = param(search, Key)

  /** The shelf of My library an address's query names; none is "Everything". */
  def shelf(search: com.histmersion.web.library.LibraryAddress.ShelfSearch): Shelf = param(search, ShelfKey).flatMap(Shelf.parse).getOrElse(Shelf.Everything)

  /** The query that chooses `item` on `shelf` (empty for none on Everything). */
  def search(item: Option[com.histmersion.web.library.LibraryAddress.SearchItem], shelf: Shelf = Shelf.Everything): com.histmersion.web.library.LibraryAddress.SearchResult =
    val parts = Option.when(shelf != Shelf.Everything)(s"$ShelfKey=${shelf.wire}").toList ++
      item.map(_.trim).filter(_.nonEmpty).map(i => s"$Key=${js.URIUtils.encodeURIComponent(i)}").toList
    if parts.isEmpty then "" else parts.mkString("?", "&", "")

  /** The address of a list with `item` chosen. */
  def href(section: LibrarySection, item: Option[com.histmersion.web.library.LibraryAddress.HrefItem], shelf: Shelf = Shelf.Everything): com.histmersion.web.library.LibraryAddress.HrefResult =
    Route.Library(section).path + search(item, shelf)

  private def decode(v: com.histmersion.web.library.LibraryAddress.DecodeV): com.histmersion.web.library.LibraryAddress.DecodeResult =
    try js.URIUtils.decodeURIComponent(v.replace('+', ' '))
    catch case _: Throwable => v
