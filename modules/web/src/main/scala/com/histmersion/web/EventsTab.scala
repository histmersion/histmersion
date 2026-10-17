package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object EventsTab:

  /** The tab ([[FilterTab]]): the GLOBAL filter (title included), as the Sources tab sends it — the
    * server relates it to events (see [[Api.EventQuery]]). A page at a time; "show more" fetches past
    * the first. */
  private val tab = FilterTab[Api.EventQuery, EventSummary](
    name = "events", noun = "events",
    query = () => UiState.eventQuery(),
    fetch = (q, offset) => ApiClient.events(q.copy(filter = q.filter.copy(offset = offset))).map(r => (r.events, r.total)),
    render = renderEvent,
    empty = "No events relate to the current filter.",
    page = Layout.tabPage,
    debounceMs = () => UiState.reloadDelay(Layout.tabReloadMs))

  def pane: HtmlElement = tab.pane

  /** Pure: a row's date — the stated dating where there is one (`250 CE ± 2500 (tephrochronology)`),
    * else its four-point span. */
  def when(e: EventSummary): String = e.datingNote.getOrElse(YearFmt.span(e.timeSpan))

  private def renderEvent(evt: EventSummary): HtmlElement =
    li(
      cls := "event-row",
      // its card on hover (Preview: the event's picture and credit, #1446)
      div(cls := "event-label", dataAttr("event") := evt.id,
        // its kind's icon and its tier, as on the map (#1449)
        span(cls := s"event-icon hd-badge hd-ev hd-ev--${EventMarker.tierClass(evt.tier)} hd-ev--${EventTaxonomy.group(evt.kind)}",
          title := s"${EventTaxonomy.of(evt.kind).label} · ${if evt.tier == EventTier.Catalogue then "catalogue only" else "attested"}",
          onMountCallback(ctx => ctx.thisNode.ref.innerHTML = Glyphs.badge(EventTaxonomy.icon(evt.kind)))),
        evt.label),
      div(
        cls := "event-meta",
        span(cls := "event-kind", Fmt.display(evt.kind)),
        span(cls := "event-period", EventsTab.when(evt)),
        EventPrecision.label(evt.precision).map(l => span(cls := "event-precision",
          title := (if evt.precision == EventPrecision.Imprecise then "listed, not drawn on the map at a year" else l), l))
          .getOrElse(emptyNode),
        span(cls := "event-historicity", Fmt.display(evt.historicity))
      ),
      evt.description.filter(_.nonEmpty).map { d =>
        div(cls := "event-desc", Fmt.clip(d, 120))
      }.getOrElse(emptyNode),
      evt.citekey.map { ck =>
        span(cls := "event-source", SourceLink(ck))
      }.getOrElse(emptyNode)
    )

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit = tab.refresh()

  def load(): Unit = tab.load()
