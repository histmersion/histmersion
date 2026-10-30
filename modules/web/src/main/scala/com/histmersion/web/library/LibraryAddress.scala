package com.histmersion.web.library

import com.histmersion.web.{LibrarySection, Route}

import scala.scalajs.js

/** The item a Library list has chosen, in the address: `/library?item=<citekey>`,
  * `/library/articles?item=<slug>` — so the choice survives a reload, a shared link and back/forward. Pure. */
object LibraryAddress:

  private val Key = "item"

  /** The chosen item of an address's query (`?item=…`, with or without its `?`). */
  def item(search: String): Option[String] =
    search.stripPrefix("?").split('&').toList
      .map(_.split("=", 2).toList)
      .collectFirst { case List(Key, v) => decode(v) }
      .map(_.trim).filter(_.nonEmpty)

  /** The query that chooses `item` (empty for none). */
  def search(item: Option[String]): String =
    item.map(_.trim).filter(_.nonEmpty).fold("")(i => s"?$Key=${js.URIUtils.encodeURIComponent(i)}")

  /** The address of a list with `item` chosen. */
  def href(section: LibrarySection, item: Option[String]): String = Route.Library(section).path + search(item)

  private def decode(v: String): String =
    try js.URIUtils.decodeURIComponent(v.replace('+', ' ')) catch case _: Throwable => v
