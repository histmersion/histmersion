package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object AppState:

  val filter: Var[Filter] = Var(Filter())

  val results: Var[Option[SearchResult]] = Var(None)
  val geoHits: Var[List[GeoHit]]        = Var(Nil)

  val activeTab: Var[String] = Var("sources")

  val openItem: Var[Option[String]] = Var(None)

  def reload(): Unit =
    val f = filter.now()
    ApiClient.sources(f).foreach(r => results.set(Some(r)))
    ApiClient.findings(Api.FindingsQuery.of(f)).foreach(hits => geoHits.set(hits))

  def appElement: HtmlElement =
    div(
      headerElement,
      div(cls := "main-layout",
        div(cls := "sidebar",
          tabBar,
          child <-- results.signal.map {
            case None    => div(cls := "status", "Loading...")
            case Some(r) => div(cls := "count", s"${r.total} source(s)")
          }
        )
      ),
      onMountCallback(_ => reload())
    )

  private def headerElement: HtmlElement =
    headerTag(
      h1("hist-db ", span(cls := "muted", "· atlas")),
      div(cls := "controls",
        input(
          typ := "search",
          placeholder := "search the corpus...",
          controlled(
            value <-- filter.signal.map(_.q.getOrElse("")),
            onInput.mapToValue.map(v => if v.isEmpty then None else Some(v)) --> { q =>
              filter.update(_.copy(q = q))
              reload()
            }
          )
        )
      )
    )

  private def tabBar: HtmlElement =
    div(cls := "tabs",
      List("sources", "articles", "by time", "by region").map { tab =>
        button(
          cls := "tab",
          cls("active") <-- activeTab.signal.map(_ == tab),
          tab,
          onClick --> { _ => activeTab.set(tab) }
        )
      }
    )
