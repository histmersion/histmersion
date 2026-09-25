package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object EventsTab:

  private val eventsVar = Var[List[EventSummary]](Nil)
  private val totalVar = Var(0)
  private val shownVar = Var(0)
  private val loading = Var(false)

  def pane: HtmlElement =
    div(
      cls := "events-pane",
      // the tab's own search box is UiState.aq — in the URL, shared by both list tabs, and the
      // query below reads it back from there (no private copy)
      input(
        typ := "search",
        cls := "tab-search",
        placeholder := "search events…",
        aria.label := "search events",
        controlled(
          value <-- UiState.aq.signal.map(_.getOrElse("")),
          onInput.mapToValue --> { v => UiState.set(UiState.aq := Some(v.trim).filter(_.nonEmpty))() }
        )
      ),
      child <-- loading.signal.map {
        case true => div(cls := "events-loading", "Loading…")
        case false => emptyNode
      },
      ul(
        cls := "events-list",
        children <-- eventsVar.signal.combineWith(shownVar.signal).map { (evts, n) =>
          evts.take(n).map(renderEvent)
        }
      ),
      child <-- eventsVar.signal.combineWith(shownVar.signal, totalVar.signal).map { (evts, n, t) =>
        val remaining = math.min(evts.length, t) - n
        if remaining > 0 then
          button(
            cls := "show-more",
            s"show $remaining more",
            onClick --> { _ => shownVar.update(_ + 25) }
          )
        else emptyNode
      }
    )

  private def fmtTimeSpan(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"≥${Fmt.yr(y)}"),
      ts.beginBy.map(y => Fmt.yr(y)),
      ts.endFrom.map(y => Fmt.yr(y)),
      ts.notAfter.map(y => s"≤${Fmt.yr(y)}")
    ).flatten
    if parts.length >= 2 then s"${parts.head}–${parts.last}"
    else parts.headOption.getOrElse("undated")

  private def renderEvent(evt: EventSummary): HtmlElement =
    li(
      cls := "event-row",
      div(cls := "event-label", evt.label),
      div(
        cls := "event-meta",
        span(cls := "event-kind", Fmt.display(evt.kind)),
        span(cls := "event-period", fmtTimeSpan(evt.timeSpan)),
        span(cls := "event-historicity", Fmt.display(evt.historicity))
      ),
      evt.description.filter(_.nonEmpty).map { d =>
        div(cls := "event-desc", Fmt.clip(d, 120))
      }.getOrElse(emptyNode),
      evt.citekey.map { ck =>
        span(cls := "event-source", SourceLink(ck))
      }.getOrElse(emptyNode)
    )

  private var reqId: Int = 0
  private var lastQuery: Option[Api.EventQuery] = None
  private var reloadTimer: Int = 0

  private def query(): Api.EventQuery =
    val local = UiState.aq.now().getOrElse("").trim
    val global = UiState.q.now().map(_.trim).filter(_.nonEmpty)
    val combined = List(global, Option(local).filter(_.nonEmpty)).flatten.mkString(" ")
    val from = UiState.from.now()
    val to = UiState.to.now()
    val placeId = UiState.placeIds.now().headOption
    Api.EventQuery(
      q = if combined.isEmpty then None else Some(combined),
      from = from,
      to = to,
      placeId = placeId,
      limit = Some(200)
    )

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit =
    val q = query()
    if !lastQuery.contains(q) then
      lastQuery = Some(q)
      dom.window.clearTimeout(reloadTimer)
      reloadTimer = dom.window.setTimeout(() => load(), Layout.tabReloadMs)

  def load(): Unit =
    loading.set(true)
    reqId += 1
    val mine = reqId
    val q = query()
    lastQuery = Some(q)
    ApiClient.events(q).foreach { result =>
      if mine == reqId then
        eventsVar.set(result.events)
        totalVar.set(result.total)
        shownVar.set(math.min(25, result.events.length))
        loading.set(false)
    }


