package histdb.web

import com.raquo.laminar.api.L.*
import histdb.core.*
import histdb.web.leaflet.*
import histdb.web.ui.{Layout, MediaKindColors as MKC, OverlayColors}
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

object MapView:

  private var map: LeafletMap               = uninitialized
  private var cluster: MarkerClusterGroup   = uninitialized
  private var radius: Int                   = Layout.markerRadiusDefault
  private val pickerMax                     = Layout.pickerMax
  private val fitMaxZoom                    = Layout.fitMaxZoom
  private var currentSpan: (Long, Long)     = (0L, 1L)
  private var legendEl: dom.Element         = uninitialized
  private val overlayLayer                  = FadingLayer()
  private var overlayOn: Boolean            = false
  private var overlayReqId: Int             = 0
  private val mediaLayer                    = FadingLayer()
  private var mediaOn: Boolean              = false
  private var mediaReqId: Int               = 0
  private val sitesLayer                    = FadingLayer()
  private var sitesOn: Boolean              = false
  private var sitesReqId: Int               = 0
  private var tileLayerRef: TileLayer        = uninitialized
  private var tileUrl: String               = ""
  private var tileMaxZoom: Int              = 12
  private var playbackMode: Boolean         = false
  private var prevMarkers: scala.collection.mutable.LinkedHashMap[String, Layer] =
    scala.collection.mutable.LinkedHashMap.empty
  private var exitLayer: LayerGroup         = uninitialized
  private var exitTimer: Int                = 0

  def leafletMap: LeafletMap = map
  def isOverlayOn: Boolean = overlayOn

  /** Credit line for a tile source: OSM data always; the HOT style also credits its makers. */
  private[web] def attributionFor(url: String): String =
    val osm = "© OpenStreetMap contributors"
    if url.contains("openstreetmap.fr/hot") then s"$osm · tiles © Humanitarian OSM Team, OSM France"
    else osm

  def setTileUrl(url: String, maxZoom: Int): Unit =
    if map != null && tileLayerRef != null then
      map.removeLayer(tileLayerRef)
      tileLayerRef = L.tileLayer(url, js.Dynamic.literal(
        attribution = attributionFor(url),
        maxZoom = maxZoom,
        opacity = 0.85
      ).asInstanceOf[js.Object])
      tileLayerRef.addTo(map)

  def setPlaybackMode(on: Boolean): Unit =
    playbackMode = on
    if !on then prevMarkers.clear()

  private[web] def midOf(pt: GeoHit): Option[Double] =
    for a <- pt.periodStart; b <- pt.periodEnd yield (a + b).toDouble / 2

  private[web] def avgMid(items: Iterable[GeoHit]): Option[Double] =
    val (sum, n) = items.foldLeft((0.0, 0)) { case ((s, c), pt) =>
      midOf(pt) match
        case Some(m) => (s + m, c + 1)
        case None    => (s, c)
    }
    if n > 0 then Some(sum / n) else None

  private[web] def colorFor(mid: Option[Double], span: (Long, Long), confidence: String): String =
    val lo = span._1.min(span._2).toDouble
    val hi = span._1.max(span._2).toDouble
    val base = mid match
      case None    => Fmt.color(0)
      case Some(m) => Fmt.color(if hi == lo then 0.5 else (m - lo) / (hi - lo))
    Fmt.desaturate(base, confidence)

  private def worstConf(items: Iterable[GeoHit]): String =
    Fmt.worstConf(items.map(_.confidence.getOrElse("high")))

  private[web] def midRange(items: Iterable[GeoHit]): Option[(Double, Double)] =
    var lo = Double.MaxValue
    var hi = Double.MinValue
    for pt <- items do
      midOf(pt).foreach { m =>
        if m < lo then lo = m
        if m > hi then hi = m
      }
    if lo <= hi then Some((lo, hi)) else None

  private[web] def gradientBg(items: Iterable[GeoHit], span: (Long, Long), confidence: String): String =
    midRange(items) match
      case None => s"background:${colorFor(None, span, confidence)}"
      case Some((lo, hi)) =>
        val sLo = span._1.min(span._2).toDouble
        val sHi = span._1.max(span._2).toDouble
        def norm(v: Double): Double = if sHi == sLo then 0.5 else (v - sLo) / (sHi - sLo)
        val minC = Fmt.desaturate(Fmt.color(norm(lo)), confidence)
        val maxC = Fmt.desaturate(Fmt.color(norm(hi)), confidence)
        if minC == maxC then s"background:$minC"
        else s"background:linear-gradient(135deg,$minC,$maxC)"

  private def radiusFor(n: Int): Int =
    if n > 1 then radius + 4 + 3 * n.toString.length else radius

  private def maxDotRadius: Int = radiusFor(999)

  private def dotIcon(n: Int, bgStyle: String, extraClass: String, spanYears: Option[Long]): DivIcon =
    val r = radiusFor(n)
    val ringW = if n == 1 && spanYears.exists(_ > 0) then
      math.max(1, math.min(4, math.round(math.log10(spanYears.get.toDouble + 1) * 1.3).toInt))
    else 0
    val d = 2 * r
    val label = if n > 1 then n.toString else ""
    val ringStyle = if ringW > 0 then s";box-shadow:0 0 0 ${ringW}px rgba(255,255,255,.2), 0 0 0 .5px #000" else ""
    L.divIcon(js.Dynamic.literal(
      className = "hd-dot-wrap",
      iconSize = js.Array(d, d),
      iconAnchor = js.Array(r, r),
      html = s"""<span class="hd-dot $extraClass" style="$bgStyle;width:${d}px;height:${d}px;line-height:${d - 1}px;font-size:${math.min(12, r)}px$ringStyle">$label</span>"""
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
        tileUrl = ui.tiles
        tileMaxZoom = ui.tilesMaxZoom
        tileLayerRef = L.tileLayer(ui.tiles, js.Dynamic.literal(
          attribution = attributionFor(ui.tiles),
          maxZoom = ui.tilesMaxZoom,
          opacity = 0.85
        ).asInstanceOf[js.Object])
        tileLayerRef.addTo(map)

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
            dotIcon(n, gradientBg(allItems, currentSpan, worstConf(allItems)), "hd-cluster", None)
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
        restoreLayerState()

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
      wrap.className = "hd-layers-wrap"

      val btn = dom.document.createElement("button").asInstanceOf[dom.html.Button]
      btn.className = "hd-layers-btn"
      btn.title = "map layers"
      btn.innerHTML = "&#x2630;"

      val panel = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      panel.className = "hd-layers-panel"
      panel.style.display = "none"

      def makeCheck(label: String, icon: String, initial: Boolean, toggle: () => Boolean): dom.html.Input =
        val item = dom.document.createElement("label").asInstanceOf[dom.html.Label]
        item.className = "hd-layers-item"
        val cb = dom.document.createElement("input").asInstanceOf[dom.html.Input]
        cb.`type` = "checkbox"
        cb.checked = initial
        cb.addEventListener("change", { (_: dom.Event) =>
          val on = toggle()
          cb.checked = on
        })
        item.appendChild(cb)
        val txt = dom.document.createTextNode(s" $icon $label")
        item.appendChild(txt)
        panel.appendChild(item)
        cb

      val sectionHist = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      sectionHist.className = "hd-layers-section"
      sectionHist.textContent = "Historical"
      panel.appendChild(sectionHist)

      val cbBounds = makeCheck("Boundaries", "⬡", overlayOn, () => toggleOverlay())
      val cbSites = makeCheck("Sites", "🏛", sitesOn, () => toggleSites())
      val cbMedia = makeCheck("Media", "🖼", mediaOn, () => toggleMedia())

      val sectionBase = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      sectionBase.className = "hd-layers-section"
      sectionBase.textContent = "Base map"
      panel.appendChild(sectionBase)

      val baseStyles = List(
        ("Standard", tileUrl),
        ("Muted", "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png")
      )

      var activeBaseUrl = tileUrl
      val radioName = "hd-basemap"
      baseStyles.foreach { (name, url) =>
        val item = dom.document.createElement("label").asInstanceOf[dom.html.Label]
        item.className = "hd-layers-item"
        val rb = dom.document.createElement("input").asInstanceOf[dom.html.Input]
        rb.`type` = "radio"
        rb.name = radioName
        rb.checked = (url == activeBaseUrl)
        rb.addEventListener("change", { (_: dom.Event) =>
          if rb.checked then
            map.removeLayer(tileLayerRef)
            tileLayerRef = L.tileLayer(url, js.Dynamic.literal(
              attribution = attributionFor(url),
              maxZoom = tileMaxZoom,
              opacity = 0.85
            ).asInstanceOf[js.Object])
            tileLayerRef.addTo(map)
            activeBaseUrl = url
        })
        item.appendChild(rb)
        val txt = dom.document.createTextNode(s" $name")
        item.appendChild(txt)
        panel.appendChild(item)
      }

      btn.addEventListener("click", { (e: dom.MouseEvent) =>
        e.stopPropagation()
        val showing = panel.style.display != "none"
        panel.style.display = if showing then "none" else "block"
        cbBounds.checked = overlayOn
        cbSites.checked = sitesOn
        cbMedia.checked = mediaOn
      })

      dom.document.addEventListener("pointerdown", { (e: dom.PointerEvent) =>
        val target = e.target.asInstanceOf[dom.Element]
        if panel.style.display != "none" && !wrap.contains(target) then
          panel.style.display = "none"
      })

      wrap.appendChild(btn)
      wrap.appendChild(panel)
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

  private case class Spot(lat: Double, lon: Double, items: scala.collection.mutable.ListBuffer[GeoHit])

  private def buildSpots(points: List[GeoHit]): scala.collection.mutable.LinkedHashMap[String, Spot] =
    val spots = scala.collection.mutable.LinkedHashMap.empty[String, Spot]
    for pt <- points do
      for lat <- pt.lat; lon <- pt.lon do
        val key = s"$lat|$lon"
        val sp = spots.getOrElseUpdate(key, Spot(lat, lon, scala.collection.mutable.ListBuffer.empty))
        sp.items += pt
    spots

  private def makeMarker(key: String, sp: Spot, span: (Long, Long), entering: Boolean): Layer =
    val n = sp.items.size
    val conf = worstConf(sp.items)
    val bgStyle = if n > 1 then gradientBg(sp.items, span, conf)
      else s"background:${colorFor(avgMid(sp.items), span, conf)}"
    val spanYears = if n == 1 then
      for a <- sp.items.head.periodStart; b <- sp.items.head.periodEnd yield b - a
    else None
    val enterCls = if entering then " hd-entering" else ""
    val extraCls = (if n > 1 then "hd-coincident" else "") + enterCls
    val m = L.marker(js.Array(sp.lat, sp.lon), js.Dynamic.literal(
      icon = dotIcon(n, bgStyle, extraCls, spanYears),
      title = if n > 1 then s"$n findings here"
      else s"${sp.items.head.citekey} (${Fmt.periodStr(sp.items.head.periodStart, sp.items.head.periodEnd)})"
    ).asInstanceOf[js.Object])
    m.asInstanceOf[js.Dynamic].hdItems = sp.items.toJSArray
    m.asInstanceOf[js.Dynamic].hdKey = key
    if n == 1 then m.bindPopup(popupHtml(sp.items.head))
    else m.on("click", { (_: LeafletEvent) =>
      ResultsList.pick(sp.items.toList, s"$n findings at ${sp.lat}, ${sp.lon}", None)
    })
    m

  def render(points: List[GeoHit], span: (Long, Long)): Unit =
    currentSpan = span
    if cluster != null then
      updateLegend(span)
      val spots = buildSpots(points)

      val newKeys = spots.keySet
      val oldKeys = prevMarkers.keySet
      val entering = newKeys -- oldKeys
      val exiting  = oldKeys -- newKeys
      val staying  = newKeys & oldKeys

      if exiting.nonEmpty then
        dom.window.clearTimeout(exitTimer)
        if exitLayer != null then map.removeLayer(exitLayer)
        exitLayer = L.layerGroup()
        for key <- exiting do
          val old = prevMarkers(key)
          val el = old.asInstanceOf[js.Dynamic].getElement()
          if !js.isUndefined(el) && el != null then
            val dot = el.asInstanceOf[dom.Element].querySelector(".hd-dot")
            if dot != null then dot.asInstanceOf[dom.html.Element].classList.add("hd-exiting")
          exitLayer.addLayer(old)
          prevMarkers.remove(key)
        exitLayer.addTo(map)
        exitTimer = dom.window.setTimeout({ () =>
          if exitLayer != null then
            map.removeLayer(exitLayer)
            exitLayer = null
        }, Layout.exitAnimMs)

      for key <- staying do
        val sp = spots(key)
        val oldMarker = prevMarkers(key)
        cluster.removeLayer(oldMarker)
        val m = makeMarker(key, sp, span, entering = false)
        cluster.addLayer(m)
        prevMarkers(key) = m

      if entering.nonEmpty then
        val newMarkers = js.Array[Layer]()
        for key <- entering do
          val sp = spots(key)
          val m = makeMarker(key, sp, span, entering = prevMarkers.nonEmpty)
          newMarkers.push(m)
          prevMarkers(key) = m
        cluster.addLayers(newMarkers)

      reloadLayers(span)

  /** Overlay layers follow the map at most once per `Layout.layerReloadMs`, so a timeline drag
    * (which renders on every pointer move) does not fire a request burst. */
  private val reloadLayers = Throttle[(Long, Long)](Layout.layerReloadMs, { span =>
    if overlayOn then loadOverlay(span)
    if mediaOn then loadMedia(span)
    if sitesOn then loadSites(span)
  })

  /** Place GeoJSON by (kind, asOf): a drag back and forth over the same years is served locally. */
  private val geoCache = scala.collection.mutable.LinkedHashMap.empty[(String, Long), scala.concurrent.Future[String]]

  private def placesGeoCached(kind: String, asOf: Long, limit: Int): scala.concurrent.Future[String] =
    val k = (kind, asOf)
    geoCache.get(k) match
      case Some(f) => f
      case None =>
        val f = ApiClient.placesGeo(kind = Some(kind), asOf = Some(asOf), limit = Some(limit))
        f.failed.foreach(_ => geoCache.remove(k))(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)
        geoCache(k) = f
        while geoCache.size > Layout.geoCacheSize do geoCache.remove(geoCache.head._1)
        f

  private def dynStr(v: js.Dynamic): Option[String] =
    if js.isUndefined(v) || v == null then None else Some(v.toString)

  private def dynPeriod(props: js.Dynamic): Option[(Double, Double)] =
    val ps = props.selectDynamic("periodStart")
    val pe = props.selectDynamic("periodEnd")
    if js.isUndefined(ps) || js.isUndefined(pe) || ps == null || pe == null then None
    else Some((ps.asInstanceOf[Double], pe.asInstanceOf[Double]))

  private def viridisAt(mid: Double, span: (Long, Long)): String =
    val sLo = span._1.min(span._2).toDouble
    val sHi = span._1.max(span._2).toDouble
    Fmt.color(if sHi == sLo then 0.5 else ((mid - sLo) / (sHi - sLo)).max(0).min(1))

  private def saveLayerState(): Unit =
    try dom.window.localStorage.setItem("histdb.layers",
      js.JSON.stringify(js.Dynamic.literal(overlay = overlayOn, media = mediaOn, sites = sitesOn, base = tileUrl)))
    catch case _: Exception => ()

  private def restoreLayerState(): Unit =
    try
      val raw = dom.window.localStorage.getItem("histdb.layers")
      if raw != null then
        val d = js.JSON.parse(raw)
        if d.overlay.asInstanceOf[Boolean] then toggleOverlay()
        if d.media.asInstanceOf[Boolean] then toggleMedia()
        if d.sites.asInstanceOf[Boolean] then toggleSites()
    catch case _: Exception => ()

  def toggleOverlay(): Boolean =
    overlayOn = !overlayOn
    if overlayOn then
      overlayLayer.attach(map)
      loadOverlay(currentSpan)
    else overlayLayer.detach(map)
    saveLayerState()
    overlayOn

  private def loadOverlay(span: (Long, Long)): Unit =
    val mid = (span._1 + span._2) / 2
    overlayReqId += 1
    val myId = overlayReqId
    placesGeoCached("polity", mid, Layout.overlayFetchLimit).foreach { geojson =>
      if myId == overlayReqId then
        val features = js.JSON.parse(geojson).features.asInstanceOf[js.Array[js.Dynamic]]
        // largest first, so small polities sit on top and stay hoverable
        val sorted = features.toList.sortBy(f => dynPeriod(f.properties).map((a, b) => a - b).getOrElse(99999.0))
        val mode = playbackMode
        def colorOf(f: js.Dynamic): String =
          viridisAt(dynPeriod(f.properties).map((a, b) => (a + b) / 2).getOrElse(mid.toDouble), span)
        overlayLayer.sync[js.Dynamic](
          sorted,
          // the tooltip kind is fixed at creation, so a playback toggle swaps (and cross-fades) all
          key = f => s"${dynStr(f.properties.id).orElse(dynStr(f.properties.label)).getOrElse("?")}|$mode",
          create = f => polityLayer(f, colorOf(f), mode),
          update = (lyr, f) =>
            val c = colorOf(f)
            lyr.asInstanceOf[js.Dynamic].setStyle(js.Dynamic.literal(color = c, fillColor = c))
        )
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  private def polityLayer(feature: js.Dynamic, color: String, playback: Boolean): Layer =
    val props = feature.properties
    val label = dynStr(props.label).getOrElse("unnamed")
    val period = dynPeriod(props).map((a, b) => s" (${Fmt.yr(a.toLong)}–${Fmt.yr(b.toLong)})").getOrElse("")
    L.geoJSON(js.Dynamic.literal(`type` = "FeatureCollection", features = js.Array(feature)), js.Dynamic.literal(
      style = { (_: js.Dynamic) =>
        js.Dynamic.literal(
          color = color, weight = 1.5, opacity = 0.8,
          fillColor = color, fillOpacity = 0.1, className = "hd-polity"
        )
      }: js.Function1[js.Dynamic, js.Dynamic],
      onEachFeature = { (_: js.Dynamic, leafLayer: js.Dynamic) =>
        if playback then
          leafLayer.bindTooltip(label,
            js.Dynamic.literal(permanent = true, direction = "center", className = "hd-polity-label"))
        else
          leafLayer.bindTooltip(s"$label$period",
            js.Dynamic.literal(sticky = true, className = "hd-overlay-tip"))
      }: js.Function2[js.Dynamic, js.Dynamic, Unit]
    ).asInstanceOf[js.Object])

  def toggleMedia(): Boolean =
    mediaOn = !mediaOn
    if mediaOn then
      mediaLayer.attach(map)
      loadMedia(currentSpan)
    else mediaLayer.detach(map)
    saveLayerState()
    mediaOn

  private def loadMedia(span: (Long, Long)): Unit =
    mediaReqId += 1
    val myId = mediaReqId
    ApiClient.mediaGeo(from = Some(span._1), to = Some(span._2), limit = Some(Layout.mediaFetchLimit)).foreach { hits =>
      if myId == mediaReqId then
        mediaLayer.sync[MediaGeoHit](
          hits,
          key = h => s"${h.citekey}|${h.assetName}|${h.lat}|${h.lon}",
          create = mediaMarker,
          update = (_, _) => ()
        )
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  private def mediaMarker(h: MediaGeoHit): Layer =
    val color = MKC(h.kind.getOrElse("scan"))
    val period = Fmt.periodStr(h.periodStart, h.periodEnd)
    val caption = h.caption.map(c => s"<br><i>${Fmt.esc(c)}</i>").getOrElse("")
    val icon = L.divIcon(js.Dynamic.literal(
      className = "hd-dot-wrap",
      iconSize = js.Array(Layout.mediaDotSize.value, Layout.mediaDotSize.value),
      iconAnchor = js.Array(Layout.mediaDotSize.value / 2, Layout.mediaDotSize.value / 2),
      html = s"""<span class="hd-media-dot" style="background:$color" title="${Fmt.display(h.kind.getOrElse("media"))}"></span>"""
    ).asInstanceOf[js.Object])
    val m = L.marker(js.Array(h.lat, h.lon), js.Dynamic.literal(icon = icon).asInstanceOf[js.Object])
    m.bindPopup(s"""<b>${Fmt.esc(h.citekey)}</b> / ${Fmt.esc(h.assetName)}<br>""" +
      s"""${Fmt.display(h.kind.getOrElse("media"))} · $period$caption""")
    m

  def toggleSites(): Boolean =
    sitesOn = !sitesOn
    if sitesOn then
      sitesLayer.attach(map)
      loadSites(currentSpan)
    else sitesLayer.detach(map)
    saveLayerState()
    sitesOn

  private def loadSites(span: (Long, Long)): Unit =
    val mid = (span._1 + span._2) / 2
    sitesReqId += 1
    val myId = sitesReqId
    placesGeoCached("site", mid, Layout.siteFetchLimit).foreach { geojson =>
      if myId == sitesReqId then
        val features = js.JSON.parse(geojson).features.asInstanceOf[js.Array[js.Dynamic]]
        def colorOf(f: js.Dynamic): String =
          viridisAt(dynPeriod(f.properties).map((a, b) => (a + b) / 2).getOrElse(mid.toDouble), span)
        def latLon(f: js.Dynamic): (Double, Double) =
          val c = f.geometry.coordinates
          (c.selectDynamic("1").asInstanceOf[Double], c.selectDynamic("0").asInstanceOf[Double])
        sitesLayer.sync[js.Dynamic](
          features.toList,
          key = f => dynStr(f.properties.id).getOrElse(latLon(f).toString),
          create = f => siteMarker(f, latLon(f), colorOf(f)),
          update = (lyr, f) =>
            val c = colorOf(f)
            lyr.asInstanceOf[js.Dynamic].setStyle(js.Dynamic.literal(color = c, fillColor = c))
        )
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  private def siteMarker(feature: js.Dynamic, at: (Double, Double), color: String): Layer =
    val props = feature.properties
    val label = dynStr(props.label).getOrElse("")
    val marker = L.circleMarker(js.Array(at._1, at._2), js.Dynamic.literal(
      radius = 4, color = color, fillColor = color, fillOpacity = 0.9,
      weight = 1, className = "hd-site-marker"
    ).asInstanceOf[js.Object])
    if label.nonEmpty then
      val periodStr = dynPeriod(props).map((a, b) => s" (${Fmt.yr(a.toLong)}–${Fmt.yr(b.toLong)})").getOrElse("")
      marker.bindTooltip(s"${Fmt.display(label)}$periodStr", js.Dynamic.literal(
        permanent = false, direction = "right", className = "hd-site-tip",
        offset = js.Array(6, 0)
      ).asInstanceOf[js.Object])
    dynStr(props.id).foreach { id =>
      // clicking a site previews the place (as of the current span's midpoint), not a source
      marker.on("click", { (_: LeafletEvent) =>
        PlacesInput.preview(id, Some((currentSpan._1 + currentSpan._2) / 2))
      })
    }
    marker
