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
            cls := "btn btn--bare btn--block show-more",
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

  /** The query this tab sends: the GLOBAL filter (title included), as the Sources tab sends it — the
    * server relates it to events (see [[Api.EventQuery]]). A pure function of the UiState, so the
    * two lists cannot disagree with the Sources tab about what is filtered. */
  private def query(): Api.EventQuery = UiState.eventQuery()

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit =
    val q = query()
    if !lastQuery.contains(q) then
      lastQuery = Some(q)
      dom.window.clearTimeout(reloadTimer)
      reloadTimer = dom.window.setTimeout(() => load(), UiState.reloadDelay(Layout.tabReloadMs))

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


