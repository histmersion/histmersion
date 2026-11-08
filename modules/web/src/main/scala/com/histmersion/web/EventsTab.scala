package com.histmersion
package web

import hearth.kindlings.optics.syntax.*
import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, YearFmt}
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.{EventPrecision, EventSummary, EventTaxonomy, EventTier}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Plural
import com.histmersion.api.catalogue.Year
import com.histmersion.api.RowCount
import com.histmersion.web.marks.RowActions
import com.histmersion.web.marks.ItemRef
import com.histmersion.api.personal.Target
import com.histmersion.api.ItemKind
import com.histmersion.api.personal.ItemKey
import com.histmersion.api.library.Title
import com.histmersion.api.UiLabels
import com.histmersion.api.Markdown

object EventsTab:
  type When = When.T
  object When extends Newtype[String, Pure]

  /** The tab ([[FilterTab]]): the GLOBAL filter (title included), as the Sources tab sends it — the server relates it
    * to events (see [[Api.EventQuery]]). A page at a time; "show more" fetches past the first.
    */

  private val tab = FilterTab[Api.EventQuery, EventSummary](
    name = FilterTab.Name("events"),
    singular = Plural.Noun.Singular("event"),
    noun = Plural.Noun.Plural("events"),
    query = () => UiState.eventQuery(),
    fetch = (q, offset) =>
      ApiClient
        .events(q.modify(_.filter.offset).setTo(offset))
        .map(r =>
          TabPage(
            r.events,
            r.total,
            List(
              TabPage.Extras(ResultsPaging.span(UiState.from.now().map(Year(_)), UiState.to.now().map(Year(_))).value)
            )
          )
        ),
    render = renderEvent,
    empty = FilterTab.Empty("No events relate to the current filter."),
    page = ResultsPaging.PageNumber(Layout.tabPage.value),
    debounceMs = () => UiState.reloadDelay(UiState.ReloadDelayNormalMs(Layout.tabReloadMs.value))
  )

  def pane: HtmlElement = tab.pane

  /** How many the list holds, once it has an answer (its tab's count). */
  val total: Signal[Option[RowCount]] =
    tab.state.signal.map(s => Option.when(s.loaded.value)(RowCount(math.max(s.total.value, s.rows.size))))

  /** Pure: a row's date — the stated dating where there is one (`250 CE ± 2500 (tephrochronology)`), else its
    * four-point span.
    */
  def when(e: EventSummary): When = When(e.datingNote.getOrElse(YearFmt.span(e.timeSpan)))

  private def renderEvent(evt: EventSummary): HtmlElement =
    li(
      cls := "event-row",
      RowActions(
        ItemRef(
          Target.item(ItemKind.Event, ItemKey(evt.id.value)),
          Title(evt.label.value)
        ),
        ItemRef.Href(Route.Event(Route.Event.Id(evt.id.value)).path),
        when(evt).value
      ),
      // its card on hover (Preview: the event's picture and credit, #1446); a plain click shows its detail
      a(
        cls := "event-label row-link",
        href := Route.Event(Route.Event.Id(evt.id.value)).path,
        dataAttr("event") := evt.id.value,
        onClick --> { ev =>
          if Router.plain(ev) then {
            ev.preventDefault(); ResultsPanel.show(Preview.Target.Event(Preview.Target.Event.Id(evt.id.value)))
          }
        },
        // its kind's icon and its tier, as on the map (#1449)
        span(
          cls := s"event-icon hd-badge hd-ev hd-ev--${EventMarker.tierClass(Api.EventQuery.Tier(evt.tier.value))} hd-ev--${EventTaxonomy.group(evt.kind)}",
          title := s"${EventTaxonomy.of(evt.kind).label} · ${
              if evt.tier.value == EventTier.Catalogue then "catalogue only" else "attested"
            }",
          onMountCallback(ctx =>
            ctx.thisNode.ref.innerHTML = Glyphs.badge(Glyphs.BadgeName(EventTaxonomy.icon(evt.kind).value)).value
          )
        ),
        evt.label.value
      ),
      div(
        cls := "event-meta",
        span(cls := "event-kind", Fmt.display(UiLabels.Text(evt.kind.value)).value),
        span(cls := "event-period", when(evt).value),
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
        span(cls := "event-historicity", Fmt.display(UiLabels.Text(evt.historicity.value)).value)
      ),
      evt.description
        .filter(_.value.nonEmpty)
        .map { d =>
          div(cls := "event-desc", Fmt.clip(d.value, Markdown.CharLimit(120)))
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
