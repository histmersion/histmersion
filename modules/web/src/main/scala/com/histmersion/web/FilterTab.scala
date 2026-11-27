package com.histmersion
package web
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import com.histmersion.api.Plural
import com.histmersion.api.library.Filter
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
    name: FilterTab.Name,
    singular: Plural.Noun.Singular,
    noun: Plural.Noun.Plural,
    query: () => Q,
    fetch: (Q, Filter.Offset) => Future[TabPage[Row]],
    render: Row => HtmlElement,
    empty: FilterTab.Empty,
    page: ResultsPaging.PageNumber,
    debounceMs: () => UiState.ReloadDelay
)(using ExecutionContext):

  val state: Var[TabState[Row]] = Var(TabState[Row]())
  private val latest = Latest()
  // `var`s: the query last sent (to skip an unchanged one) and the pending debounce
  private var lastQuery: Option[Q] = None
  private var timer: Int = 0

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pane: HtmlElement =
    div(
      cls := s"$name-pane",
      ResultsPaging.countBar(
        state.signal.map(_.countLine(singular, noun)),
        state.signal.map(s => ResultsPaging.Loading(s.loading.value && s.loaded.value)),
        state.signal.map(s => ResultsPaging.Failed(s.error.nonEmpty))
      ),
      child.maybe <-- state.signal.map(
        _.error.map(m => p(cls := "form-error", role := "alert", s"Could not list $noun: ${m.sentence}"))
      ),
      child.maybe <-- state.signal.map(s =>
        Option
          .when(s.loaded.value && !s.loading.value && s.error.isEmpty && s.rows.isEmpty)(p(cls := "muted", empty))
      ),
      ul(cls := s"$name-list", children <-- state.signal.map(s => s.rows.take(s.shown.value).map(render))),
      ResultsPaging.moreButton(
        state.signal.map(_.moreLabel(page)),
        state.signal.map(s => ResultsPaging.Loading(s.loading.value)),
        () => more()
      )
    )

  /** Called on every UiState change: refetch only when the query has changed, after the debounce. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def refresh(): Unit =
    val q = query()
    if !lastQuery.contains(q) then
      lastQuery = Some(q)
      dom.window.clearTimeout(timer)
      timer = dom.window.setTimeout(() => load(), debounceMs().value)

  /** The first page of the current query, now. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def load(): Unit =
    val q = query()
    lastQuery = Some(q)
    state.update(TabState.loading)
    latest(fetch(q, Filter.Offset(0)))(
      p => state.set(TabState.first(p, page)),
      m => state.update(TabState.failed(_, Problem.Unclassified(Legacy.Text(m))))
    )

  /** "show more": the loaded rows first, then the next page from the server. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def more(): Unit =
    TabState.more(state.now(), page) match
      case Right(next)  => state.set(next)
      case Left(offset) =>
        val q = lastQuery.getOrElse(query())
        state.update(TabState.loading)
        latest(fetch(q, Filter.Offset(offset.value)))(
          p => state.update(TabState.appended(_, p, page)),
          m => state.update(TabState.failed(_, Problem.Unclassified(Legacy.Text(m))))
        )

object FilterTab:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Empty = Empty.T
  object Empty extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
