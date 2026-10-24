package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.map.*
import org.scalajs.dom

/** The base map (docs/user-guide/exploring/web-ui.md, "The base map"; docs/research-map-styles.md):
  *
  *  - **Readable** and **Physical**, the two vector styles drawn from this server's own extracts
  *    (`GET /api/basemap/{layer}/{z}/{x}/{y}`) — today's landscape, with a notice saying so;
  *  - the **online** raster maps (OpenStreetMap's tiles: Standard, Muted, Dark), unless the page is
  *    `local` (`ui.base-map-source`): then it asks no other host for anything.
  *
  * One menu chooses among them; a small options panel (names on or off, their language, contours in
  * Physical, the sea's depth) lies over the vector styles, per viewer ([[UiState.mapOptions]]). The map surface draws
  * the choice ([[BaseMapSpec]]); without a GPU ([[Capabilities.software]]) the vector styles are drawn
  * without their relief — the cheapest drawing — and a line of the notice says so. */
object BaseMaps:

  /** What the server reports (`GET /api/basemap`); `None` until asked. */
  val status: Var[Option[BasemapStatus]] = Var(None)

  /** Whether the page has only the local maps (`ui.base-map-source` resolved to `local`). */
  val localOnly: Var[Boolean] = Var(false)

  /** The base map drawn — the choice as [[effective]] maps it: what the names' tone follows. */
  val shown: Var[BaseMap] = Var(BaseMap.Standard)

  /** The browser draws its WebGL in software: the cheapest drawing of every map. */
  val software: Var[Boolean] = Var(false)

  /** The map's view, once it is still (its zoom and region): what the contours' line follows. */
  val view: Var[Option[(Double, GeoBounds)]] = Var(None)

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The local style an extract draws best: Readable when it can draw it whole, else Physical. Pure. */
  def defaultLocal(s: Option[BasemapStatus]): BaseMap =
    if s.exists(st => st.available && st.missing(BaseStyles.Readable.id).isEmpty) || s.isEmpty then BaseMap.Readable
    else BaseMap.Physical

  /** The base map drawn for the kept choice `b`: on a `local` page an online choice is the default local
    * style, so a browser's earlier choice never reaches another host. Pure. */
  def effective(b: BaseMap, local: Boolean, s: Option[BasemapStatus]): BaseMap =
    if local && b.online then defaultLocal(s) else b

  /** The choices the menu offers: the two styles, and the online maps unless the page is `local`. Pure. */
  def choices(local: Boolean): List[BaseMap] = if local then BaseMap.vectors else BaseMap.values.toList

  /** A choice's label: a vector style says when the data is missing, or when the download left out
    * layers it draws. Pure. */
  def optionLabel(b: BaseMap, s: Option[BasemapStatus]): String =
    if b.online then b.label
    else if !s.forall(_.available) then s"${b.label} — not downloaded"
    else if s.exists(st => st.missing(b.wire).nonEmpty) then s"${b.label} — needs the full download"
    else b.label

  /** The notice over a vector style. */
  val PastNotice: String =
    "Today's terrain. Coastlines, rivers, lakes and vegetation were different in the past."

  /** The line added without a GPU. */
  val SoftwareNotice: String =
    "This browser draws maps without a GPU: the relief is left out to keep the map quick."

  /** The docs section the notice links to, under `ui.docs-url`. */
  def docsLink(docsUrl: String, page: String = "exploring/web-ui.md#the-base-map"): String =
    val base = if docsUrl.isEmpty || docsUrl.endsWith("/") then docsUrl else docsUrl + "/"
    s"$base$page"

  /** The docs section on downloading the full extract. */
  val OfflinePage = "how-to/offline.md#the-one-time-download"

  /** The notice when the extracts are not on the server: what to run. */
  def missingNotice(s: Option[BasemapStatus]): String =
    val cmd = s.map(_.fetchCommand).getOrElse(Basemap.FetchCommand)
    s"This map is not downloaded on this server. Run `$cmd` on the machine that runs it (a few hundred MB to a few GB), " +
      "then choose it again."

  /** The notice over a style drawn without some of its layers: which, and what to run. Pure. */
  def lackingNotice(missing: List[String]): String =
    s"This map also draws ${missing.mkString(", ")}, which the download on this server left out. Run " +
      s"`${Basemap.KeepAllCommand}` on the machine that runs it, then choose the map again."

  /** Pure: the credits line — each source's credit once, in order, separated by a middle dot. */
  def creditsHtml(credits: Seq[String]): String = credits.map(_.trim).filter(_.nonEmpty).distinct.mkString(" · ")

  /** How far the notice's corner rises so it clears the right-hand corner's controls: their height and a
    * gap when the two would overlap side by side, else nothing. Pure. */
  def noticeLift(noticeLeft: Double, noticeRight: Double, creditsLeft: Double, creditsRight: Double, creditsHeight: Double): Double =
    if noticeRight > creditsLeft && creditsRight > noticeLeft then creditsHeight + 4 else 0

  /** The languages offered for the names: English, each place's own, and those both the map's data and
    * Natural Earth's names carry. */
  val Languages: List[(String, String)] = List("en" -> "English", "local" -> "Local (own script)", "de" -> "Deutsch",
    "fr" -> "Français", "it" -> "Italiano", "es" -> "Español", "el" -> "Ελληνικά", "tr" -> "Türkçe", "ru" -> "Русский",
    "ar" -> "العربية", "he" -> "עברית", "fa" -> "فارسی", "zh-Hans" -> "中文")

  /** The online raster map of `b` (`ui.tiles` for Standard and Dark). Pure. */
  def raster(b: BaseMap, ui: UiConfig): BaseMapSpec.Raster =
    val osm = "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors (ODbL)"
    val url = b.url.getOrElse(ui.tiles)
    val credit = if url.contains("openstreetmap.fr/hot") then s"$osm · tiles © Humanitarian OSM Team, OSM France" else osm
    BaseMapSpec.Raster(url, ui.tilesMaxZoom, credit, dark = BaseMap.darkened(b, MapTone.parse(ui.tilesTone)))

  /** What is drawn for base map `b` (already [[effective]]), the server's status and the viewer's
    * options: the spec, the notice, and how deep the map zooms. Pure. */
  def plan(b: BaseMap, s: Option[BasemapStatus], prefs: MapPrefs, ui: UiConfig, local: Boolean, soft: Boolean)
      : (BaseMapSpec, Option[(String, Boolean)], Int) =
    b.vector match
      case None => (raster(b, ui), None, ui.tilesMaxZoom)
      case Some(v) =>
        s.filter(_.available) match
          case Some(st) =>
            val lang = prefs.language.getOrElse(ui.mapLanguage)
            val spec = BaseMapSpec.Vector(v, st, lang, prefs.names.getOrElse(true), prefs.contours.getOrElse(true),
              prefs.seaDepth(v), reduced = soft)
            val lacking = st.missing(b.wire)
            val notice = if lacking.nonEmpty then (lackingNotice(lacking), true) else (PastNotice, false)
            (spec, Some(notice), st.mapMaxZoom)
          case None =>
            // not downloaded: the online Standard stands in (none on a local page), and the notice says what to run
            (if local then BaseMapSpec.Blank else raster(BaseMap.Standard, ui), Some((missingNotice(s), true)),
              if local then ui.tilesMaxZoom else ui.tilesMaxZoom)

  // ── on the map ──────────────────────────────────────────────────────────────────────────────────

  /** The base map drawn now (a small map elsewhere on the page draws the same). */
  // `var`: set with every change of the base map
  var current: BaseMapSpec = BaseMapSpec.Blank

  // `var`s: the page's settings and its map, once installed
  private var ui: UiConfig = null
  private var surface: MapSurface = null
  private var noticeEl: Option[dom.HTMLElement] = None

  /** Before the map is made: the page's `ui.base-map-source`. */
  def configure(config: UiConfig): Unit =
    ui = config
    localOnly.set(config.baseMapSource == BaseMapSource.Local)

  /** Ask the server again (on start, and when a vector style is chosen). */
  def refresh(): Unit =
    ApiClient.basemap().foreach(s => status.set(Some(s)))(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  def install(m: MapSurface, config: UiConfig)(using Owner): Unit =
    surface = m
    ui = config
    software.set(m.capabilities.software)
    view.set(Some((m.zoom, m.bounds)))
    val _ = m.on(MapEventKind.MoveEnd)(_ => view.set(Some((m.zoom, m.bounds))))
    // a base map drawn: its credits are known (they follow its sources and the layers drawn)
    val _ = m.on(MapEventKind.BaseMapReady)(_ => if m.credits != drawnCredits then drawFoot())
    refresh()
    // a page with only the local maps, never told otherwise by this browser: its best local style
    status.signal.foreach { s =>
      if localOnly.now() && !UiState.stored(UiState.baseMap) && UiState.baseMap.now().online then
        UiState.set(UiState.baseMap := defaultLocal(s))()
    }
    UiState.baseMap.signal.combineWith(status.signal, UiState.mapOptions.signal).foreach { (b, s, prefs) =>
      val eb = effective(b, localOnly.now(), s)
      shown.set(eb)
      val (spec, notice, maxZoom) = plan(eb, s, prefs, config, localOnly.now(), software.now())
      m.setMaxZoom(maxZoom.toDouble)
      m.setBaseMap(spec)
      current = spec
      setNotice(notice.map((t, warn) => (if software.now() && eb.vector.isDefined then s"$t $SoftwareNotice" else t, warn)),
        lacking = notice.exists(_._1.startsWith("This map also")))
      m.container.classList.toggle("basemap-vector", eb.vector.isDefined && spec.isInstanceOf[BaseMapSpec.Vector])
    }

  /** The map's foot, bottom left (#3759): the notice over a vector style (or what to run), and under it the
    * base map's credits — one box, where MapLibre's ⓘ button and its folded credits used to sit in the
    * right-hand corner under the legend. Without a notice (an online map) the box holds the credits alone;
    * without either it is gone. */
  private def setNotice(n: Option[(String, Boolean)], lacking: Boolean): Unit =
    notice = n.map((t, w) => (t, w, lacking))
    drawFoot()

  // `var`s: the notice drawn now (its text, whether it warns, whether it is the lacking-layers one), and the
  // credits drawn with it
  private var notice: Option[(String, Boolean, Boolean)] = None
  private var drawnCredits: Seq[String] = Nil

  private def drawFoot(): Unit =
    noticeEl.foreach(_.remove())
    noticeEl = None
    val credits = surface.credits
    drawnCredits = credits
    if notice.nonEmpty || credits.nonEmpty then
      val box = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
      box.className = if notice.exists(_._2) then "map-foot is-missing hd-map-control" else "map-foot hd-map-control"
      notice.foreach { (text, warn, lacking) =>
        val el = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        el.className = "terrain-notice"
        el.setAttribute("role", "note")
        val p = dom.document.createElement("span")
        p.textContent = text
        val a = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
        a.href = if lacking then docsLink(ui.docsUrl, OfflinePage) else if warn then docsLink(ui.docsUrl, "how-to/offline.md") else docsLink(ui.docsUrl)
        a.target = "_blank"
        a.rel = "noopener"
        a.textContent = if warn then "How to download it" else "Why?"
        el.appendChild(p)
        el.appendChild(dom.document.createTextNode(" "))
        el.appendChild(a)
        box.appendChild(el)
      }
      if credits.nonEmpty then
        val c = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        c.className = "map-credits"
        // the sources' own credit lines (their links included), as the map library showed them
        c.innerHTML = creditsHtml(credits)
        box.appendChild(c)
      surface.corner(Corner.BottomLeft).appendChild(box)
      noticeEl = Some(box)
      dom.window.requestAnimationFrame((_: Double) => fitNotice())
    else surface.container.style.setProperty("--terrain-notice-lift", "0px")

  // the notice's watcher of the right-hand corner (the credits, the legend), made once
  private lazy val cornerWatch: Unit =
    val ro = scala.scalajs.js.Dynamic.global.ResizeObserver
    if !scala.scalajs.js.isUndefined(ro) then
      val o = scala.scalajs.js.Dynamic.newInstance(ro)(((_: scala.scalajs.js.Any) => fitNotice()): scala.scalajs.js.Function1[scala.scalajs.js.Any, Unit])
      o.observe(surface.corner(Corner.BottomRight))
      o.observe(surface.container)

  /** The notice shares the map's foot with the right-hand corner (the credits, the legend and its key):
    * where they would meet, the notice's corner rises above that corner. */
  private def fitNotice(): Unit =
    cornerWatch
    noticeEl.foreach { n =>
      val boxes = surface.corner(Corner.BottomRight).children.toList.map(_.getBoundingClientRect()).filter(r => r.width > 0 && r.height > 0)
      val nr = n.getBoundingClientRect()
      val lift = if boxes.isEmpty then 0.0
        else noticeLift(nr.left, nr.right, boxes.map(_.left).min, boxes.map(_.right).max, boxes.map(_.bottom).max - boxes.map(_.top).min)
      surface.container.style.setProperty("--terrain-notice-lift", s"${lift}px")
    }

  // ── the controls ────────────────────────────────────────────────────────────────────────────────

  private val open: Var[Boolean] = Var(false)

  private val onVector: Signal[Boolean] = shown.signal.map(_.vector.isDefined)

  private def baseSelect: HtmlElement =
    select(
      cls := "field map-base-select",
      aria.label := "base map",
      title := "Readable and Physical are drawn from this server's own data (today's landscape); the online maps are OpenStreetMap's tiles",
      children <-- localOnly.signal.combineWith(status.signal).map((l, s) =>
        choices(l).map(b => option(value := b.wire, optionLabel(b, s),
          title := (if b.online then "Tiles from the internet" else s.map(_.missing(b.wire)).filter(_.nonEmpty).fold(PastNotice)(lackingNotice))))),
      controlled(
        value <-- shown.signal.map(_.wire).composeChanges(_.delay(0)),
        onChange.mapToValue --> { v =>
          BaseMap.fromWire(v).foreach { b =>
            if b.vector.isDefined then refresh()
            UiState.set(UiState.baseMap := b)()
          }
        }
      )
    )

  /** The button that opens the options; disabled on an online map. */
  private def optionsButton: HtmlElement =
    button(
      typ := "button",
      idAttr := "map-options-button",
      cls := "btn btn--toggle btn--icon map-options-btn",
      aria.label := "map options",
      htmlAttr("aria-haspopup", com.raquo.laminar.codecs.StringAsIsCodec) := "dialog",
      aria.controls := "map-options",
      aria.expanded <-- open.signal,
      disabled <-- onVector.map(!_),
      title <-- onVector.map(b => if b then "Map options: names, their language, contours, the sea's depth" else "Map options — for Readable and Physical"),
      Icon(Icon.MapContours),
      onClick --> { _ => open.update(!_) }
    )

  /** The names' language unless the viewer chose one: `basemap.labels.language`, as the server reports it. */
  private def defaultLanguage: String = if ui == null || ui.mapLanguage.isEmpty then "en" else ui.mapLanguage

  /** The languages the menu offers: [[Languages]], and the configured default when it is not among them. Pure. */
  def languageChoices(default: String): List[(String, String)] =
    if Languages.exists(_._1 == default) then Languages else Languages :+ (default -> default)

  private def label_(mods: Modifier[HtmlElement]*): HtmlElement = htmlTag("label")(mods*)

  private def setPrefs(f: MapPrefs => MapPrefs): Unit = UiState.set(UiState.mapOptions := f(UiState.mapOptions.now()))()

  /** The relief as the server has it, when it can be drawn. */
  private def relief(s: Option[BasemapStatus]): Option[BasemapLayerInfo] =
    s.flatMap(_.layers.find(l => l.layer == BasemapLayer.Terrain.wire && l.present && l.error.isEmpty))

  /** Relief deeper than the extract: a focus region downloaded, or the on-demand relief on. Pure. */
  def deeperRelief(i: BasemapLayerInfo): Boolean = i.focus.nonEmpty || i.onDemand.exists(_.enabled)

  /** The app zoom from which Physical draws its contours (its style's `contours` layer: MapLibre zoom 10). */
  val ContoursFromZoom: Double = 11.0

  /** Why the contours cannot be drawn anywhere, if they cannot: only Physical draws them, with a GPU, from
    * relief deeper than the extract — a focus region (`basemap fetch --layer terrain --focus`) or the
    * on-demand relief (`basemap.terrain.on-demand`). Pure. */
  def contoursWhy(b: BaseMap, s: Option[BasemapStatus], soft: Boolean): Option[String] =
    if b != BaseMap.Physical then Some("Physical draws contours")
    else if soft then Some("not drawn without a GPU")
    else relief(s) match
      case None => Some(s"no relief downloaded — `${Basemap.FetchCommand} --layer terrain`")
      case Some(i) if !deeperRelief(i) =>
        Some(s"no detailed relief: no focus region downloaded and on-demand relief is off — " +
          s"`${Basemap.FetchCommand} --layer terrain --focus <region>`")
      case _ => None

  /** Do a region of the view and a focus region's box meet? Pure. */
  def meets(v: GeoBounds, b: BBox): Boolean =
    v.south <= b.maxLat && v.north >= b.minLat && v.west <= b.maxLon && v.east >= b.minLon

  /** Why the contours, possible on this map, are not drawn in this view (zoom `z`, region `v`): zoomed out
    * too far, or outside every focus region with the on-demand relief off (the contours there follow the
    * extract's coarse relief). `None`: they are drawn. Pure. */
  def contoursNote(s: Option[BasemapStatus], z: Double, v: GeoBounds): Option[String] =
    relief(s).flatMap { i =>
      val inFocus = i.focus.exists(f => BBox.parse(f.bbox).exists(meets(v, _)))
      if z < ContoursFromZoom then Some(f"zoom in to see contours (from zoom ${ContoursFromZoom.toInt}%d)")
      else if !inFocus && !i.onDemand.exists(_.enabled) then
        Some(s"outside the detailed relief (${i.focus.map(_.name).mkString(", ")}) and on-demand relief is off: " +
          "contours here follow the coarse relief")
      else None
    }

  /** Why the sea cannot be coloured by its depth, if it cannot: with a GPU, from the sea floor
    * (`basemap fetch --layer bathymetry`). Pure. */
  def seaDepthWhy(b: BaseMap, s: Option[BasemapStatus], soft: Boolean): Option[String] =
    if b.vector.isEmpty then Some("Readable and Physical draw the sea's depth")
    else if soft then Some("not drawn without a GPU")
    else if !s.exists(_.layers.exists(l => l.layer == BasemapLayer.Bathymetry.wire && l.present && l.error.isEmpty)) then
      Some(s"no sea floor downloaded — `${Basemap.FetchCommand} --layer bathymetry`")
    else None

  private def panel: HtmlElement =
    val why = shown.signal.combineWith(status.signal, software.signal).map(contoursWhy.tupled)
    val note = why.combineWith(status.signal, view.signal).map { (w, s, v) =>
      if w.isDefined then None else v.flatMap((z, b) => contoursNote(s, z, b))
    }
    val depthWhy = shown.signal.combineWith(status.signal, software.signal).map(seaDepthWhy.tupled)
    div(
      idAttr := "map-options",
      cls := "map-options",
      role := "dialog",
      aria.label := "map options",
      cls("is-hidden") <-- open.signal.combineWith(onVector).map((o, v) => !o || !v),
      div(cls := "map-option",
        label_(
          input(typ := "checkbox", cls := "map-option-names",
            checked <-- UiState.mapOptions.signal.map(_.names.getOrElse(true)),
            onInput.mapToChecked --> (on => setPrefs(_.copy(names = Option.when(!on)(false))))),
          span("Names"))),
      div(cls := "map-option map-option-row",
        label_(span("Language"),
          select(cls := "field map-language-select", aria.label := "names' language",
            disabled <-- UiState.mapOptions.signal.map(_.names.contains(false)),
            languageChoices(defaultLanguage).map((code, name) => option(value := code, name)),
            controlled(
              value <-- UiState.mapOptions.signal.map(_.language.getOrElse(defaultLanguage)),
              onChange.mapToValue --> (v => setPrefs(_.copy(language = Option.when(v != defaultLanguage)(v)))))))),
      div(cls := "map-option",
        cls("is-disabled") <-- why.map(_.isDefined),
        label_(
          input(typ := "checkbox", cls := "map-option-contours", disabled <-- why.map(_.isDefined),
            checked <-- UiState.mapOptions.signal.combineWith(why).map((p, w) => w.isEmpty && p.contours.getOrElse(true)),
            onInput.mapToChecked --> (on => setPrefs(_.copy(contours = Option.when(!on)(false))))),
          span("Contour lines")),
        child.maybe <-- why.combineWith(note).map((w, n) => w.orElse(n).map(t => div(cls := "map-option-why map-option-contours-why", t)))),
      div(cls := "map-option",
        cls("is-disabled") <-- depthWhy.map(_.isDefined),
        label_(
          input(typ := "checkbox", cls := "map-option-depth", disabled <-- depthWhy.map(_.isDefined),
            checked <-- UiState.mapOptions.signal.combineWith(shown.signal, depthWhy).map((p, b, w) =>
              w.isEmpty && b.vector.exists(p.seaDepth)),
            onInput.mapToChecked --> (on => shown.now().vector.foreach(v => setPrefs(_.withSeaDepth(v, on))))),
          span("Sea depth")),
        child.maybe <-- depthWhy.map(_.map(w => div(cls := "map-option-why map-option-depth-why", w)))),
      div(cls := "map-option map-option-row",
        button(typ := "button", cls := "btn btn--ghost btn--sm map-options-reset", "Reset",
          disabled <-- UiState.mapOptions.signal.map(_ == MapPrefs()),
          onClick --> { _ => setPrefs(_ => MapPrefs()) }),
        button(typ := "button", cls := "btn btn--ghost btn--sm map-options-close", "Close", onClick --> { _ => close() }))
    )

  /** Where the panel goes: under its button, its right edge at the button's — moved left or right to stay
    * inside the window. Pure. */
  def placement(buttonLeft: Double, buttonRight: Double, buttonBottom: Double, width: Double, windowWidth: Double): (Double, Double) =
    val x = math.max(8.0, math.min(windowWidth - width - 8, buttonRight - width))
    (if buttonLeft < 0 then 8.0 else x, buttonBottom + 4)

  private def place(): Unit =
    for
      b <- Option(dom.document.getElementById("map-options-button"))
      p <- Option(dom.document.getElementById("map-options")).map(_.asInstanceOf[dom.HTMLElement])
    do
      val r = b.getBoundingClientRect()
      val (x, y) = placement(r.left, r.right, r.bottom, math.min(300.0, dom.window.innerWidth * 0.92), dom.window.innerWidth)
      p.style.setProperty("--map-options-x", s"${x}px")
      p.style.setProperty("--map-options-y", s"${y}px")

  private def close(): Unit =
    open.set(false)
    Option(dom.document.getElementById("map-options-button")).foreach(_.asInstanceOf[dom.HTMLElement].focus())

  /** The base-map controls of the map's toolbar: the menu and the options. */
  def controls: HtmlElement =
    div(
      cls := "btn-group map-base-group",
      role := "group",
      aria.label := "base map",
      baseSelect,
      div(cls := "map-options-anchor", optionsButton, panel),
      onVector --> { v => if !v then open.set(false) },
      documentEvents(_.onKeyDown) --> { e => if e.key == "Escape" && open.now() then close() },
      documentEvents(_.onPointerDown) --> { e =>
        val t = e.target.asInstanceOf[dom.Element]
        if open.now() && t != null && t.closest(".map-options-anchor") == null then open.set(false)
      },
      open.signal.changes.filter(identity) --> { _ =>
        place()
        dom.window.setTimeout(() => Option(dom.document.querySelector("#map-options input")).foreach(_.asInstanceOf[dom.HTMLElement].focus()), 0)
      },
      windowEvents(_.onResize) --> { _ => if open.now() then place() }
    )
