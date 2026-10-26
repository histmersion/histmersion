package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Drops the answers to superseded requests (#1798 item 5) — the `reqId` / `mine == reqId` guard the
  * views spelled out by hand: `start()` hands out a ticket, and only the NEWEST ticket's answer is
  * applied, so an older response can never overwrite a newer query's. */
final class Latest:
  // `var`: the newest ticket handed out
  private var newest: Int = 0

  /** A new request: the tickets handed out before it are superseded. */
  def start(): Int = { newest += 1; newest }

  /** Whether `ticket` is still the newest request. */
  def isCurrent(ticket: Int): Boolean = ticket == newest

  /** Supersede every request still out: none of their answers is applied (a layer switched off, an
    * input unmounted). */
  def cancel(): Unit = { val _ = start() }

  /** `f`'s raw outcome, handed to `k` only while it is the newest request — for a view that answers a
    * failure its own way (or not at all), and the guard each view used to spell out with a counter. */
  def onCurrent[A](f: Future[A])(k: Try[A] => Unit)(using ExecutionContext): Unit =
    val ticket = start()
    f.onComplete(r => if isCurrent(ticket) then k(r))

  /** `f`'s outcome, applied only while it is the newest request: `ok` on success, `failed` with the
    * error message otherwise — a failure is never left unanswered (a view stuck on "Loading…"). */
  def apply[A](f: Future[A])(ok: A => Unit, failed: String => Unit)(using ExecutionContext): Unit =
    val ticket = start()
    f.onComplete(r => if isCurrent(ticket) then answer(r, ok, failed))

  private[web] def answer[A](r: Try[A], ok: A => Unit, failed: String => Unit): Unit = r match
    case Success(a) => ok(a)
    case Failure(e) => failed(Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.toString))

/** One page of a tab's answer: its rows, the server's total, and the count line's extras for it
  * (`200 concepts`, the filter's time window, …). */
final case class TabPage[Row](rows: List[Row], total: Int, extras: List[String] = Nil)

/** What a filter-driven list tab shows: the rows loaded from the server (a page at a time), how many
  * the list reveals, the server's total, the count line's extras, and whether a request is out or
  * failed. Pure. */
final case class TabState[Row](
    rows: List[Row] = Nil,
    total: Int = 0,
    shown: Int = 0,
    loading: Boolean = false,
    error: Option[String] = None,
    loaded: Boolean = false,
    extras: List[String] = Nil):

  /** Rows the list does not show yet — loaded or still on the server. */
  def left: Int = math.max(0, math.max(total, rows.size) - shown)

  /** The "show more" label ([[ResultsPaging.moreLabel]]); `None` when every row is shown. */
  def moreLabel(page: Int): Option[String] = ResultsPaging.moreLabel(left, page)

  /** The count line ([[ResultsPaging.countLine]]); `loading…` before the first answer. */
  def countLine(singular: String, plural: String): String =
    if !loaded then "loading…"
    else ResultsPaging.countLine(shown, math.max(total, rows.size), singular, plural, extras)

object TabState:
  /** A new query was sent: what is shown stays until the answer comes; any old error is gone. */
  def loading[Row](s: TabState[Row]): TabState[Row] = s.copy(loading = true, error = None)

  /** The first page of a query's answer. */
  def first[Row](p: TabPage[Row], page: Int): TabState[Row] =
    TabState(p.rows, p.total, math.min(page, p.rows.size), loaded = true, extras = p.extras)

  /** A further page from the server, appended (what "show more" asked for once the loaded rows ran out). */
  def appended[Row](s: TabState[Row], p: TabPage[Row], page: Int): TabState[Row] =
    val rows = s.rows ++ p.rows
    s.copy(rows = rows, total = p.total, shown = math.min(s.shown + page, rows.size), loading = false, error = None,
      extras = p.extras)

  /** A request failed: loading ends, the error is shown, the rows already there stay. */
  def failed[Row](s: TabState[Row], message: String): TabState[Row] =
    s.copy(loading = false, error = Some(message), loaded = true)

  /** "show more": reveal the next `page` loaded rows, or — when the loaded rows run out before the
    * total — `Left(offset)`, the next page to ask the server for (rows past the first fetch used to be
    * unreachable). */
  def more[Row](s: TabState[Row], page: Int): Either[Int, TabState[Row]] =
    if s.shown + page <= s.rows.size || s.rows.size >= s.total then Right(s.copy(shown = math.min(s.shown + page, s.rows.size)))
    else Left(s.rows.size)

/** One controller for the list tabs that follow the global filter (Articles, Events — #1798 item 5):
  * on a UiState change it recomputes the query, skips an unchanged one, debounces, drops answers to
  * superseded requests ([[Latest]]), shows the sidebar's count line ([[ResultsPaging]], `· loading…`
  * while a request is out), an error line (never stuck loading), the empty text, and ONE "show N more
  * (M left)" that reveals the loaded rows and then fetches the next page from the server.
  *
  * @param name     the CSS prefix (`articles` → `articles-pane`, `articles-list`)
  * @param singular the row's noun in the count line (`article`); `noun` its plural
  * @param query    the query, a pure function of the UiState
  * @param fetch    one page of the answer at an offset: its rows, the total and the count line's extras
  * @param page     how many rows "show more" reveals */
final class FilterTab[Q, Row](
    name: String,
    singular: String,
    noun: String,
    query: () => Q,
    fetch: (Q, Int) => Future[TabPage[Row]],
    render: Row => HtmlElement,
    empty: String,
    page: Int,
    debounceMs: () => Int)(using ExecutionContext):

  val state: Var[TabState[Row]] = Var(TabState[Row]())
  private val latest = Latest()
  // `var`s: the query last sent (to skip an unchanged one) and the pending debounce
  private var lastQuery: Option[Q] = None
  private var timer: Int           = 0

  def pane: HtmlElement =
    div(
      cls := s"$name-pane",
      ResultsPaging.countBar(state.signal.map(_.countLine(singular, noun)), state.signal.map(s => s.loading && s.loaded),
        state.signal.map(_.error.nonEmpty)),
      child.maybe <-- state.signal.map(_.error.map(m => p(cls := "form-error", role := "alert", s"Could not list $noun: $m"))),
      child.maybe <-- state.signal.map(s =>
        Option.when(s.loaded && !s.loading && s.error.isEmpty && s.rows.isEmpty)(p(cls := "muted", empty))),
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
      case Right(next) => state.set(next)
      case Left(offset) =>
        val q = lastQuery.getOrElse(query())
        state.update(TabState.loading)
        latest(fetch(q, offset))(p => state.update(TabState.appended(_, p, page)), m => state.update(TabState.failed(_, m)))
