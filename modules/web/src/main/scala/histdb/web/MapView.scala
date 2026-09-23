package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import histdb.web.leaflet.*
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

object MapView:

  private var map: LeafletMap               = uninitialized
  private var cluster: MarkerClusterGroup   = uninitialized
  private var radius: Int                   = 5
  private val pickerMax                     = 15
  private val fitMaxZoom                    = 9
  private var currentSpan: (Long, Long)     = (0L, 1L)
  private var legendEl: dom.Element         = uninitialized
  private var overlayLayer: GeoJSONLayer    = uninitialized
  private var overlayOn: Boolean            = false
  private var overlayReqId: Int             = 0
  private var mediaLayer: LayerGroup        = uninitialized
  private var mediaOn: Boolean              = false
  private var mediaReqId: Int               = 0

  def leafletMap: LeafletMap = map

  private def midOf(pt: GeoHit): Option[Double] =
    for a <- pt.periodStart; b <- pt.periodEnd yield (a + b).toDouble / 2

  private def avgMid(items: Iterable[GeoHit]): Option[Double] =
    val (sum, n) = items.foldLeft((0.0, 0)) { case ((s, c), pt) =>
      midOf(pt) match
        case Some(m) => (s + m, c + 1)
        case None    => (s, c)
    }
    if n > 0 then Some(sum / n) else None

  private def colorFor(mid: Option[Double], span: (Long, Long), confidence: String): String =
    val lo = span._1.min(span._2).toDouble
    val hi = span._1.max(span._2).toDouble
    val base = mid match
      case None    => Fmt.color(0)
      case Some(m) => Fmt.color(if hi == lo then 0.5 else (m - lo) / (hi - lo))
    Fmt.desaturate(base, confidence)

  private def worstConf(items: Iterable[GeoHit]): String =
    Fmt.worstConf(items.map(_.confidence.getOrElse("high")))

  private def radiusFor(n: Int): Int =
    if n > 1 then radius + 4 + 3 * n.toString.length else radius

  private def maxDotRadius: Int = radiusFor(999)

  private def dotIcon(n: Int, color: String, extraClass: String, spanYears: Option[Long]): DivIcon =
    val r = radiusFor(n)
    val ringW = if n == 1 && spanYears.exists(_ > 0) then
      math.max(1, math.min(4, math.round(math.log10(spanYears.get.toDouble + 1) * 1.3).toInt))
    else 0
    val d = 2 * r
    val label = if n > 1 then n.toString else ""
    val bg = s"background:$color"
    val ringStyle = if ringW > 0 then s";box-shadow:0 0 0 ${ringW}px ${color}44, 0 0 0 .5px #000" else ""
    L.divIcon(js.Dynamic.literal(
      className = "hd-dot-wrap",
      iconSize = js.Array(d, d),
      iconAnchor = js.Array(r, r),
      html = s"""<span class="hd-dot $extraClass" style="$bg;width:${d}px;height:${d}px;line-height:${d - 1}px;font-size:${math.min(12, r)}px$ringStyle">$label</span>"""
    ).asInstanceOf[js.Object])

  private def popupHtml(pt: GeoHit): String =
    val conf = pt.confidence match
      case Some(c) if c != "high" => s""" <span class="hd-conf">($c)</span>"""
      case _                      => ""
    val ck = Fmt.esc(pt.citekey)
    val title = Fmt.esc(pt.title)
    val period = Fmt.periodStr(pt.periodStart, pt.periodEnd)
    val claim = Fmt.esc(pt.claim)
    s"""<b>$ck</b> — $title<br><i>$period</i><br>$claim$conf<br>""" +
      s"""<a href="#" data-ck="$ck">read source</a>""" +
      s""" · <a href="/source/${Fmt.esc(js.URIUtils.encodeURIComponent(pt.citekey))}" target="_blank">new tab ↗</a>"""

  def init(ui: UiConfig): HtmlElement =
    radius = ui.markerRadius
    div(
      idAttr := "map",
      cls := "map-container",
      onMountCallback { ctx =>
        map = L.map("map", js.Dynamic.literal(worldCopyJump = true).asInstanceOf[js.Object])
        map.setView(js.Array(37.0, 30.0), 5)
        L.tileLayer(ui.tiles, js.Dynamic.literal(
          attribution = "© OpenStreetMap",
          maxZoom = ui.tilesMaxZoom,
          opacity = 0.85
        ).asInstanceOf[js.Object]).addTo(map)

        cluster = L.markerClusterGroup(js.Dynamic.literal(
          maxClusterRadius = 2 * maxDotRadius,
          spiderfyOnMaxZoom = false,
          zoomToBoundsOnClick = false,
          showCoverageOnHover = false,
          iconCreateFunction = { (c: js.Dynamic) =>
            val kids = c.getAllChildMarkers().asInstanceOf[js.Array[js.Dynamic]]
            var n = 0
            val allItems = scala.collection.mutable.ListBuffer.empty[GeoHit]
            kids.foreach { m =>
              val items = m.hdItems.asInstanceOf[js.Array[GeoHit]]
              n += items.length
              allItems ++= items
            }
            dotIcon(n, colorFor(avgMid(allItems), currentSpan, worstConf(allItems)), "hd-cluster", None)
          }: js.Function1[js.Dynamic, DivIcon]
        ).asInstanceOf[js.Object])

        cluster.on("clusterclick", { (e: LeafletEvent) =>
          val items = e.layer.getAllChildMarkers().asInstanceOf[js.Array[js.Dynamic]]
            .flatMap(_.hdItems.asInstanceOf[js.Array[GeoHit]])
          val bounds = e.layer.getBounds().asInstanceOf[LatLngBounds]
          if items.length > pickerMax then
            map.fitBounds(bounds, js.Dynamic.literal(
              padding = js.Array(40, 40), maxZoom = fitMaxZoom).asInstanceOf[js.Object])
          else
            ResultsList.pick(items.toList, s"${items.length} finding(s) in this cluster",
              Some(() => map.fitBounds(bounds, js.Dynamic.literal(
                padding = js.Array(40, 40), maxZoom = fitMaxZoom).asInstanceOf[js.Object])))
        })

        cluster.addTo(map)
        addLegend()
        addOverlayControl()

        map.getContainer().addEventListener("click", { (e: dom.MouseEvent) =>
          val target = e.target.asInstanceOf[dom.Element]
          val a = if target.closest != null then
            Option(target.closest("[data-ck]"))
          else None
          a.foreach { el =>
            e.preventDefault()
            ArticleModal.open(el.asInstanceOf[dom.HTMLElement].dataset.get("ck").getOrElse(""))
          }
        })
      }
    )

  private def addLegend(): Unit =
    val ctrl = L.control(js.Dynamic.literal(position = "bottomright").asInstanceOf[js.Object])
    ctrl.asInstanceOf[js.Dynamic].onAdd = { (_: js.Dynamic) =>
      legendEl = DomUtil.create("div", "hd-legend")
      val ramp = (0 until 10).map(i => s"""<i style="background:${Fmt.color(i.toDouble / 9)}"></i>""").mkString
      val confPips = List("high", "medium", "low", "contested").map(c =>
        s"""<i style="background:${Fmt.desaturate("rgb(33,145,140)", c)}" title="$c"></i>""").mkString
      legendEl.innerHTML =
        s"""<div class="hd-ramp">$ramp</div>""" +
        """<div class="hd-ramp-labels"><span class="lo"></span>""" +
        """<span class="mid">older ← → newer</span><span class="hi"></span></div>""" +
        s"""<div class="hd-legend-confidence"><span class="hd-conf-label">certainty</span>$confPips""" +
        """<span class="hd-conf-range">high → contested</span></div>"""
      DomEvent.disableClickPropagation(legendEl)
      legendEl
    }: js.Function1[js.Dynamic, dom.Element]
    ctrl.addTo(map)

  private def addOverlayControl(): Unit =
    val ctrl = L.control(js.Dynamic.literal(position = "topleft").asInstanceOf[js.Object])
    ctrl.asInstanceOf[js.Dynamic].onAdd = { (_: js.Dynamic) =>
      val wrap = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      wrap.className = "hd-map-controls"

      val btnBounds = dom.document.createElement("button").asInstanceOf[dom.html.Button]
      btnBounds.className = "hd-overlay-btn"
      btnBounds.title = "toggle historical boundaries"
      btnBounds.textContent = "⬡"
      btnBounds.addEventListener("click", { (_: dom.MouseEvent) =>
        val on = toggleOverlay()
        if on then btnBounds.classList.add("active") else btnBounds.classList.remove("active")
      })
      wrap.appendChild(btnBounds)

      val btnMedia = dom.document.createElement("button").asInstanceOf[dom.html.Button]
      btnMedia.className = "hd-overlay-btn"
      btnMedia.title = "toggle media markers"
      btnMedia.textContent = "🖼"
      btnMedia.addEventListener("click", { (_: dom.MouseEvent) =>
        val on = toggleMedia()
        if on then btnMedia.classList.add("active") else btnMedia.classList.remove("active")
      })
      wrap.appendChild(btnMedia)

      DomEvent.disableClickPropagation(wrap)
      wrap
    }: js.Function1[js.Dynamic, dom.Element]
    ctrl.addTo(map)

  private def updateLegend(span: (Long, Long)): Unit =
    if legendEl == null then ()
    else
      val lo = legendEl.querySelector(".lo")
      val hi = legendEl.querySelector(".hi")
      if lo != null then lo.textContent = Fmt.yr(span._1.min(span._2))
      if hi != null then hi.textContent = Fmt.yr(span._1.max(span._2))

  def render(points: List[GeoHit], span: (Long, Long)): Unit =
    currentSpan = span
    if cluster != null then
      updateLegend(span)
      cluster.clearLayers()

      case class Spot(lat: Double, lon: Double, items: scala.collection.mutable.ListBuffer[GeoHit])

      val spots = scala.collection.mutable.LinkedHashMap.empty[String, Spot]
      for pt <- points do
        for lat <- pt.lat; lon <- pt.lon do
          val key = s"$lat|$lon"
          val sp = spots.getOrElseUpdate(key, Spot(lat, lon, scala.collection.mutable.ListBuffer.empty))
          sp.items += pt

      val markers = js.Array[Layer]()
      for sp <- spots.values do
        val n = sp.items.size
        val dotColor = colorFor(avgMid(sp.items), span, worstConf(sp.items))
        val spanYears = if n == 1 then
          for a <- sp.items.head.periodStart; b <- sp.items.head.periodEnd yield b - a
        else None
        val m = L.marker(js.Array(sp.lat, sp.lon), js.Dynamic.literal(
          icon = dotIcon(n, dotColor, if n > 1 then "hd-coincident" else "", spanYears),
          title = if n > 1 then s"$n findings here"
          else s"${sp.items.head.citekey} (${Fmt.periodStr(sp.items.head.periodStart, sp.items.head.periodEnd)})"
        ).asInstanceOf[js.Object])
        m.asInstanceOf[js.Dynamic].hdItems = sp.items.toJSArray
        if n == 1 then m.bindPopup(popupHtml(sp.items.head))
        else m.on("click", { (_: LeafletEvent) =>
          ResultsList.pick(sp.items.toList, s"$n findings at ${sp.lat}, ${sp.lon}", None)
        })
        markers.push(m)
      cluster.addLayers(markers)
      if overlayOn then loadOverlay(span)
      if mediaOn then loadMedia(span)

  def toggleOverlay(): Boolean =
    overlayOn = !overlayOn
    if overlayOn then loadOverlay(currentSpan)
    else if overlayLayer != null then map.removeLayer(overlayLayer)
    overlayOn

  private def loadOverlay(span: (Long, Long)): Unit =
    val mid = (span._1 + span._2) / 2
    overlayReqId += 1
    val myId = overlayReqId
    ApiClient.placesGeo(kind = Some("polity"), asOf = Some(mid), limit = Some(50)).foreach { geojson =>
      if myId == overlayReqId then
        if overlayLayer != null then map.removeLayer(overlayLayer)
        val data = js.JSON.parse(geojson)
        overlayLayer = L.geoJSON(data, js.Dynamic.literal(
          style = { (_: js.Dynamic) =>
            js.Dynamic.literal(
              color = "#f0c040", weight = 1.5, opacity = 0.7,
              fillColor = "#f0c040", fillOpacity = 0.08
            )
          }: js.Function1[js.Dynamic, js.Dynamic],
          onEachFeature = { (feature: js.Dynamic, layer: js.Dynamic) =>
            val props = feature.properties
            val label = if js.isUndefined(props.label) || props.label == null then "unnamed"
              else props.label.asInstanceOf[String]
            val ps = props.selectDynamic("periodStart")
            val pe = props.selectDynamic("periodEnd")
            val period = if !js.isUndefined(ps) && !js.isUndefined(pe) then
              s" (${Fmt.yr(ps.asInstanceOf[Double].toLong)}–${Fmt.yr(pe.asInstanceOf[Double].toLong)})"
            else ""
            layer.bindTooltip(s"$label$period",
              js.Dynamic.literal(sticky = true, className = "hd-overlay-tip"))
          }: js.Function2[js.Dynamic, js.Dynamic, Unit]
        ).asInstanceOf[js.Object])
        overlayLayer.addTo(map)
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  private val MediaKindColors: Map[String, String] = Map(
    "figure" -> "#6ea8fe", "photo" -> "#7dc97d", "map" -> "#f0c040",
    "plan" -> "#b060d0", "scan" -> "#9aa3b2"
  )

  def toggleMedia(): Boolean =
    mediaOn = !mediaOn
    if mediaOn then loadMedia(currentSpan)
    else if mediaLayer != null then map.removeLayer(mediaLayer)
    mediaOn

  private def loadMedia(span: (Long, Long)): Unit =
    mediaReqId += 1
    val myId = mediaReqId
    ApiClient.mediaGeo(from = Some(span._1), to = Some(span._2), limit = Some(200)).foreach { hits =>
      if myId == mediaReqId then
        if mediaLayer != null then map.removeLayer(mediaLayer)
        mediaLayer = L.layerGroup()
        for h <- hits do
          val color = MediaKindColors.getOrElse(h.kind.getOrElse("scan"), "#9aa3b2")
          val period = Fmt.periodStr(h.periodStart, h.periodEnd)
          val caption = h.caption.map(c => s"<br><i>${Fmt.esc(c)}</i>").getOrElse("")
          val icon = L.divIcon(js.Dynamic.literal(
            className = "hd-dot-wrap",
            iconSize = js.Array(10, 10),
            iconAnchor = js.Array(5, 5),
            html = s"""<span class="hd-media-dot" style="background:$color" title="${h.kind.getOrElse("media")}"></span>"""
          ).asInstanceOf[js.Object])
          val m = L.marker(js.Array(h.lat, h.lon), js.Dynamic.literal(icon = icon).asInstanceOf[js.Object])
          m.bindPopup(s"""<b>${Fmt.esc(h.citekey)}</b> / ${Fmt.esc(h.assetName)}<br>""" +
            s"""${h.kind.getOrElse("media")} · $period$caption""")
          mediaLayer.addLayer(m)
        mediaLayer.addTo(map)
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)
