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

  /** `f`'s outcome, applied only while it is the newest request: `ok` on success, `failed` with the
    * error message otherwise — a failure is never left unanswered (a view stuck on "Loading…"). */
  def apply[A](f: Future[A])(ok: A => Unit, failed: String => Unit)(using ExecutionContext): Unit =
    val ticket = start()
    f.onComplete(r => if isCurrent(ticket) then answer(r, ok, failed))

  private[web] def answer[A](r: Try[A], ok: A => Unit, failed: String => Unit): Unit = r match
    case Success(a) => ok(a)
    case Failure(e) => failed(Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.toString))

/** What a filter-driven list tab shows: the rows loaded from the server (a page at a time), how many
  * the list reveals, the server's total, and whether a request is out or failed. Pure. */
final case class TabState[Row](
    rows: List[Row] = Nil,
    total: Int = 0,
    shown: Int = 0,
    loading: Boolean = false,
    error: Option[String] = None,
    loaded: Boolean = false):

  /** Rows the list does not show yet — loaded or still on the server. */
  def left: Int = math.max(0, math.max(total, rows.size) - shown)

  /** The "show more" label: `show 25 more (175 left)`, or `show 3 more` when that is all; `None` when
    * every row is shown. */
  def moreLabel(page: Int): Option[String] =
    Option.when(left > 0) {
      val n = math.min(page, left)
      if n == left then s"show $n more" else s"show $n more ($left left)"
    }

object TabState:
  /** A new query was sent: what is shown stays until the answer comes; any old error is gone. */
  def loading[Row](s: TabState[Row]): TabState[Row] = s.copy(loading = true, error = None)

  /** The first page of a query's answer. */
  def first[Row](rows: List[Row], total: Int, page: Int): TabState[Row] =
    TabState(rows, total, math.min(page, rows.size), loaded = true)

  /** A further page from the server, appended (what "show more" asked for once the loaded rows ran out). */
  def appended[Row](s: TabState[Row], more: List[Row], total: Int, page: Int): TabState[Row] =
    val rows = s.rows ++ more
    s.copy(rows = rows, total = total, shown = math.min(s.shown + page, rows.size), loading = false, error = None)

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
  * superseded requests ([[Latest]]), shows a loading line, an error line (never stuck on "Loading…"),
  * the empty text, and ONE "show N more (M left)" that reveals the loaded rows and then fetches the next
  * page from the server.
  *
  * @param name     the CSS prefix (`articles` → `articles-pane`, `articles-list`, `articles-loading`)
  * @param query    the query, a pure function of the UiState
  * @param fetch    one page of the answer at an offset: its rows and the total
  * @param fetchSize how many rows one request asks for; `page` how many "show more" reveals */
final class FilterTab[Q, Row](
    name: String,
    noun: String,
    query: () => Q,
    fetch: (Q, Int) => Future[(List[Row], Int)],
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
      child <-- state.signal.map(s => if s.loading then div(cls := s"$name-loading", "Loading…") else emptyNode),
      child.maybe <-- state.signal.map(_.error.map(m => p(cls := "form-error", role := "alert", s"Could not list $noun: $m"))),
      child.maybe <-- state.signal.map(s =>
        Option.when(s.loaded && !s.loading && s.error.isEmpty && s.rows.isEmpty)(p(cls := "muted", empty))),
      ul(cls := s"$name-list", children <-- state.signal.map(s => s.rows.take(s.shown).map(render))),
      child <-- state.signal.map(s =>
        s.moreLabel(page).fold(emptyNode)(label =>
          button(cls := "btn btn--bare btn--block show-more", label, disabled := s.loading, onClick --> (_ => more()))))
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
    latest(fetch(q, 0))((rows, total) => state.set(TabState.first(rows, total, page)),
      m => state.update(TabState.failed(_, m)))

  /** "show more": the loaded rows first, then the next page from the server. */
  def more(): Unit =
    TabState.more(state.now(), page) match
      case Right(next) => state.set(next)
      case Left(offset) =>
        val q = lastQuery.getOrElse(query())
        state.update(TabState.loading)
        latest(fetch(q, offset))((rows, total) => state.update(TabState.appended(_, rows, total, page)),
          m => state.update(TabState.failed(_, m)))
