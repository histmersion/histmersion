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
  * drawn in the page from the two extracts the server holds (`GET /api/basemap/{layer}/{z}/{x}/{y}`):
  *
  *  - the coast, water, rivers and natural land cover — vector tiles, drawn on canvas by
  *    protomaps-leaflet (loaded on first use) with our own paint rules, coloured by the stylesheet's
  *    `--terrain-*` tokens; there are no roads, places, borders or buildings in the data at all;
  *  - the relief — elevation tiles shaded here ([[Hillshade]]) into a translucent layer above them.
  *
  * Always with a notice that this is TODAY's landscape. When the server has not downloaded it, the
  * standard tiles stay, dimmed, and the notice says how to fetch it. */
object TerrainMap:

  /** What the server reports (`GET /api/basemap`); `None` until asked. */
  val status: Var[Option[BasemapStatus]] = Var(None)

  /** Ask the server again (on start, and each time the terrain map is chosen). */
  def refresh(): Future[BasemapStatus] =
    val f = ApiClient.basemap()
    f.foreach(s => status.set(Some(s)))
    f

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

  /** The base-map menu's label for the terrain map: it says when the data is missing. */
  def optionLabel(s: Option[BasemapStatus]): String =
    if s.forall(_.available) then BaseMap.Terrain.label else s"${BaseMap.Terrain.label} — not downloaded"

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

  /** A kind's paint group: which `--terrain-*` token fills a land-cover polygon of that kind. */
  def coverToken(kind: String): Option[String] = kind match
    case "forest" | "wood"                                   => Some("--terrain-forest")
    case "scrub" | "heath" | "grassland" | "meadow"          => Some("--terrain-scrub")
    case "wetland" | "marsh" | "swamp" | "bog"               => Some("--terrain-wetland")
    case "barren" | "bare_rock" | "scree" | "desert"         => Some("--terrain-barren")
    case "sand" | "beach"                                    => Some("--terrain-sand")
    case "glacier"                                           => Some("--terrain-ice")
    case _                                                   => None

  // ── drawing ─────────────────────────────────────────────────────────────────────────────────────

  // `var`s: what this module put on the map, and which request is the latest (a quick switch away
  // and back must not leave two copies)
  private var shown: List[Layer]          = Nil
  private var notice: js.UndefOr[js.Dynamic] = js.undefined
  private var generation                  = 0

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
    val known = refresh().map(Option(_)).recover { case _ => status.now() }
    known.flatMap(s => if s.exists(_.available) then renderer.map(_ => s) else Future.successful(s)).onComplete { r =>
      if mine == generation then
        val s = r.toOption.flatten
        s.flatMap(st => template(st, BasemapLayer.Vector).map(st -> _)) match
          case Some((st, vector)) =>
            raster(false)
            draw(map, st, vector)
            setNotice(map, PastNotice, docsUrl, missing = false)
          case None =>
            raster(true)
            setNotice(map, r.failed.toOption.fold(missingNotice(s))(e => s"The terrain map could not be drawn: ${e.getMessage}."),
              docsUrl, missing = true)
    }

  /** Leave the terrain map: its layers and its notice go. */
  def leave(map: LeafletMap): Unit =
    generation += 1
    clear(map)

  private def clear(map: LeafletMap): Unit =
    shown.foreach(l => map.removeLayer(l))
    shown = Nil
    notice.foreach(_.remove())
    notice = js.undefined
    map.getContainer().classList.remove("terrain-missing")
    map.getContainer().classList.remove("terrain-on")

  private def color(token: String): String = ThemeTokens(token)

  private def polygon(token: String): PolygonSymbolizer =
    new PolygonSymbolizer(js.Dynamic.literal(fill = color(token)))

  private def kindOf(f: PmFeature): String = f.props.get("kind").map(_.toString).getOrElse("")

  private def rule(layer: String, sym: js.Object, filter: (Int, PmFeature) => Boolean): js.Object =
    js.Dynamic.literal(dataLayer = layer, symbolizer = sym, filter = (filter: js.Function2[Int, PmFeature, Boolean]))

  /** The paint rules, bottom to top: land, land cover (by kind), water bodies, rivers and streams. */
  private def paintRules: js.Array[js.Object] =
    val cover = List("--terrain-forest", "--terrain-scrub", "--terrain-wetland", "--terrain-barren", "--terrain-sand", "--terrain-ice")
    val covers = cover.flatMap { token =>
      List("landcover", "landuse").map(l => rule(l, polygon(token), (_, f) => f.geomType == 3 && coverToken(kindOf(f)).contains(token)))
    }
    val riverWidth: js.Function1[Int, Double] = z => math.max(0.6, (z - 4) * 0.45)
    val streamWidth: js.Function1[Int, Double] = z => math.max(0.4, (z - 8) * 0.4)
    js.Array(
      (rule("earth", polygon("--terrain-land"), (_, f) => f.geomType == 3) ::
        covers :::
        List(
          rule("water", polygon("--terrain-water"), (_, f) => f.geomType == 3),
          rule("water", new LineSymbolizer(js.Dynamic.literal(color = color("--terrain-river"), width = riverWidth)),
            (_, f) => f.geomType == 2 && kindOf(f) == "river"),
          rule("water", new LineSymbolizer(js.Dynamic.literal(color = color("--terrain-river"), width = streamWidth)),
            (z, f) => f.geomType == 2 && z >= 9 && kindOf(f) == "stream")))*)

  private def draw(map: LeafletMap, s: BasemapStatus, vector: String): Unit =
    val credit = attribution(s)
    val vinfo  = s.layers.find(_.layer == BasemapLayer.Vector.wire)
    val vopts = js.Dynamic.literal(
      url = vector,
      paintRules = paintRules,
      labelRules = js.Array[js.Object](),
      backgroundColor = color("--terrain-water"),
      maxDataZoom = vinfo.flatMap(_.maxZoom).getOrElse(10),
      attribution = credit,
      zIndex = 1)
    vinfo.flatMap(bounds).foreach(b => vopts.bounds = b)
    map.getContainer().classList.add("terrain-on")
    val vlayer = Protomaps.leafletLayer(vopts)
    vlayer.addTo(map)
    val relief = template(s, BasemapLayer.Terrain).zip(s.layers.find(_.layer == BasemapLayer.Terrain.wire)).map { (url, info) =>
      val l = reliefLayer(url, info)
      l.addTo(map)
      l
    }
    shown = vlayer :: relief.toList

  /** The relief: a grid of 512-pixel canvases, one per elevation tile (a 512-pixel tile of zoom
    * `z − 1` covers a 512-pixel cell of zoom `z`), shaded as they arrive. */
  private def reliefLayer(url: String, info: BasemapLayerInfo): Layer =
    val maxData = info.maxZoom.getOrElse(8)
    val shadow  = tint("--terrain-shadow")
    val light   = tint("--terrain-highlight")
    val create: js.Function2[js.Dynamic, js.Function2[js.Any, dom.HTMLCanvasElement, Unit], dom.HTMLCanvasElement] =
      (coords, done) =>
        val canvas = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
        canvas.width = 512
        canvas.height = 512
        val z = coords.z.asInstanceOf[Int] - 1
        val x = coords.x.asInstanceOf[Double]
        val y = coords.y.asInstanceOf[Double]
        val tileUrl = url.replace("{z}", z.toString).replace("{x}", x.toLong.toString).replace("{y}", y.toLong.toString)
        shadeInto(canvas, tileUrl, z, y, shadow, light).onComplete { r =>
          done(r.failed.toOption.map(e => js.Error(e.getMessage): js.Any).getOrElse(null), canvas)
        }
        canvas
    val cls  = GridLayer.extend(js.Dynamic.literal(createTile = create))
    val opts = js.Dynamic.literal(tileSize = 512, minNativeZoom = 1, maxNativeZoom = maxData + 1, zIndex = 2,
      className = "terrain-relief", updateWhenZooming = false)
    bounds(info).foreach(b => opts.bounds = b)
    js.Dynamic.newInstance(cls)(opts).asInstanceOf[Layer]

  private def tint(token: String): Hillshade.Tint =
    val (r, g, b) = ThemeTokens.rgb(color(token)).getOrElse((0, 0, 0))
    val alpha = color(s"$token-alpha").toDoubleOption.getOrElse(0.5)
    Hillshade.Tint(r, g, b, math.round(alpha * 255).toInt)

  /** Fetch one elevation tile, shade it, draw it into `canvas`. A tile the extract does not have
    * (204) leaves the canvas transparent. */
  private def shadeInto(canvas: dom.HTMLCanvasElement, url: String, z: Int, y: Double,
      shadow: Hillshade.Tint, light: Hillshade.Tint): Future[Unit] =
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
              Hillshade.exaggeration(z + 1), Hillshade.Light(), shadow, light, out.data.asInstanceOf[Uint8ClampedArray])
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
