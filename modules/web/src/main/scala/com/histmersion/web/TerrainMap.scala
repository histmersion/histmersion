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
import scala.scalajs.js.typedarray.Uint8ClampedArray

/** The terrain base map ([[BaseMap.Terrain]], docs/research-terrain-basemap.md): MODERN terrain only,
  * drawn in the page from the extracts the server holds (`GET /api/basemap/{layer}/{z}/{x}/{y}`) with
  * one of the named styles it serves (`GET /api/basemap/styles`, [[TerrainRules]]):
  *
  *  - the coast, water, rivers and land cover — vector tiles, drawn on canvas by protomaps-leaflet
  *    (loaded on first use) with the style's fills and lines; no roads, places, borders or buildings;
  *  - the relief — elevation tiles shaded here ([[Hillshade]]) into a translucent layer above them;
  *  - the physical names — seas, ranges, deserts, lakes, rivers — the style's labels, in a layer of their
  *    own above the relief and below every marker of the map.
  *
  * While it is shown, the page asks every `checkSeconds` whether the styles changed (the server reads
  * the style file again when it changes) and draws the map again when they did.
  *
  * Always with a notice that this is TODAY's landscape. When the server has not downloaded it, the
  * standard tiles stay, dimmed, and the notice says how to fetch it. */
object TerrainMap:

  /** What the server reports (`GET /api/basemap`); `None` until asked. */
  val status: Var[Option[BasemapStatus]] = Var(None)

  /** The named styles the server serves; `None` until asked. */
  val styles: Var[Option[TerrainStyles]] = Var(None)

  /** The tone of the style shown: the map's own names follow it ([[MapLabels]]). */
  val tone: Var[MapTone] = Var(MapTone.Dark)

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
  def optionValue(id: String): String = s"${BaseMap.Terrain.wire}:$id"

  /** A menu value → the base map and, for the terrain map, the style it names. Pure. */
  def parseOption(v: String): Option[(BaseMap, Option[String])] =
    v.split(":", 2).toList match
      case b :: id :: Nil => BaseMap.fromWire(b).filter(_ == BaseMap.Terrain).map(_ -> Some(id).filter(_.nonEmpty))
      case b :: Nil       => BaseMap.fromWire(b).map(_ -> None)
      case _              => None

  /** The menu's value for the map shown. Pure. */
  def selectValue(b: BaseMap, style: Option[String], ss: Option[TerrainStyles]): String =
    if b != BaseMap.Terrain then b.wire
    else ss.flatMap(_.pick(style)).fold(b.wire)(s => optionValue(s.id))

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The notice over the terrain map. */
  val PastNotice: String =
    "Today's terrain. Coastlines, rivers, lakes and vegetation were different in the past."

  /** The docs section the notice links to, under `ui.docs-url`. */
  def docsLink(docsUrl: String): String =
    val base = if docsUrl.isEmpty || docsUrl.endsWith("/") then docsUrl else docsUrl + "/"
    s"${base}exploring/web-ui.md#the-terrain-map"

  /** The notice when the extracts are not on the server: what to run. */
  def missingNotice(s: Option[BasemapStatus]): String =
    s"The terrain map is not downloaded on this server. Run `${s.map(_.fetchCommand).getOrElse(Basemap.FetchCommand)}` " +
      "on the machine that runs it (a few hundred MB to a few GB), then choose Terrain again."

  /** The base-map menu's label for a terrain style (`None`: before the styles are known): it says
    * when the data is missing. */
  def optionLabel(s: Option[BasemapStatus], style: Option[TerrainStyle] = None): String =
    val name = style.fold(BaseMap.Terrain.label)(t => s"${t.name} (no modern features)")
    if s.forall(_.available) then name else s"$name — not downloaded"

  /** The sources the server has (`vector`, `labels`), as the style's layers name them. */
  def sources(s: BasemapStatus): Set[String] =
    TerrainStyles.Sources.toSet.filter(src => BasemapLayer.parse(src).exists(l => template(s, l).isDefined))

  /** A layer's tile URL template, when it is there. */
  def template(s: BasemapStatus, l: BasemapLayer): Option[String] =
    s.layers.find(i => i.layer == l.wire && i.present && i.error.isEmpty)
      .map(i => BasemapApi.tileTemplate(l, i.version.getOrElse("0")))

  /** The extract's region as Leaflet bounds `[[south, west], [north, east]]`. */
  def bounds(i: BasemapLayerInfo): Option[js.Array[js.Array[Double]]] =
    i.bbox.flatMap(BBox.parse).map(b => js.Array(js.Array(b.minLat, b.minLon), js.Array(b.maxLat, b.maxLon)))

  /** Every credit line of the layers shown, joined. */
  def attribution(s: BasemapStatus): String =
    s.layers.filter(i => i.present && i.error.isEmpty).map(_.attribution).filter(_.nonEmpty).distinct.mkString(" · ")

  // ── drawing ─────────────────────────────────────────────────────────────────────────────────────

  // `var`s: what this module put on the map, and which request is the latest (a quick switch away
  // and back must not leave two copies)
  private var shown: List[Layer]          = Nil
  private var notice: js.UndefOr[js.Dynamic] = js.undefined
  private var generation                  = 0
  private var poll: Int                   = 0
  // what `enter` was called with, for a redraw when the styles change
  private var entered: Option[(LeafletMap, String, Boolean => Unit)] = None

  private val ScriptSrc = "/web/vendor/protomaps-leaflet/protomaps-leaflet.js"

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

  /** Show the terrain map on `map`. `raster(on)` puts the ordinary tiles back (on) or away (off). */
  def enter(map: LeafletMap, docsUrl: String, raster: Boolean => Unit): Unit =
    generation += 1
    val mine = generation
    clear(map)
    // the server's answer (else what it said last), then the renderer — only when there is a map to draw
    entered = Some((map, docsUrl, raster))
    val known = refresh().map(Option(_)).recover { case _ => status.now() }
    val ss    = refreshStyles().map(Option(_)).recover { case _ => styles.now() }
    known.zip(ss).flatMap((s, t) => if s.exists(_.available) then renderer.map(_ => (s, t)) else Future.successful((s, t))).onComplete { r =>
      if mine == generation then
        val s = r.toOption.flatMap(_._1)
        val style = r.toOption.flatMap(_._2).flatMap(_.pick(UiState.terrainStyle.now()))
        s.flatMap(st => template(st, BasemapLayer.Vector).map(st -> _)).zip(style.zip(r.toOption.flatMap(_._2))) match
          case Some(((st, vector), (style, all))) =>
            raster(false)
            tone.set(if style.tone == "light" then MapTone.Light else MapTone.Dark)
            draw(map, st, vector, style, all)
            setNotice(map, PastNotice, docsUrl, missing = false)
            watch(mine, all.checkSeconds)
          case None =>
            raster(true)
            setNotice(map, r.failed.toOption.fold(missingNotice(s))(e => s"The terrain map could not be drawn: ${e.getMessage}."),
              docsUrl, missing = true)
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
            if mine == generation && !was.contains(s.version) then entered.foreach((m, d, r) => enter(m, d, r))
          }
      , seconds * 1000.0)

  private def clear(map: LeafletMap): Unit =
    dom.window.clearInterval(poll)
    tone.set(MapTone.Dark)
    shown.foreach(l => map.removeLayer(l))
    shown = Nil
    notice.foreach(_.remove())
    notice = js.undefined
    map.getContainer().classList.remove("terrain-missing")
    map.getContainer().classList.remove("terrain-on")
    map.getContainer().asInstanceOf[dom.HTMLElement].style.setProperty("--terrain-bg", "")   // empty: the declaration goes

  private def lookup(token: String): String = ThemeTokens(token)

  /** A tile URL template asking only for `reads` (the layers and geometries the rules draw). Pure. */
  def withSubset(url: String, reads: Map[String, Set[Int]]): String =
    if reads.isEmpty then url else s"$url&layers=${scala.scalajs.js.URIUtils.encodeURIComponent(Basemap.subsetParam(reads))}"

  private def sourcesOf(s: BasemapStatus, reads: Map[String, Map[String, Set[Int]]]): js.Object =
    val o = js.Dynamic.literal()
    reads.foreach { (src, layers) =>
      BasemapLayer.parse(src).foreach { l =>
        template(s, l).foreach { url =>
          o.updateDynamic(src)(js.Dynamic.literal(url = withSubset(url, layers),
            maxDataZoom = s.layers.find(_.layer == l.wire).flatMap(_.maxZoom).getOrElse(10)))
        }
      }
    }
    o

  private def draw(map: LeafletMap, s: BasemapStatus, vector: String, style: TerrainStyle, all: TerrainStyles): Unit =
    val credit = attribution(s)
    val vinfo  = s.layers.find(_.layer == BasemapLayer.Vector.wire)
    val have   = sources(s)
    val rules  = TerrainRules.compile(style, TerrainRules.language(style, all), have, UiState.terrainLabels.now(), lookup)
    def layer(paint: js.Array[js.Object], labels: js.Array[js.Object], reads: Map[String, Map[String, Set[Int]]], z: Int,
        cls: String, background: Boolean): Layer =
      val o = js.Dynamic.literal(sources = sourcesOf(s, reads), paintRules = paint, labelRules = labels,
        attribution = credit, zIndex = z, className = cls)
      if background then o.backgroundColor = TerrainRules.colour(style.background, lookup)
      vinfo.flatMap(bounds).foreach(b => o.bounds = b)
      val l = Protomaps.leafletLayer(o)
      l.addTo(map)
      l
    map.getContainer().classList.add("terrain-on")
    map.getContainer().asInstanceOf[dom.HTMLElement].style.setProperty("--terrain-bg", TerrainRules.colour(style.background, lookup))
    val base = layer(rules.paint, js.Array(), rules.paintReads, 1, "terrain-base", background = true)
    val relief = Option.when(style.relief.enabled)(()).flatMap(_ =>
      template(s, BasemapLayer.Terrain).zip(s.layers.find(_.layer == BasemapLayer.Terrain.wire))).map { (url, info) =>
      val l = reliefLayer(url, info, style.relief)
      l.addTo(map)
      l
    }
    // the names in a layer of their own: above the relief (never shaded), below every marker
    val names = Option.when(rules.labels.nonEmpty)(layer(js.Array(), rules.labels, rules.labelReads, 3, "terrain-labels", background = false))
    shown = base :: relief.toList ++ names.toList

  /** The relief: a grid of 512-pixel canvases, one per elevation tile (a 512-pixel tile of zoom
    * `z − 1` covers a 512-pixel cell of zoom `z`), shaded as they arrive. */
  private def reliefLayer(url: String, info: BasemapLayerInfo, r: ReliefStyle): Layer =
    val maxData = info.maxZoom.getOrElse(8)
    val shadow  = tint(r.shadow, r.shadowOpacity)
    val light   = tint(r.highlight, r.highlightOpacity)
    val sun     = Hillshade.Light(r.azimuth, r.altitude)
    val create: js.Function2[js.Dynamic, js.Function2[js.Any, dom.HTMLCanvasElement, Unit], dom.HTMLCanvasElement] =
      (coords, done) =>
        val canvas = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
        canvas.width = 512
        canvas.height = 512
        val z = coords.z.asInstanceOf[Int] - 1
        val x = coords.x.asInstanceOf[Double]
        val y = coords.y.asInstanceOf[Double]
        val tileUrl = url.replace("{z}", z.toString).replace("{x}", x.toLong.toString).replace("{y}", y.toLong.toString)
        shadeInto(canvas, tileUrl, z, y, shadow, light, sun, r.exaggeration).onComplete { r =>
          done(r.failed.toOption.map(e => js.Error(e.getMessage): js.Any).getOrElse(null), canvas)
        }
        canvas
    val cls  = GridLayer.extend(js.Dynamic.literal(createTile = create))
    val opts = js.Dynamic.literal(tileSize = 512, minNativeZoom = 1, maxNativeZoom = maxData + 1, zIndex = 2,
      className = "terrain-relief", updateWhenZooming = false)
    bounds(info).foreach(b => opts.bounds = b)
    js.Dynamic.newInstance(cls)(opts).asInstanceOf[Layer]

  /** A relief colour (a token, hex or rgb) and its strongest opacity. */
  private def tint(c: String, alpha: Double): Hillshade.Tint =
    val (r, g, b) = ThemeTokens.rgb(TerrainRules.colour(c, lookup)).getOrElse((0, 0, 0))
    Hillshade.Tint(r, g, b, math.round(alpha * 255).toInt)

  /** Fetch one elevation tile, shade it, draw it into `canvas`. A tile the extract does not have
    * (204) leaves the canvas transparent. */
  private def shadeInto(canvas: dom.HTMLCanvasElement, url: String, z: Int, y: Double,
      shadow: Hillshade.Tint, light: Hillshade.Tint, sun: Hillshade.Light, ex: ZoomValue): Future[Unit] =
    dom.fetch(url).toFuture.flatMap { r =>
      if r.status == 204 || !r.ok then Future.unit
      else
        r.blob().toFuture.flatMap { blob =>
          js.Dynamic.global.createImageBitmap(blob).asInstanceOf[js.Promise[js.Dynamic]].toFuture.map { bmp =>
            val w   = bmp.width.asInstanceOf[Int]
            val h   = bmp.height.asInstanceOf[Int]
            val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
            canvas.width = w
            canvas.height = h
            ctx.drawImage(bmp.asInstanceOf[dom.HTMLImageElement], 0, 0)
            bmp.close()
            val px  = ctx.getImageData(0, 0, w, h)
            val out = ctx.createImageData(w, h)
            Hillshade.shade(px.data.asInstanceOf[Uint8ClampedArray], w, h, Hillshade.metresPerPixel(z, y, w),
              ex.at(z + 1), sun, shadow, light, out.data.asInstanceOf[Uint8ClampedArray])
            ctx.putImageData(out, 0, 0)
          }
        }
    }

  private def setNotice(map: LeafletMap, text: String, docsUrl: String, missing: Boolean): Unit =
    notice.foreach(_.remove())
    map.getContainer().classList.toggle("terrain-missing", missing)
    val ctrl = L.control(js.Dynamic.literal(position = "bottomleft").asInstanceOf[js.Object]).asInstanceOf[js.Dynamic]
    ctrl.onAdd = { (_: js.Dynamic) =>
      val el = DomUtil.create("div", if missing then "terrain-notice is-missing" else "terrain-notice")
      el.setAttribute("role", "note")
      val p = dom.document.createElement("span")
      p.textContent = text
      val a = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
      a.href = docsLink(docsUrl)
      a.target = "_blank"
      a.rel = "noopener"
      a.textContent = if missing then "How to download it" else "Why?"
      el.appendChild(p)
      el.appendChild(dom.document.createTextNode(" "))
      el.appendChild(a)
      DomEvent.disableClickPropagation(el)
      el
    }: js.Function1[js.Dynamic, dom.Element]
    ctrl.addTo(map.asInstanceOf[js.Any])
    notice = ctrl
