package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}

/** One controller for the list tabs that follow the global filter (Articles, Events — #1798 item 5): on a UiState
  * change it recomputes the query, skips an unchanged one, debounces, drops answers to superseded requests
  * ([[Latest]]), shows the sidebar's count line ([[ResultsPaging]], `· loading…` while a request is out), an error line
  * (never stuck loading), the empty text, and ONE "show N more (M left)" that reveals the loaded rows and then fetches
  * the next page from the server.
  *
  * @param name
  *   the CSS prefix (`articles` → `articles-pane`, `articles-list`)
  * @param singular
  *   the row's noun in the count line (`article`); `noun` its plural
  * @param query
  *   the query, a pure function of the UiState
  * @param fetch
  *   one page of the answer at an offset: its rows, the total and the count line's extras
  * @param page
  *   how many rows "show more" reveals
  */
final class FilterTab[Q, Row](
    name: String,
    singular: String,
    noun: String,
    query: () => Q,
    fetch: (Q, Int) => Future[TabPage[Row]],
    render: Row => HtmlElement,
    empty: String,
    page: Int,
    debounceMs: () => Int
)(using ExecutionContext):

  val state: Var[TabState[Row]] = Var(TabState[Row]())
  private val latest = Latest()
  // `var`s: the query last sent (to skip an unchanged one) and the pending debounce
  private var lastQuery: Option[Q] = None
  private var timer: Int = 0

  def pane: HtmlElement =
    div(
      cls := s"$name-pane",
      ResultsPaging.countBar(
        state.signal.map(_.countLine(singular, noun)),
        state.signal.map(s => s.loading && s.loaded),
        state.signal.map(_.error.nonEmpty)
      ),
      child.maybe <-- state.signal.map(
        _.error.map(m => p(cls := "form-error", role := "alert", s"Could not list $noun: $m"))
      ),
      child.maybe <-- state.signal.map(s =>
        Option.when(s.loaded && !s.loading && s.error.isEmpty && s.rows.isEmpty)(p(cls := "muted", empty))
      ),
      ul(cls := s"$name-list", children <-- state.signal.map(s => s.rows.take(s.shown).map(render))),
      ResultsPaging.moreButton(state.signal.map(_.moreLabel(page)), state.signal.map(_.loading), () => more())
    )

  /** Called on every UiState change: refetch only when the query has changed, after the debounce. */
  def refresh(): Unit =
    val q = query()
    if !lastQuery.contains(q) then
      lastQuery = Some(q)
      dom.window.clearTimeout(timer)
      timer = dom.window.setTimeout(() => load(), debounceMs())

  /** The first page of the current query, now. */
  def load(): Unit =
    val q = query()
    lastQuery = Some(q)
    state.update(TabState.loading)
    latest(fetch(q, 0))(p => state.set(TabState.first(p, page)), m => state.update(TabState.failed(_, m)))

  /** "show more": the loaded rows first, then the next page from the server. */
  def more(): Unit =
    TabState.more(state.now(), page) match
      case Right(next)  => state.set(next)
      case Left(offset) =>
        val q = lastQuery.getOrElse(query())
        state.update(TabState.loading)
        latest(fetch(q, offset))(
          p => state.update(TabState.appended(_, p, page)),
          m => state.update(TabState.failed(_, m))
        )
