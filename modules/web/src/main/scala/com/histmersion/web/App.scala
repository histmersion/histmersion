package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.ui.{Layout, SidebarTab}
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
  private var cachedFindings: List[GeoHit] = Nil
  private var cachedNonTimeKey: String = ""

  def init(): Unit =
    dom.console.log("Histmersion: init")
    ApiClient.config().failed.foreach { e =>
      dom.console.error("Histmersion: config failed", e.getMessage)
      Option(dom.document.getElementById("app")).foreach(_.textContent = s"Failed to load config: ${e.getMessage}")
    }
    ApiClient.config().foreach { ui =>
      config = ui
      UiState.init(ui)
      Viewport.init(ui)
      MobileChrome.install()
      Auth.init(ui.writeKeyRequired)
      RunsView.enabled.set(ui.agentsEnabled)
      MergeView.configure(ui)

      val appEl = div(
        cls := "histmersion-app",
        MobileChrome.rootClasses,
        MobileChrome.bar(ui),
        // the dragged width is data; the grid that uses it is the stylesheet's
        styleAttr <-- sidebarWidth.signal.map(w => s"--sidebar-w:${w}px"),
        MapView.init(ui),
        PlacesInput.mapChips,
        sidebarElement(ui),
        TimelineView.init(ui),
        FilterBar.init(ui),
        ViewportToggle.init(ui),
        ArticleModal.element,
        MobileChrome.scrim,
        MobileChrome.edges,
        statusElement
      )

      render(dom.document.getElementById("app"), appEl)
      Preview.install()

      TreeTab.init(ui)
      ArticleModal.restore()
      WindowManager.installKeys()
      val initTab = activeTab
      if initTab == SidebarTab.Articles.key then ArticlesTab.load()
      else if initTab == SidebarTab.Events.key then EventsTab.load()
      else if initTab == SidebarTab.ByTime.key || initTab == SidebarTab.ByRegion.key then TreeTab.show(initTab)

      loadTagline()

      UiState.signal.foreach { _ =>
        schedule()
        val tab = activeTab
        if tab == SidebarTab.Articles.key then ArticlesTab.refresh()
        else if tab == SidebarTab.Events.key then EventsTab.refresh()
      }(using unsafeWindowOwner)

      lastParams = Some(paramsKey())
      load()
    }

  private val statusVar = Var("")
  private def statusElement: HtmlElement =
    span(idAttr := "status", child.text <-- statusVar.signal)

  private val sidebarWidth = Var(Layout.sidebarDefault.value)

  private def activeTab: String = UiState.tab.now().getOrElse(SidebarTab.Sources.key)
  private def activeTabSignal: Signal[String] = UiState.tab.signal.map(_.getOrElse(SidebarTab.Sources.key))
  private def setActiveTab(key: String): Unit =
    UiState.set(UiState.tab := Some(key))(push = true)
    if key == SidebarTab.Articles.key then ArticlesTab.load()
    else if key == SidebarTab.Events.key then EventsTab.load()
    else if key == SidebarTab.ByTime.key || key == SidebarTab.ByRegion.key then TreeTab.show(key)

  private def sidebarElement(ui: UiConfig): HtmlElement =
    val tabs = SidebarTab.values.toList.map(t => t.key -> t.label)
    div(
      cls := "sidebar",
      // the bottom sheet's height in the narrow layout (the classes do nothing on a wide screen)
      cls <-- UiState.sheet.signal.map(s => s"sheet-${s.wire}"),
      sheetHandle,
      div(
        cls := "sidebar-resize",
        onPointerDown --> { ev =>
          ev.preventDefault()
          val startX = ev.clientX
          val startW = sidebarWidth.now()
          val target = ev.target.asInstanceOf[dom.HTMLElement]
          target.setPointerCapture(ev.pointerId)
          val move = { (me: dom.PointerEvent) =>
            val nw = Math.max(Layout.sidebarMinWidth.value, Math.min(Layout.sidebarMaxWidth.value, startW - (me.clientX - startX).toInt))
            sidebarWidth.set(nw)
          }: js.Function1[dom.PointerEvent, Unit]
          val up = { (_: dom.PointerEvent) =>
            target.removeEventListener("pointermove", move)
          }: js.Function1[dom.PointerEvent, Unit]
          target.addEventListener("pointermove", move)
          target.addEventListener("pointerup", up, js.Dynamic.literal(once = true).asInstanceOf[dom.EventListenerOptions])
        }
      ),
      titleFilter,
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
        cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Sources.key),
        ResultsList.element
      ),
      div(
        cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Articles.key),
        ArticlesTab.pane
      ),
      div(
        cls("is-hidden") <-- activeTabSignal.map(_ != SidebarTab.Events.key),
        EventsTab.pane
      ),
      div(
        cls("is-hidden") <-- activeTabSignal.map(t => t != SidebarTab.ByTime.key && t != SidebarTab.ByRegion.key),
        TreeTab.pane
      )
    )

  /** The bottom sheet's grab handle (shown only in the narrow layout): a tap moves the sheet one step
    * up (from full back to the strip); a vertical drag lets go at the nearest of the three. */
  private def sheetHandle: HtmlElement =
    // `var`s: the drag in progress — where it started, and whether it moved enough to be a drag
    var startY  = 0.0
    var dragged = false
    def h = dom.window.innerHeight.toDouble
    /** A length token of the stylesheet's, in px. Measured, not parsed: the tokens are `clamp()` and
      * `calc()` of the screen's size, which only layout resolves. */
    def px(name: String) =
      scala.util.Try {
        val probe = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        probe.className = "length-probe"
        probe.style.setProperty("--probe", s"var($name)")
        dom.document.body.appendChild(probe)
        val v = probe.getBoundingClientRect().height
        probe.remove()
        v
      }.getOrElse(0.0)
    def sheetOf(ev: dom.PointerEvent) = Option(ev.target.asInstanceOf[dom.Element].closest(".sidebar"))
      .map(_.asInstanceOf[dom.HTMLElement])
    button(
      typ := "button",
      cls := "sheet-handle",
      aria.label := "resize the list",
      title <-- UiState.sheet.signal.map(s => if s == Sheet.Full then "shrink the list" else "pull the list up"),
      span(cls := "sheet-grip"),
      onPointerDown --> { ev =>
        startY = ev.clientY
        dragged = false
        ev.target.asInstanceOf[dom.HTMLElement].setPointerCapture(ev.pointerId)
      },
      onPointerMove --> { ev =>
        if ev.buttons != 0 then   // pressed: a mouse hovering over the handle is not a drag
          if math.abs(ev.clientY - startY) > 12 then dragged = true
          // the sheet follows the finger; the stylesheet uses the height while `sheet-dragging` is on
          if dragged then sheetOf(ev).foreach { el =>
            el.classList.add("sheet-dragging")
            el.style.setProperty("--sheet-live", s"${math.max(px("--sheet-peek"), h - ev.clientY)}px")
          }
      },
      onPointerUp --> { ev =>
        sheetOf(ev).foreach(_.classList.remove("sheet-dragging"))
        val next =
          if dragged then Sheet.nearest((h - ev.clientY) / h, px("--sheet-peek") / h, 1 - px("--sheet-full-gap") / h)
          else Sheet.next(UiState.sheet.now())
        UiState.set(UiState.sheet := next)()
      }
    )

  /** The ONE title quick-filter, above the tab strip: it narrows every tab (sources by title,
    * articles by title, events by label) through [[UiState.title]]. */
  private def titleFilter: HtmlElement =
    div(
      cls := "title-filter",
      input(
        typ := "search",
        cls := "field",
        idAttr := "f-title",
        placeholder := "filter by title…",
        aria.label := "filter by title",
        controlled(
          value <-- UiState.title.signal.map(_.getOrElse("")),
          onInput.mapToValue --> { v => UiState.set(UiState.title := Some(v).filter(_.trim.nonEmpty))() }
        )
      )
    )

  private def loadTagline(): Unit =
    ApiClient.sources(Filter(limit = 1)).foreach { r =>
      Option(dom.document.getElementById("tagline")).foreach { el =>
        el.textContent = s"${Plural.count(r.total, "source")} · search, then read"
      }
    }

  private def paramsKey(): String =
    val f = UiState.toFilter()
    js.JSON.stringify(js.Dynamic.literal(
      q = f.q.orNull,
      title = f.title.orNull,
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

  private def nonTimeParamsKey(): String =
    val f = UiState.toFilter()
    js.JSON.stringify(js.Dynamic.literal(
      q = f.q.orNull,
      title = f.title.orNull,
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

  private def quickRender(): Unit =
    val a = UiState.from.now().getOrElse(config.time.defaultFrom)
    val b = UiState.to.now().getOrElse(config.time.defaultTo)
    quickRenderRange(a, b)

  private def quickRenderRange(a: Long, b: Long): Unit =
    if cachedFindings.nonEmpty then
      val filtered = cachedFindings.filter { pt =>
        pt.periodStart.forall(_ <= b) && pt.periodEnd.forall(_ >= a)
      }
      MapView.render(filtered, (a, b))

  def quickRenderIfCached(from: Long, to: Long): Unit =
    quickRenderRange(from, to)

  private def schedule(): Unit =
    val key = paramsKey()
    if lastParams.contains(key) then ()
    else
      val ntk = nonTimeParamsKey()
      if ntk == cachedNonTimeKey && cachedFindings.nonEmpty then
        quickRender()
        lastParams = Some(key)
        dom.window.clearTimeout(timer)
        timer = dom.window.setTimeout(() => load(), config.debounce.reloadMs * 2)
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
        cachedFindings = points
        cachedNonTimeKey = nonTimeParamsKey()

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
