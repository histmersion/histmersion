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
    singular = com.histmersion.api.Plural.Noun.Singular("event"),
    noun = com.histmersion.api.Plural.Noun.Plural("events"),
    query = () => UiState.eventQuery(),
    fetch = (q, offset) =>
      ApiClient
        .events(q.modify(_.filter.offset).setTo(offset))
        .map(r => TabPage(r.events, r.total, List(ResultsPaging.span(UiState.from.now().map(com.histmersion.api.catalogue.Year(_)), UiState.to.now().map(com.histmersion.api.catalogue.Year(_)))))),
    render = renderEvent,
    empty = com.histmersion.web.FilterTab.Empty("No events relate to the current filter."),
    page = ResultsPaging.PageNumber(Layout.tabPage.value),
    debounceMs = () => UiState.reloadDelay(UiState.ReloadDelayNormalMs(Layout.tabReloadMs.value))
  )

  def pane: HtmlElement = tab.pane

  /** How many the list holds, once it has an answer (its tab's count). */
  val total: Signal[Option[com.histmersion.api.RowCount]] = tab.state.signal.map(s => Option.when(s.loaded)(com.histmersion.api.RowCount(math.max(s.total.value, s.rows.size))))

  /** Pure: a row's date — the stated dating where there is one (`250 CE ± 2500 (tephrochronology)`), else its
    * four-point span.
    */
  def when(e: EventSummary): com.histmersion.web.EventsTab.When = When(e.datingNote.getOrElse(YearFmt.span(e.timeSpan)))

  private def renderEvent(evt: EventSummary): HtmlElement =
    li(
      cls := "event-row",
      marks.RowActions(
        marks.ItemRef(
          com.histmersion.api.personal.Target.item(com.histmersion.api.ItemKind.Event, com.histmersion.api.personal.ItemKey(evt.id.value)),
          marks.ItemRef.Title(evt.label.value)
        ),
        marks.ItemRef.Href(Route.Event(Route.Event.Id(evt.id.value)).path),
        library.TableRow.Byline(EventsTab.when(evt).value)
      ),
      // its card on hover (Preview: the event's picture and credit, #1446); a plain click shows its detail
      a(
        cls := "event-label row-link",
        href := Route.Event(Route.Event.Id(evt.id.value)).path,
        dataAttr("event") := evt.id.value,
        onClick --> { ev =>
          if Router.plain(ev) then { ev.preventDefault(); ResultsPanel.show(Preview.Target.Event(Preview.Target.Event.Id(evt.id.value))) }
        },
        // its kind's icon and its tier, as on the map (#1449)
        span(
          cls := s"event-icon hd-badge hd-ev hd-ev--${EventMarker.tierClass(Api.EventQuery.Tier(evt.tier.value))} hd-ev--${EventTaxonomy.group(evt.kind)}",
          title := s"${EventTaxonomy.of(evt.kind).label} · ${
              if evt.tier.value == EventTier.Catalogue then "catalogue only" else "attested"
            }",
          onMountCallback(ctx => ctx.thisNode.ref.innerHTML = Glyphs.badge(Glyphs.BadgeName(EventTaxonomy.icon(evt.kind).value)).value)
        ),
        evt.label.value
      ),
      div(
        cls := "event-meta",
        span(cls := "event-kind", Fmt.display(com.histmersion.api.UiLabels.Text(evt.kind.value)).value),
        span(cls := "event-period", EventsTab.when(evt).value),
        EventPrecision
          .label(evt.precision)
          .map(l =>
            span(
              cls := "event-precision",
              title := (if evt.precision.value == EventPrecision.Imprecise then "listed, not drawn on the map at a year"
                        else l),
              l
            )
          )
          .getOrElse(emptyNode),
        span(cls := "event-historicity", Fmt.display(com.histmersion.api.UiLabels.Text(evt.historicity.value)).value)
      ),
      evt.description
        .filter(_.value.nonEmpty)
        .map { d =>
          div(cls := "event-desc", Fmt.clip(d.value, com.histmersion.api.Markdown.CharLimit(120)))
        }
        .getOrElse(emptyNode),
      evt.citekey
        .map { ck =>
          span(cls := "event-source", SourceLink(Preview.Target.Source.Citekey(ck.value)))
        }
        .getOrElse(emptyNode)
    )

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit = tab.refresh()

  def load(): Unit = tab.load()
