package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object App:

  private var reqId: Int = 0
  private var timer: Int = 0
  private var totalKey: Option[String] = None
  private var totalCount: Int = 0
  private var lastParams: Option[String] = None
  private var config: UiConfig = uninitialized

  def init(): Unit =
    ApiClient.config().foreach { ui =>
      config = ui
      UiState.init(ui)

      val appEl = div(
        cls := "histdb-app",
        MapView.init(ui),
        sidebarElement(ui),
        TimelineView.init(ui),
        FilterBar.init(ui),
        ViewportToggle.init(ui),
        ArticleModal.element,
        statusElement
      )

      renderOnDomContentLoaded(dom.document.getElementById("app"), appEl)

      TreeTab.init(ui)
      ArticleModal.restore()
      ArticlesTab.restore()

      loadTagline()

      UiState.signal.foreach { _ =>
        schedule()
      }(using unsafeWindowOwner)

      lastParams = Some(paramsKey())
      load()
    }

  private val statusVar = Var("")
  private def statusElement: HtmlElement =
    span(idAttr := "status", child.text <-- statusVar.signal)

  private def sidebarElement(ui: UiConfig): HtmlElement =
    div(
      cls := "sidebar",
      ResultsList.element,
      TreeTab.pane,
      ArticlesTab.pane
    )

  private def loadTagline(): Unit =
    ApiClient.sources(Filter(limit = 1)).foreach { r =>
      Option(dom.document.getElementById("tagline")).foreach { el =>
        el.textContent = s"${r.total} sources · search, then read"
      }
    }

  private def paramsKey(): String =
    val f = UiState.toFilter()
    js.JSON.stringify(js.Dynamic.literal(
      q = f.q.orNull,
      from = f.period.map(_.start.toDouble).getOrElse(0.0),
      to = f.period.map(_.end.toDouble).getOrElse(0.0),
      tags = f.tags.mkString(","),
      authors = f.authors.mkString(","),
      topics = f.topics.mkString(","),
      status = f.status.mkString(","),
      places = f.places.mkString(","),
      sort = f.sort.toString,
      bbox = f.bbox.map(b => s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}").orNull,
      yearFrom = f.years.map(_.start.toDouble).getOrElse(0.0),
      yearTo = f.years.map(_.end.toDouble).getOrElse(0.0)
    ))

  private def schedule(): Unit =
    val key = paramsKey()
    if lastParams.contains(key) then ()
    else
      lastParams = Some(key)
      dom.window.clearTimeout(timer)
      timer = dom.window.setTimeout(() => load(), config.debounce.reloadMs)

  private def load(): Unit =
    reqId += 1
    val mine = reqId

    val f = UiState.toFilter()
    val fNoBox = UiState.toFilter(except = Set("bbox"))
    val inView = UiState.inView.now()
    val a = UiState.from.now().getOrElse(config.time.defaultFrom)
    val b = UiState.to.now().getOrElse(config.time.defaultTo)

    statusVar.set("loading…")
    ResultsList.busy(true)

    ApiClient.findings(Api.FindingsQuery.of(f)).foreach { points =>
      if mine != reqId then ()
      else
        if !inView then
          totalCount = points.length
        else
          ApiClient.findings(Api.FindingsQuery.of(fNoBox)).foreach { basePoints =>
            if mine == reqId then totalCount = basePoints.length
          }

        MapView.render(points, (a, b))

        val sourceFilter = f.copy(limit = config.pageSize)
        ApiClient.sources(sourceFilter).foreach { result =>
          if mine != reqId then ()
          else
            ResultsList.renderSources(result, ResultsList.SourceRenderOpts(
              findings = points.length,
              total = totalCount,
              inView = inView,
              params = Some(sourceFilter)
            ))
            statusVar.set("")
        }
    }
