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
    dom.console.log("hist-db: init")
    ApiClient.config().failed.foreach { e =>
      dom.console.error("hist-db: config failed", e.getMessage)
      Option(dom.document.getElementById("app")).foreach(_.textContent = s"Failed to load config: ${e.getMessage}")
    }
    ApiClient.config().foreach { ui =>
      config = ui
      UiState.init(ui)

      val appEl = div(
        cls := "histdb-app",
        styleAttr <-- sidebarWidth.signal.map(w => s"grid-template-columns:1fr ${w}px"),
        MapView.init(ui),
        sidebarElement(ui),
        TimelineView.init(ui),
        FilterBar.init(ui),
        ViewportToggle.init(ui),
        ArticleModal.element,
        statusElement
      )

      render(dom.document.getElementById("app"), appEl)

      TreeTab.init(ui)
      ArticleModal.restore()
      ArticlesTab.restore()

      dom.document.addEventListener("click", { (e: dom.MouseEvent) =>
        val target = e.target.asInstanceOf[dom.Element]
        val chip = Option(target.closest("[data-filter-key]"))
        chip.foreach { el =>
          val key = el.getAttribute("data-filter-key")
          val value = el.getAttribute("data-filter-value")
          if key != null && value != null then
            FilterBar.addFilter(key, value)
        }
      })

      loadTagline()

      UiState.signal.foreach { _ =>
        schedule()
        val tab = activeTab
        if tab == "articles" then ArticlesTab.load()
        else if tab == "events" then EventsTab.load()
      }(using unsafeWindowOwner)

      lastParams = Some(paramsKey())
      load()
    }

  private val statusVar = Var("")
  private def statusElement: HtmlElement =
    span(idAttr := "status", child.text <-- statusVar.signal)

  private val sidebarWidth = Var(360)

  private def activeTab: String = UiState.tab.now().getOrElse("sources")
  private def activeTabSignal: Signal[String] = UiState.tab.signal.map(_.getOrElse("sources"))
  private def setActiveTab(key: String): Unit =
    UiState.set("tab" -> Some(key))(push = true)
    if key == "articles" then ArticlesTab.load()
    else if key == "events" then EventsTab.load()

  private def sidebarElement(ui: UiConfig): HtmlElement =
    val tabs = List("sources" -> "sources", "articles" -> "articles", "events" -> "events", "bytime" -> "by time", "byregion" -> "by region")
    div(
      cls := "sidebar",
      div(
        cls := "sidebar-resize",
        onPointerDown --> { ev =>
          ev.preventDefault()
          val startX = ev.clientX
          val startW = sidebarWidth.now()
          val target = ev.target.asInstanceOf[dom.HTMLElement]
          target.setPointerCapture(ev.pointerId)
          val move = { (me: dom.PointerEvent) =>
            val nw = Math.max(200, Math.min(800, startW - (me.clientX - startX).toInt))
            sidebarWidth.set(nw)
          }: js.Function1[dom.PointerEvent, Unit]
          val up = { (_: dom.PointerEvent) =>
            target.removeEventListener("pointermove", move)
          }: js.Function1[dom.PointerEvent, Unit]
          target.addEventListener("pointermove", move)
          target.addEventListener("pointerup", up, js.Dynamic.literal(once = true).asInstanceOf[dom.EventListenerOptions])
        }
      ),
      div(
        cls := "tabs",
        tabs.map { (key, label) =>
          button(
            cls <-- activeTabSignal.map(t => if t == key then "tab active" else "tab"),
            label,
            onClick --> { _ => setActiveTab(key) }
          )
        }
      ),
      div(
        display <-- activeTabSignal.map(t => if t == "sources" then "" else "none"),
        ResultsList.element
      ),
      div(
        display <-- activeTabSignal.map(t => if t == "articles" then "" else "none"),
        ArticlesTab.pane
      ),
      div(
        display <-- activeTabSignal.map(t => if t == "events" then "" else "none"),
        EventsTab.pane
      ),
      div(
        display <-- activeTabSignal.map(t => if t == "bytime" || t == "byregion" then "" else "none"),
        TreeTab.pane
      )
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
