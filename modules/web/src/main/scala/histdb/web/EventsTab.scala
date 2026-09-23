package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object EventsTab:

  private val eventsVar = Var[List[EventSummary]](Nil)
  private val totalVar = Var(0)
  private val shownVar = Var(0)
  private val loading = Var(false)
  private val searchQuery = Var("")
  private var searchTimer: Int = 0

  def pane: HtmlElement =
    div(
      cls := "events-pane",
      div(
        cls := "events-search",
        input(
          typ := "search",
          placeholder := "search events…",
          controlled(
            value <-- searchQuery.signal,
            onInput.mapToValue --> { v =>
              searchQuery.set(v)
              debounceLoad()
            }
          )
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
        span(cls := "event-kind", evt.kind),
        span(cls := "event-period", fmtTimeSpan(evt.timeSpan)),
        span(cls := "event-historicity", evt.historicity)
      ),
      evt.description.filter(_.nonEmpty).map { d =>
        div(cls := "event-desc", Fmt.clip(d, 120))
      }.getOrElse(emptyNode),
      evt.citekey.map { ck =>
        a(
          cls := "event-source",
          href := "#",
          onClick.preventDefault --> { _ => ArticleModal.open(ck) },
          s"[$ck]"
        )
      }.getOrElse(emptyNode)
    )

  def load(): Unit =
    loading.set(true)
    val q = searchQuery.now()
    val from = UiState.from.now()
    val to = UiState.to.now()
    val query = Api.EventQuery(
      q = if q.isEmpty then None else Some(q),
      from = from,
      to = to,
      limit = Some(200)
    )
    ApiClient.events(query).foreach { result =>
      eventsVar.set(result.events)
      totalVar.set(result.total)
      shownVar.set(math.min(25, result.events.length))
      loading.set(false)
    }

  private def debounceLoad(): Unit =
    dom.window.clearTimeout(searchTimer)
    searchTimer = dom.window.setTimeout(() => load(), 300)
