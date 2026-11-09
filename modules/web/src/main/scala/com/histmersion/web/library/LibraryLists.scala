package com.histmersion
package web.library
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Api
import com.histmersion.api.library.{Filter, Hit}
import com.histmersion.web.{ApiClient, Latest, LibrarySection, UiState}
import com.histmersion.web.ui.Layout
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.research.Article
import com.histmersion.api.Offset
import com.histmersion.web.ErrorLine
import com.histmersion.api.RowCount

/** Library's two lists, a page at a time: the sources and the articles the filter panel selects (the map's view never
  * narrows them: Library lists everywhere), the sources in [[LibrarySort]]'s order. Both are read whenever the filter
  * changes, so both tabs show their counts; a page of one is read when it is turned to. A list read for the same
  * filter, order and page is not read again.
  */
object LibraryLists:
  val sources: Var[ListPage[Hit]] = Var(ListPage())
  val articles: Var[ListPage[Article.Summary]] = Var(ListPage())

  private val latestSources = Latest()
  private val latestArticles = Latest()
  // `var`s: what the lists were last read for (the filter and the order), the pending reread
  private var readFor: Option[String] = None
  private var timer: Int = 0

  def sort: LibrarySort = LibrarySort.of(UiState.librarySort.now().map(LibrarySort.Wire(_)))

  /** The filter the lists follow: the panel's, everywhere. */
  def filter(): Filter = UiState.toFilter(except = Set(UiState.ToFilterExcept("bbox")))

  private def key(f: Filter): String = s"$f|${sort.wire.value}"

  /** The table's rows of `section`, as keys. */
  def keys(section: LibrarySection): List[TableRow.Key] =
    if section == LibrarySection.Articles then articles.now().rows.map(a => TableRow.Key(a.slug.value))
    else sources.now().rows.map(h => TableRow.Key(h.source.citekey.value))

  /** Read both lists' first pages, unless they are already read for this filter and order. */
  def refresh(): Unit =
    val f = filter()
    if !readFor.contains(key(f)) then
      readFor = Some(key(f))
      readSources(f, Offset(0))
      readArticles(f, Offset(0))

  /** [[refresh]] after the filter has stopped changing (typing in the words field), as Explore's lists do. */
  def refreshSoon(): Unit =
    dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(
      () => refresh(),
      UiState.reloadDelay(UiState.ReloadDelayNormalMs(Layout.tabReloadMs.value)).value
    )

  /** Turn `section`'s list to the page starting at `offset`. */
  def turnTo(section: LibrarySection, offset: Offset): Unit =
    if section == LibrarySection.Articles then readArticles(filter(), offset) else readSources(filter(), offset)

  private def readSources(f: Filter, offset: Offset): Unit =
    sources.update(_.copy(loading = true, error = None))
    val page = f
      .modify(_.sort)
      .setTo(sort.sort)
      .modify(_.limit)
      .setTo(Filter.Limit(Layout.libraryPage.value))
      .modify(_.offset)
      .setTo(Filter.Offset(offset.value))
    latestSources(ApiClient.sources(page))(
      r => sources.set(ListPage(r.hits, r.offset, r.total, loaded = true)),
      m => sources.update(_.copy(loading = false, loaded = true, error = Some(Problem.Unclassified(Legacy.Text(m)))))
    )

  private def readArticles(f: Filter, offset: Offset): Unit =
    articles.update(_.copy(loading = true, error = None))
    val page = f
      .modify(_.limit)
      .setTo(Filter.Limit(Layout.libraryPage.value))
      .modify(_.offset)
      .setTo(Filter.Offset(offset.value))
    latestArticles(ApiClient.articles(Api.ArticleQuery(page)))(
      r => articles.set(ListPage(r.articles, r.offset, r.total, loaded = true)),
      m => articles.update(_.copy(loading = false, loaded = true, error = Some(Problem.Unclassified(Legacy.Text(m)))))
    )

  /** How many sources and articles the collection holds, whatever the filter: "Everything"'s count. */
  def everything(): Future[RowCount] =
    ApiClient
      .sources(Filter(limit = Filter.Limit(1)))
      .zip(ApiClient.articles(Api.ArticleQuery(Filter(limit = Filter.Limit(1)))))
      .map((s, a) => RowCount(s.total.value + a.total.value))
