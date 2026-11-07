package com.histmersion.web

import hearth.kindlings.optics.syntax.*
import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, YearFmt}
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.{EventPrecision, EventSummary, EventTaxonomy, EventTier}

object EventsTab:
  type When = When.T
  object When extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The tab ([[FilterTab]]): the GLOBAL filter (title included), as the Sources tab sends it — the server relates it
    * to events (see [[Api.EventQuery]]). A page at a time; "show more" fetches past the first.
    */

  private val tab = FilterTab[Api.EventQuery, EventSummary](
    name = com.histmersion.web.FilterTab.Name("events"),
    singular = com.histmersion.web.FilterTab.Singular("event"),
    noun = com.histmersion.web.FilterTab.Noun("events"),
    query = () => UiState.eventQuery(),
    fetch = (q, offset) =>
      ApiClient
        .events(q.modify(_.filter.offset).setTo(offset))
        .map(r => TabPage(r.events, r.total, List(ResultsPaging.span(UiState.from.now(), UiState.to.now())))),
    render = renderEvent,
    empty = com.histmersion.web.FilterTab.Empty("No events relate to the current filter."),
    page = Layout.tabPage,
    debounceMs = () => UiState.reloadDelay(Layout.tabReloadMs)
  )

  def pane: HtmlElement = tab.pane

  /** How many the list holds, once it has an answer (its tab's count). */
  val total: Signal[Option[com.histmersion.api.RowCount]] = tab.state.signal.map(s => Option.when(s.loaded)(com.histmersion.api.RowCount(math.max(s.total.value, s.rows.size))))

  /** Pure: a row's date — the stated dating where there is one (`250 CE ± 2500 (tephrochronology)`), else its
    * four-point span.
    */
  def when(e: EventSummary): com.histmersion.web.EventsTab.When = e.datingNote.getOrElse(YearFmt.span(e.timeSpan))

  private def renderEvent(evt: EventSummary): HtmlElement =
    li(
      cls := "event-row",
      marks.RowActions(
        marks.ItemRef(
          com.histmersion.api.personal.Target.item(com.histmersion.api.ItemKind.Event, evt.id),
          evt.label
        ),
        Route.Event(evt.id).path,
        EventsTab.when(evt)
      ),
      // its card on hover (Preview: the event's picture and credit, #1446); a plain click shows its detail
      a(
        cls := "event-label row-link",
        href := Route.Event(evt.id).path,
        dataAttr("event") := evt.id,
        onClick --> { ev =>
          if Router.plain(ev) then { ev.preventDefault(); ResultsPanel.show(Preview.Target.Event(evt.id)) }
        },
        // its kind's icon and its tier, as on the map (#1449)
        span(
          cls := s"event-icon hd-badge hd-ev hd-ev--${EventMarker.tierClass(evt.tier)} hd-ev--${EventTaxonomy.group(evt.kind)}",
          title := s"${EventTaxonomy.of(evt.kind).label} · ${
              if evt.tier == EventTier.Catalogue then "catalogue only" else "attested"
            }",
          onMountCallback(ctx => ctx.thisNode.ref.innerHTML = Glyphs.badge(EventTaxonomy.icon(evt.kind)))
        ),
        evt.label
      ),
      div(
        cls := "event-meta",
        span(cls := "event-kind", Fmt.display(evt.kind)),
        span(cls := "event-period", EventsTab.when(evt)),
        EventPrecision
          .label(evt.precision)
          .map(l =>
            span(
              cls := "event-precision",
              title := (if evt.precision == EventPrecision.Imprecise then "listed, not drawn on the map at a year"
                        else l),
              l
            )
          )
          .getOrElse(emptyNode),
        span(cls := "event-historicity", Fmt.display(evt.historicity))
      ),
      evt.description
        .filter(_.nonEmpty)
        .map { d =>
          div(cls := "event-desc", Fmt.clip(d, com.histmersion.api.Markdown.CharLimit(120)))
        }
        .getOrElse(emptyNode),
      evt.citekey
        .map { ck =>
          span(cls := "event-source", SourceLink(ck))
        }
        .getOrElse(emptyNode)
    )

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit = tab.refresh()

  def load(): Unit = tab.load()
