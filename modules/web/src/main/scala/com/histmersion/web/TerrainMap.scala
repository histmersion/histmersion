package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import com.histmersion.web.ui.ThemeTokens
import com.raquo.laminar.api.L.Var
import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

/** The local base map ([[BaseMap.Local]], docs/research-terrain-basemap.md, its "Local street maps" section),
  * drawn in the page from the extracts the server holds (`GET /api/basemap/{layer}/{z}/{x}/{y}`) with
  * one of the named styles it serves (`GET /api/basemap/styles`, [[TerrainRules]]) — the terrain styles
  * (MODERN terrain only) and the street maps (`modern`: Standard, Muted, Dark):
  *
  *  - the coast, water, rivers and land cover — and for a street map the roads, borders and land use —
  *    vector tiles, drawn on canvas by protomaps-leaflet (loaded on first use) with the style's fills
  *    and lines;
  *  - the relief — elevation tiles shaded here ([[Hillshade]]) into a translucent layer above them;
  *  - the physical names — seas, ranges, deserts, lakes, rivers — the style's labels, in a layer of their
  *    own above the relief and below every marker of the map.
  *
  * While it is shown, the page asks every `checkSeconds` whether the styles changed (the server reads
  * the style file again when it changes) and draws the map again when they did.
  *
  * A terrain style always with a notice that this is TODAY's landscape. When the server has not
  * downloaded the extract, the standard tiles stay, dimmed (none when the page is `local`: then only
  * the style's background), and the notice says how to fetch it; a style whose layers the download
  * left out is drawn with what is there and a notice says what to run. */
object TerrainMap:

  /** What the server reports (`GET /api/basemap`); `None` until asked. */
  val status: Var[Option[BasemapStatus]] = Var(None)

  /** The named styles the server serves; `None` until asked. */
  val styles: Var[Option[TerrainStyles]] = Var(None)

  /** The tone of the style shown: the map's own names follow it ([[MapLabels]]). */
  val tone: Var[MapTone] = Var(MapTone.Dark)

  /** Whether the page has only the local maps (`ui.base-map-source` resolved to `local`, set once by
    * [[MapView.init]]): any base-map control reads it — no online map is offered, none is drawn. */
  val localOnly: Var[Boolean] = Var(false)

  /** The base map drawn — the choice as [[effective]] maps it: what the names' tone and the names
    * toggle follow. */
  val shown: Var[BaseMap] = Var(BaseMap.Standard)

  /** The base map drawn for the kept choice `b` (and style): when the page is `local` an online map is
    * the local style of the same name (`standard`, `muted`, `dark` — [[TerrainStyles.pick]] falls back
    * to the default when there is none), so a browser's earlier choice never reaches another host. Pure. */
  def effective(b: BaseMap, style: Option[String], local: Boolean): (BaseMap, Option[String]) =
    if !local || b == BaseMap.Local then (b, style) else (BaseMap.Local, Some(b.wire))

  /** The base maps the menu offers besides the local styles: the online ones unless the page is
    * `local`. Pure. */
  def onlineMaps(local: Boolean): List[BaseMap] = if local then Nil else BaseMap.values.toList.filter(_ != BaseMap.Local)

  /** Ask the server again (on start, and each time the terrain map is chosen). */
  def refresh(): Future[BasemapStatus] =
    val f = ApiClient.basemap()
    f.foreach(s => status.set(Some(s)))
    refreshStyles()
    f

  def refreshStyles(): Future[TerrainStyles] =
    val f = ApiClient.basemapStyles()
    f.foreach(s => if !styles.now().exists(_.version == s.version) then styles.set(Some(s)))
    f

  /** The base-map menu's value for a terrain style. */
  def optionValue(id: String): String = s"${BaseMap.Local.wire}:$id"

  /** A menu value → the base map and, for the terrain map, the style it names. Pure. */
  def parseOption(v: String): Option[(BaseMap, Option[String])] =
    v.split(":", 2).toList match
      case b :: id :: Nil => BaseMap.fromWire(b).filter(_ == BaseMap.Local).map(_ -> Some(id).filter(_.nonEmpty))
      case b :: Nil       => BaseMap.fromWire(b).map(_ -> None)
      case _              => None

  /** The menu's value for the map shown (`local`: an online choice as [[effective]] maps it). Pure. */
  def selectValue(b: BaseMap, style: Option[String], ss: Option[TerrainStyles], local: Boolean = false): String =
    val (eb, es) = effective(b, style, local)
    if eb != BaseMap.Local then eb.wire
    else ss.flatMap(_.pick(es)).fold(eb.wire)(s => optionValue(s.id))

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The notice over the terrain map. */
  val PastNotice: String =
    "Today's terrain. Coastlines, rivers, lakes and vegetation were different in the past."

  /** The docs section the notice links to, under `ui.docs-url`. */
  def docsLink(docsUrl: String, page: String = "exploring/web-ui.md#the-terrain-map"): String =
    val base = if docsUrl.isEmpty || docsUrl.endsWith("/") then docsUrl else docsUrl + "/"
    s"$base$page"

  /** The docs section on downloading the full extract (the street maps' layers). */
  val OfflinePage = "how-to/offline.md#the-one-time-download"

  /** The notice when the extracts are not on the server: what to run. */
  def missingNotice(s: Option[BasemapStatus], modern: Boolean = false): String =
    val cmd = s.map(_.fetchCommand).getOrElse(Basemap.FetchCommand) + (if modern then " --keep-all" else "")
    val what = if modern then "This map" else "The terrain map"
    s"$what is not downloaded on this server. Run `$cmd` on the machine that runs it (a few hundred MB to a few GB), " +
      "then choose it again."

  /** The base-map menu's label for a local style (`None`: before the styles are known): a terrain
    * style says it has no modern features; the label says when the data is missing, or when the
    * download left out layers the style draws. */
  def optionLabel(s: Option[BasemapStatus], style: Option[TerrainStyle] = None): String =
    val name = style.fold(BaseMap.Local.label)(t => if t.modern then t.name else s"${t.name} (no modern features)")
    if !s.forall(_.available) then s"$name — not downloaded"
    else if style.exists(t => s.exists(_.missing(t.id).nonEmpty)) then s"$name — needs the full download"
    else name

  /** The notice over a style drawn without some of its layers: which, and what to run. Pure. */
  def lackingNotice(missing: List[String]): String =
    s"This map also draws ${missing.mkString(", ")}, which the download on this server left out. Run " +
      s"`${Basemap.KeepAllCommand}` on the machine that runs it, then choose the map again."

  /** The sources the server has (`vector`, `labels`), as the style's layers name them. */
  def sources(s: BasemapStatus): Set[String] =
    TerrainStyles.Sources.toSet.filter(src => BasemapLayer.parse(src).exists(l => template(s, l).isDefined))

  /** A layer's tile URL template, when it is there. */
  def template(s: BasemapStatus, l: BasemapLayer): Option[String] =
    s.layers.find(i => i.layer == l.wire && i.present && i.error.isEmpty)
      .map(i => BasemapApi.tileTemplate(l, i.version.getOrElse("0")))

  /** Where the relief is asked for: anywhere when the server reads it on demand, else the extract's region
    * and the focus regions'. */
  def reliefBounds(i: BasemapLayerInfo): Option[js.Array[js.Array[Double]]] =
    if i.onDemand.exists(_.enabled) then None
    else
      val boxes = (i.bbox.toList ++ i.focus.map(_.bbox)).flatMap(BBox.parse)
      Option.when(boxes.nonEmpty)(js.Array(js.Array(boxes.map(_.minLat).min, boxes.map(_.minLon).min),
        js.Array(boxes.map(_.maxLat).max, boxes.map(_.maxLon).max)))

  /** The extract's region as Leaflet bounds `[[south, west], [north, east]]`. */
  def bounds(i: BasemapLayerInfo): Option[js.Array[js.Array[Double]]] =
    i.bbox.flatMap(BBox.parse).map(b => js.Array(js.Array(b.minLat, b.minLon), js.Array(b.maxLat, b.maxLon)))

  /** Every credit line of the layers shown, joined. */
  def attribution(s: BasemapStatus): String =
    s.layers.filter(i => i.present && i.error.isEmpty).map(_.attribution).filter(_.nonEmpty).distinct.mkString(" · ")

  // ── drawing ─────────────────────────────────────────────────────────────────────────────────────

  // `var`s: what this module put on the map, and which request is the latest (a quick switch away
  // and back must not leave two copies)
  private var drawn: List[Layer]          = Nil
  private var notice: js.UndefOr[js.Dynamic] = js.undefined
  private var generation                  = 0
  private var poll: Int                   = 0
  // what `enter` was called with, for a redraw when the styles change
  private var entered: Option[(LeafletMap, String, Boolean => Unit, Option[String])] = None

  private val ScriptSrc = "/web/vendor/protomaps-leaflet/protomaps-leaflet.js"
  private val FontsHref = "/web/vendor/noto-fonts/noto-fonts.css"

  /** The label fonts' stylesheet (served here: the map works offline), linked once, on first use. */
  private lazy val fontSheet: Unit =
    val l = dom.document.createElement("link").asInstanceOf[dom.HTMLLinkElement]
    l.rel = "stylesheet"
    l.href = FontsHref
    dom.document.head.appendChild(l)

  /** Text that needs every script a name may be written in, for `language` — so the font files a name
    * will use are loaded before it is drawn (a canvas never redraws when a font arrives late). Pure. */
  def fontSample(language: String): String =
    if language == "en" then "Aa\u00e9\u0151\u0141" else "Aa\u00e9\u0151\u0141\u0391\u03b1\u0410\u0430\u0531\u0561\u10d0\u05d0\u0627"

  /** Wait (at most `ms`) for the fonts the style's names use. */
  private def fontsReady(style: TerrainStyle, language: String, ms: Int = 3000): Future[Unit] =
    fontSheet
    val fonts = dom.document.asInstanceOf[js.Dynamic].fonts
    if js.isUndefined(fonts) then Future.unit
    else
      val loads = TerrainRules.fontsOf(style).map(f =>
        fonts.load(f, fontSample(language)).asInstanceOf[js.Promise[js.Any]].toFuture.map(_ => ()).recover { case _ => () })
      val p = Promise[Unit]()
      dom.window.setTimeout(() => { val _ = p.trySuccess(()) }, ms.toDouble)
      Future.sequence(loads).foreach(_ => p.trySuccess(()))
      p.future

  /** The renderer's script, loaded once, on first use. */
  private lazy val renderer: Future[Unit] =
    if js.typeOf(js.Dynamic.global.protomapsL) != "undefined" then Future.unit
    else
      val p = Promise[Unit]()
      val s = dom.document.createElement("script").asInstanceOf[dom.HTMLScriptElement]
      s.src = ScriptSrc
      s.addEventListener("load", (_: dom.Event) => { val _ = p.trySuccess(()) })
      s.addEventListener("error", (_: dom.Event) => { val _ = p.tryFailure(Exception(s"could not load $ScriptSrc")) })
      dom.document.head.appendChild(s)
      p.future

  /** Show the local map in style `styleId` (`None`: the default) on `map`. `raster(on)` puts the
    * ordinary tiles back (on) or away (off) — a no-op for a `local` page. */
  def enter(map: LeafletMap, docsUrl: String, raster: Boolean => Unit, styleId: Option[String]): Unit =
    generation += 1
    val mine = generation
    clear(map)
    // the server's answer (else what it said last), then the renderer — only when there is a map to draw
    entered = Some((map, docsUrl, raster, styleId))
    val known = refresh().map(Option(_)).recover { case _ => status.now() }
    val ss    = refreshStyles().map(Option(_)).recover { case _ => styles.now() }
    known.zip(ss).flatMap { (s, t) =>
      if !s.exists(_.available) then Future.successful((s, t))
      else
        val fonts = s.zip(t).flatMap((st, all) => all.pick(styleId).map(p => (withView(p, st), all)))
          .fold(Future.unit)((st, all) => if st.labels then fontsReady(st, TerrainRules.language(st, all)) else Future.unit)
        renderer.zip(fonts).map(_ => (s, t))
    }.onComplete { r =>
      if mine == generation then
        val s = r.toOption.flatMap(_._1)
        val style = r.toOption.flatMap(_._2).flatMap(_.pick(styleId))
        s.flatMap(st => template(st, BasemapLayer.Vector).map(st -> _)).zip(style.zip(r.toOption.flatMap(_._2))) match
          case Some(((st, vector), (style, all))) =>
            raster(false)
            // closer in than the raster tiles go: the relief is resampled, the contours get finer
            if rasterMaxZoom.isEmpty then rasterMaxZoom = Some(map.asInstanceOf[js.Dynamic].getMaxZoom().asInstanceOf[Double])
            map.asInstanceOf[js.Dynamic].setMaxZoom(st.mapMaxZoom)
            tone.set(if style.tone == "light" then MapTone.Light else MapTone.Dark)
            draw(map, st, vector, withView(style, st), all)
            val lacking = st.missing(style.id)
            if lacking.nonEmpty then setNotice(map, lackingNotice(lacking), docsUrl, missing = false, lacking = true)
            else if !style.modern then setNotice(map, PastNotice, docsUrl, missing = false)
            watch(mine, all.checkSeconds)
          case None =>
            raster(true)
            setNotice(map, r.failed.toOption.fold(missingNotice(s, style.exists(_.modern)))(e => s"The local map could not be drawn: ${e.getMessage}."),
              docsUrl, missing = true)
    }

  /** The layers the server has (`vector`, `terrain`, `labels`, `bathymetry`). Pure. */
  def layersHad(s: BasemapStatus): Set[String] = s.layers.filter(i => i.present && i.error.isEmpty).map(_.layer).toSet

  /** The family's name in the base-map menu: it says when the data is missing. Pure. */
  def familyLabel(s: Option[BasemapStatus]): String =
    if s.forall(_.available) then "Histmersion map" else "Histmersion map — not downloaded"

  /** The preset `p` with this viewer's options over it. */
  private def withView(p: TerrainStyle, s: BasemapStatus): TerrainStyle =
    val prefs = UiState.terrainView.now()
    TerrainView(p, prefs.of(p.id), prefs.language, layersHad(s))

  // what is drawn now — to tell an options change that touches only the names (their layer alone is drawn
  // again) from one that touches the map
  private var current: Option[(LeafletMap, BasemapStatus, String, TerrainStyle, TerrainStyles)] = None
  private var namesLayer: Option[Layer] = None

  /** The viewer changed the map's options: draw again — only the names' layer when only the names changed. */
  def viewChanged(): Unit = current.foreach { (map, st, vector, was, all) =>
    all.pick(entered.flatMap(_._4)).foreach { p =>
      val now = withView(p, st)
      if now != was then
        val mine = generation
        val ready = if now.labels then fontsReady(now, TerrainRules.language(now, all)) else Future.unit
        if TerrainView.onlyNames(was, now) then
          ready.foreach { _ =>
            if mine == generation then
              namesLayer.foreach(l => map.removeLayer(l))
              drawn = drawn.filterNot(l => namesLayer.exists(_ eq l))
              namesLayer = drawNames(map, st, now, all)
              drawn = drawn ++ namesLayer.toList
              current = Some((map, st, vector, now, all))
          }
        else
          ready.foreach { _ =>
            if mine == generation then
              drawn.foreach(l => map.removeLayer(l))
              drawn = Nil
              ReliefLayer.reset()
              draw(map, st, vector, now, all)
          }
    }
  }

  /** Leave the terrain map: its layers and its notice go. */
  def leave(map: LeafletMap): Unit =
    generation += 1
    entered = None
    clear(map)

  /** While this drawing is the current one, ask every `seconds` whether the styles changed (only
    * while the page is visible), and draw the map again when they did. */
  private def watch(mine: Int, seconds: Int): Unit =
    if seconds > 0 then
      poll = dom.window.setInterval(() =>
        if mine == generation && !dom.document.hidden then
          val was = styles.now().map(_.version)
          refreshStyles().foreach { s =>
            if mine == generation && !was.contains(s.version) then entered.foreach((m, d, r, id) => enter(m, d, r, id))
          }
      , seconds * 1000.0)

  // the map's own deepest zoom while the terrain map raises it, to put back when it goes
  private var rasterMaxZoom: Option[Double] = None

  private def clear(map: LeafletMap): Unit =
    dom.window.clearInterval(poll)
    rasterMaxZoom.foreach(z => map.asInstanceOf[js.Dynamic].setMaxZoom(z))
    rasterMaxZoom = None
    ReliefLayer.reset()
    current = None
    namesLayer = None
    tone.set(MapTone.Dark)
    drawn.foreach(l => map.removeLayer(l))
    drawn = Nil
    notice.foreach(_.remove())
    notice = js.undefined
    map.off("resize", fitHandler)
    map.getContainer().asInstanceOf[dom.HTMLElement].style.setProperty("--terrain-notice-lift", "")
    map.getContainer().classList.remove("terrain-missing")
    map.getContainer().classList.remove("terrain-on")
    map.getContainer().asInstanceOf[dom.HTMLElement].style.setProperty("--terrain-bg", "")   // empty: the declaration goes

  private def lookup(token: String): String = ThemeTokens(token)

  /** A tile URL template asking only for `reads` (the layers, geometries and kinds the rules draw), their
    * features' `keys` (the properties the rules read; none: all), and a layer drawn only closer in from
    * that tile zoom on (`fromZoom`). Pure. */
  def withSubset(url: String, reads: Map[String, Basemap.Read], keys: List[String] = Nil, fromZoom: Map[String, Int] = Map.empty): String =
    def enc(v: String) = scala.scalajs.js.URIUtils.encodeURIComponent(v)
    if reads.isEmpty then url
    else s"$url&layers=${enc(Basemap.subsetParam(reads, fromZoom))}" + (if keys.isEmpty then "" else s"&keys=${enc(keys.distinct.mkString(","))}")

  /** What one protomaps layer asks the server for: per source the data layers read, from which zoom,
    * and the properties. */
  private final case class Reads(layers: Map[String, Map[String, Basemap.Read]], zooms: Map[String, Map[String, Int]], keys: List[String])

  private def sourcesOf(s: BasemapStatus, r: Reads): js.Object =
    val reads = r.layers
    val o = js.Dynamic.literal()
    reads.foreach { (src, layers) =>
      BasemapLayer.parse(src).foreach { l =>
        template(s, l).foreach { url =>
          o.updateDynamic(src)(js.Dynamic.literal(url = withSubset(url, layers, r.keys, r.zooms.getOrElse(src, Map.empty)),
            maxDataZoom = s.layers.find(_.layer == l.wire).flatMap(_.maxZoom).getOrElse(10)))
        }
      }
    }
    o

  private def draw(map: LeafletMap, s: BasemapStatus, vector: String, style: TerrainStyle, all: TerrainStyles): Unit =
    val have   = sources(s)
    val rules  = TerrainRules.compile(style, TerrainRules.language(style, all), have, style.labels, lookup)
    def layer(paint: js.Array[js.Object], labels: js.Array[js.Object], reads: Reads,
        z: Int, cls: String, background: Boolean): Layer = vectorLayer(map, s, style, paint, labels, reads, z, cls, background)
    map.getContainer().classList.add("terrain-on")
    map.getContainer().asInstanceOf[dom.HTMLElement].style.setProperty("--terrain-bg", TerrainRules.colour(style.background, lookup))
    // bottom to top: the elevation tint (a style with one), the vector layer, the shading and contours,
    // the names — all in the tile pane, below every marker
    val setup = Option.when(style.relief.enabled)(()).flatMap(_ =>
      template(s, BasemapLayer.Terrain).zip(s.layers.find(_.layer == BasemapLayer.Terrain.wire))).map { (url, info) =>
      val sea = template(s, BasemapLayer.Bathymetry).zip(s.layers.find(_.layer == BasemapLayer.Bathymetry.wire).flatMap(_.maxZoom))
      (ReliefLayer.setup(url, info.deepest.getOrElse(8), style.relief, TerrainRules.colour(_, lookup), dom.window.devicePixelRatio, sea)
        .copy(extractMax = info.maxZoom.getOrElse(8)), info)
    }
    val tinted = setup.exists(_._1.tinted)
    val reliefs = setup.toList.flatMap((su, info) => ReliefLayer.layers(su, reliefBounds(info), tintZ = 1, shadeZ = 3))
    // with a tint: the ground (the land's fill, the open sea's) under it, the rest of the vector layer over it
    val base =
      if !tinted then List(layer(rules.paint, js.Array(), Reads(rules.paintReads, rules.paintZooms, rules.paintKeys), 2, "terrain-base", background = true))
      else
        val ground = TerrainRules.compile(style, "en", have, false, lookup, TerrainRules.isGround)
        val cover  = TerrainRules.compile(style, "en", have, false, lookup, l => !TerrainRules.isGround(l))
        List(layer(ground.paint, js.Array(), Reads(ground.paintReads, ground.paintZooms, ground.paintKeys), 0, "terrain-ground", background = true),
          layer(cover.paint, js.Array(), Reads(cover.paintReads, cover.paintZooms, cover.paintKeys), 2, "terrain-base", background = false))
    reliefs.foreach(_.addTo(map))
    // the names in a layer of their own: above the relief (never shaded), below every marker
    val names = Option.when(rules.labels.nonEmpty)(layer(js.Array(), rules.labels, Reads(rules.labelReads, rules.labelZooms, rules.labelKeys), 4, "terrain-labels", background = false))
    names.foreach(exposeLabels)
    namesLayer = names
    current = Some((map, s, "", style, all))
    drawn = base ++ reliefs ++ names.toList

  /** A protomaps-leaflet layer of the vector sources (the extract, the names). */
  private def vectorLayer(map: LeafletMap, s: BasemapStatus, style: TerrainStyle, paint: js.Array[js.Object],
      labels: js.Array[js.Object], reads: Reads, z: Int, cls: String,
      background: Boolean): Layer =
    val o = js.Dynamic.literal(sources = sourcesOf(s, reads), paintRules = paint, labelRules = labels,
      attribution = attribution(s), zIndex = z, className = cls)
    if background then o.backgroundColor = TerrainRules.colour(style.background, lookup)
    s.layers.find(_.layer == BasemapLayer.Vector.wire).flatMap(bounds).foreach(b => o.bounds = b)
    val l = Protomaps.leafletLayer(o)
    l.addTo(map)
    l

  /** The names' layer alone (another language, names on again): the style's label rules compiled again. */
  private def drawNames(map: LeafletMap, s: BasemapStatus, style: TerrainStyle, all: TerrainStyles): Option[Layer] =
    val rules = TerrainRules.compile(style, TerrainRules.language(style, all), sources(s), style.labels, lookup)
    val names = Option.when(rules.labels.nonEmpty)(vectorLayer(map, s, style, js.Array(), rules.labels, Reads(rules.labelReads, rules.labelZooms, rules.labelKeys), 4,
      "terrain-labels", background = false))
    names.foreach(exposeLabels)
    names

  /** For the browser regressions: `window.__terrainLabelBoxes()` answers the names placed now, per zoom —
    * each box (minX, minY, maxX, maxY, the label's number, its text when it is a name at a point) from
    * protomaps-leaflet's own collision index,
    * which is what decides what is drawn. */
  private def exposeLabels(names: Layer): Unit =
    val fn: js.Function0[js.Any] = () =>
      val out = js.Dictionary.empty[js.Any]
      val ls = names.asInstanceOf[js.Dynamic].labelers
      if !js.isUndefined(ls) && !js.isUndefined(ls.labelers) then
        val ids = js.Dynamic.newInstance(js.Dynamic.global.Map)()
        ls.labelers.asInstanceOf[js.Dynamic].forEach(((labeler: js.Dynamic, zoom: js.Any) =>
          val boxes = js.Array[js.Any]()
          labeler.index.tree.all().asInstanceOf[js.Array[js.Dynamic]].foreach { e =>
            val lab = e.indexedLabel
            if !ids.has(lab).asInstanceOf[Boolean] then ids.set(lab, ids.size)
            boxes.push(js.Array(e.minX, e.minY, e.maxX, e.maxY, ids.get(lab), lab.deduplicationKey))
          }
          out(zoom.toString) = boxes
        ): js.Function2[js.Dynamic, js.Any, Unit])
      out
    dom.window.asInstanceOf[js.Dynamic].__terrainLabelBoxes = fn

  private def setNotice(map: LeafletMap, text: String, docsUrl: String, missing: Boolean, lacking: Boolean = false): Unit =
    notice.foreach(_.remove())
    map.getContainer().classList.toggle("terrain-missing", missing)
    val ctrl = L.control(js.Dynamic.literal(position = "bottomleft").asInstanceOf[js.Object]).asInstanceOf[js.Dynamic]
    ctrl.onAdd = { (_: js.Dynamic) =>
      val el = DomUtil.create("div", if missing || lacking then "terrain-notice is-missing" else "terrain-notice")
      el.setAttribute("role", "note")
      val p = dom.document.createElement("span")
      p.textContent = text
      val a = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
      a.href = if lacking then docsLink(docsUrl, OfflinePage) else docsLink(docsUrl)
      a.target = "_blank"
      a.rel = "noopener"
      a.textContent = if missing || lacking then "How to download it" else "Why?"
      el.appendChild(p)
      el.appendChild(dom.document.createTextNode(" "))
      el.appendChild(a)
      DomEvent.disableClickPropagation(el)
      el
    }: js.Function1[js.Dynamic, dom.Element]
    ctrl.addTo(map.asInstanceOf[js.Any])
    notice = ctrl
    // the notice and the credits share the map's foot: where they would meet (a narrow map, long credits)
    // the notice's corner rises above the credits' line — measured now and whenever the map resizes
    dom.window.requestAnimationFrame((_: Double) => fitNotice(map))
    map.off("resize", fitHandler)
    fitHandler = (_: LeafletEvent) => fitNotice(map)
    map.on("resize", fitHandler)

  // the resize handler of the notice shown (one at a time), to take off when it goes
  private var fitHandler: js.Function1[LeafletEvent, Unit] = (_: LeafletEvent) => ()

  /** How far the notice's corner rises so it clears the credits: their height and a gap when the two
    * would overlap side by side, else nothing. Pure. */
  def noticeLift(noticeLeft: Double, noticeRight: Double, creditsLeft: Double, creditsRight: Double, creditsHeight: Double): Double =
    if noticeRight > creditsLeft && creditsRight > noticeLeft then creditsHeight + 4 else 0

  private def fitNotice(map: LeafletMap): Unit =
    val c = map.getContainer().asInstanceOf[dom.HTMLElement]
    c.style.setProperty("--terrain-notice-lift", "0px")
    for
      n <- Option(c.querySelector(".terrain-notice"))
      a <- Option(c.querySelector(".leaflet-control-attribution"))
    do
      val nr = n.getBoundingClientRect(); val ar = a.getBoundingClientRect()
      c.style.setProperty("--terrain-notice-lift", s"${noticeLift(nr.left, nr.right, ar.left, ar.right, ar.height)}px")
