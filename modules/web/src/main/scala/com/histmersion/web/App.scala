package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, Plural}
import com.histmersion.web.library
import com.histmersion.web.ui.SidebarTab
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.library.{Filter, GeoHit}

object App:

  /** Drops the answers to a superseded load ([[Latest]]). */
  private val latest = Latest()
  private var timer: Int = 0
  private var totalKey: Option[String] = None
  private var totalCount: Int = 0
  private var lastParams: Option[String] = None
  private var config: UiConfig = uninitialized
  private var cachedFindings: List[GeoHit] = Nil
  private var cachedNonTimeKey: String = ""

  /** How many findings are kept for time-only redraws ([[Diagnostics]]). */
  def keptFindings: Int = cachedFindings.length

  def init(): Unit =
    dom.console.log("Histmersion: init")
    // ONE request: the failure and the success read the same answer (it was asked for twice)
    val configF = ApiClient.config()
    configF.failed.foreach { e =>
      dom.console.error("Histmersion: config failed", e.getMessage)
      Option(dom.document.getElementById("app")).foreach(_.textContent = s"Failed to load config: ${e.getMessage}")
    }
    Theme.install()
    configF.foreach { ui =>
      config = ui
      UiState.init(ui)
      Viewport.init(ui)
      MobileChrome.install()
      Auth.init(ui.writeKeyRequired)
      RunsView.enabled.set(ui.agentsEnabled)
      Notices.install(ui)
      MergeView.configure(ui)
      MapRegionsView.configure(ui)

      com.histmersion.web.settings.DownloadJobs.configure(ui.jobPollMs)

      Router.init()
      marks.MyItems.load()
      // a first run (no setup done, a settings file of nothing): the setup first, wherever the address points
      if ui.setupDue && Router.now() != Route.Setup && !com.histmersion.web.setup.SetupPage.left then
        Router.go(Route.Setup, push = false)
      val appEl = div(
        cls := "histmersion-app",
        cls("is-setup") <-- Router.route.map(_ == Route.Setup),
        MobileChrome.rootClasses,
        // which of the four places is showing: the narrow layout moves the search for Explore's own
        dataAttr("showing") <-- Router.route.map(_.place.toString.toLowerCase),
        // the dragged width is data; the grid that uses it is the stylesheet's
        styleAttr <-- ResultsPanel.width.signal.map(w => s"--sidebar-w:${w}px"),
        // the filter panel lies over the map: folded or open, the map keeps its size; its corners' controls
        // move clear of it (the stylesheet's `--filters-w`)
        cls("filters-folded") <-- FilterBar.folded.signal,
        cls("on-library-list") <-- Router.route.map(r => filtersShown(r) && r != Route.Explore),
        TopBar.element(ui),
        // Explore stays laid out under the other pages, hidden and out of the keyboard's way, so the map
        // is neither resized nor reloaded by a visit to Library or Research
        div(
          cls := "explore-place",
          cls("is-away") <-- Router.route.map(_ != Route.Explore),
          inert <-- Router.route.map(_ != Route.Explore),
          MobileChrome.bar(ui),
          MapView.init(ui),
          PlacesInput.mapChips,
          ResultsPanel.element(ui),
          TimelineView.init(ui),
          ViewportToggle.init(ui),
          MobileChrome.edges
        ),
        // the filter panel is Explore's and Library's lists' (one filter state): it lies over the map, or
        // beside the lists; elsewhere it is hidden and out of the keyboard's way like Explore
        FilterBar
          .init(ui, library.MyLibrary.panelTop, List(library.MyLibrary.panelFoot, marks.SavedViews.panelSection))
          .amend(
            cls("is-away") <-- Router.route.map(r => !filtersShown(r)),
            inert <-- Router.route.map(r => !filtersShown(r))
          ),
        MobileChrome.scrim,
        Pages.element(ui),
        ArticleModal.element,
        marks.NoteEditor.element,
        com.histmersion.web.ui.ContextMenu.element,
        com.histmersion.web.ui.Flash.element,
        statusElement
      )

      render(dom.document.getElementById("app"), appEl)
      Preview.install()
      Diagnostics.install()

      TreeTab.init(ui)
      ArticleModal.restore()
      WindowManager.installKeys()
      TopBar.installShortcut()
      // every tab's count is shown: the lists load at once, and each refetches only when its query changed
      ArticlesTab.load()
      EventsTab.load()
      TreeTab.show(SidebarTab.Sources.key)
      MapLists.refresh(ui)
      MapLists.follow(ui)

      loadTagline()

      // the state the first load is for, BEFORE subscribing: the subscription answers at once with the
      // current state, which scheduled a second identical load (300 KB of findings, the list drawn twice)
      lastParams = Some(paramsKey())
      UiState.signal.foreach { _ =>
        schedule()
        ArticlesTab.refresh()
        EventsTab.refresh()
      }(using unsafeWindowOwner)

      load()
    }

  /** The pages the filter panel is shown on: Explore and Library's two lists. */
  private def filtersShown(r: Route): Boolean = r match
    case Route.Explore    => true
    case Route.Library(s) => s.isList
    case _                => false

  /** The `inert` attribute: the subtree takes no focus, clicks or find-in-page. */
  private val inert = htmlAttr("inert", com.raquo.laminar.codecs.BooleanAsAttrPresenceCodec)

  private val statusVar = Var("")
  private def statusElement: HtmlElement =
    span(idAttr := "status", child.text <-- statusVar.signal)

  private def loadTagline(): Unit =
    ApiClient.sources(Filter(limit = 1)).foreach { r =>
      Option(dom.document.getElementById("tagline")).foreach { el =>
        el.textContent = s"${Plural.count(r.total, "source")} · search, then read"
      }
    }

  private def paramsKey(): String =
    val f = UiState.toFilter()
    js.JSON.stringify(
      js.Dynamic.literal(
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
      )
    )

  private def nonTimeParamsKey(): String =
    val f = UiState.toFilter()
    js.JSON.stringify(
      js.Dynamic.literal(
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
      )
    )

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

  /** The map drawn for the span `[from, to]` again, from the findings held — after playback, which drew its frames'
    * windows: the findings, the legend's years, the tiled layers and the boundaries all of the whole span, as before it
    * played (with no findings held too: the layers still follow the span).
    */
  def restoreSpan(from: Long, to: Long): Unit =
    MapView.render(
      cachedFindings.filter(pt => pt.periodStart.forall(_ <= to) && pt.periodEnd.forall(_ >= from)),
      (from, to)
    )

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
        timer = dom.window.setTimeout(() => load(), UiState.reloadDelay(config.debounce.reloadMs))

  private def load(): Unit =
    val mine = latest.start()

    val f = UiState.toFilter()
    val fNoBox = UiState.toFilter(except = Set("bbox"))
    val inView = UiState.inView.now()
    val a = UiState.from.now().getOrElse(config.time.defaultFrom)
    val b = UiState.to.now().getOrElse(config.time.defaultTo)

    statusVar.set("loading…")
    ResultsList.busy(true)

    // ALL AT ONCE: the map feed, the page of sources and (in view) the feed without the viewport.
    // The sources used to be asked for only once the findings had arrived — a whole round trip and
    // the findings' query added to every list refresh; the list still renders once all are in.
    val sourceFilter = f.copy(limit = config.pageSize)
    val pointsF = ApiClient.findings(Api.FindingsQuery.of(f))
    val sourcesF = ApiClient.sources(sourceFilter)
    val totalF =
      if inView then ApiClient.findings(Api.FindingsQuery.of(fNoBox)).map(_.length)
      else pointsF.map(_.length)

    pointsF.foreach { points =>
      if latest.isCurrent(mine) then
        cachedFindings = points
        cachedNonTimeKey = nonTimeParamsKey()
        MapView.render(points, (a, b))
    }

    // a failed load ends the "loading…" state with what went wrong, instead of leaving it there
    (for points <- pointsF; result <- sourcesF; total <- totalF yield (points, result, total)).onComplete { r =>
      if latest.isCurrent(mine) then
        latest.answer(
          r,
          { (points, result, total) =>
            totalCount = total
            ResultsList.renderSources(
              result,
              ResultsList.SourceRenderOpts(
                findings = points.length,
                total = totalCount,
                inView = inView,
                params = Some(sourceFilter)
              )
            )
            statusVar.set("")
          },
          m => { statusVar.set(""); ResultsList.failed(s"Could not list sources: $m") }
        )
    }
