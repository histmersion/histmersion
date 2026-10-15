package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import com.histmersion.web.ui.Layout
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
  private var overlayReqId: Int             = 0
  // the four kinds of point (docs/map-layers.md): ONE layer implementation, four descriptors, one
  // cluster group; the events' layer is EventMarkers'
  private val findingsLayer                 = PointLayer(PointKinds.Findings, () => dots, () => spanMid())
  private val mediaLayer                    = PointLayer(PointKinds.Media, () => dots, () => spanMid())
  private var mediaReqId: Int               = 0
  private val sitesLayer                    = PointLayer(PointKinds.Sites, () => dots, () => spanMid())
  private var sitesReqId: Int               = 0
  private var tileLayerRef: TileLayer        = uninitialized
  private var tileUrl: String               = ""
  private var tileMaxZoom: Int              = 12
  private var playbackMode: Boolean         = false

  def leafletMap: LeafletMap = map

  /** The map's ONE cluster group, for every kind of point ([[PointLayer]]): what is near on screen is one
    * bubble, whatever it is. Regions never join. */
  val dots: FadingLayer.Sink = new FadingLayer.Sink:
    def add(layers: js.Array[Layer]): Unit    = if cluster != null && layers.nonEmpty then { val _ = cluster.addLayers(layers) }
    def remove(layers: js.Array[Layer]): Unit = if cluster != null && layers.nonEmpty then { val _ = cluster.removeLayers(layers) }
    def refresh(): Unit                       = if cluster != null then { val _ = cluster.refreshClusters() }

  /** An overlay's switch: how it goes on and off the map, and how it loads the span. */
  private final case class Slot(attach: LeafletMap => Unit, detach: LeafletMap => Unit, load: ((Long, Long)) => Unit)

  private def slot(l: MapLayer): Slot = l match
    case MapLayer.Boundaries => Slot(overlayLayer.attach, overlayLayer.detach, loadOverlay)
    case MapLayer.Sites      => Slot(sitesLayer.attach, sitesLayer.detach, loadSites)
    case MapLayer.Media      => Slot(mediaLayer.attach, mediaLayer.detach, loadMedia)
    case MapLayer.Events     => Slot(EventMarkers.layer.attach, EventMarkers.layer.detach, loadEvents)

  /** The overlays on the Leaflet map, reconciled with `UiState.layers` — the map FOLLOWS the state
    * (it never keeps a copy of its own), and replaying a state never toggles anything twice. */
  private val layerSync = LayerSync(
    attach = l => { val sl = slot(l); sl.attach(map); sl.load(currentSpan) },
    detach = l => { slot(l).detach(map); if l == MapLayer.Boundaries then MapLabels.detach() })

  /** Credit line for a tile source: OSM data always; the HOT style also credits its makers. */
  private[web] def attributionFor(url: String): String =
    val osm = "© OpenStreetMap contributors"
    if url.contains("openstreetmap.fr/hot") then s"$osm · tiles © Humanitarian OSM Team, OSM France"
    else osm

  /** Idempotent: the base map's signal replays its current value on mount. */
  def setTileUrl(url: String, maxZoom: Int): Unit =
    if map != null && tileLayerRef != null && url != tileUrl then
      tileUrl = url
      map.removeLayer(tileLayerRef)
      tileLayerRef = L.tileLayer(url, js.Dynamic.literal(
        attribution = attributionFor(url),
        maxZoom = maxZoom,
        opacity = 0.85
      ).asInstanceOf[js.Object])
      tileLayerRef.addTo(map)

  def setPlaybackMode(on: Boolean): Unit = playbackMode = on

  private[web] def midOf(pt: GeoHit): Option[Double] =
    for a <- pt.periodStart; b <- pt.periodEnd yield (a + b).toDouble / 2

  private[web] def avgMid(items: Iterable[GeoHit]): Option[Double] =
    val (sum, n) = items.foldLeft((0.0, 0)) { case ((s, c), pt) =>
      midOf(pt) match
        case Some(m) => (s + m, c + 1)
        case None    => (s, c)
    }
    if n > 0 then Some(sum / n) else None

  private[web] def colorFor(mid: Option[Double], span: (Long, Long), confidence: Confidence): String =
    val lo = span._1.min(span._2).toDouble
    val hi = span._1.max(span._2).toDouble
    val base = mid match
      case None    => Fmt.color(0)
      case Some(m) => Fmt.color(if hi == lo then 0.5 else (m - lo) / (hi - lo))
    Fmt.desaturate(base, confidence)

  private def worstConf(items: Iterable[GeoHit]): Confidence =
    Fmt.worstConf(items.map(_.confidence.getOrElse(Confidence.High)))

  private[web] def midRange(items: Iterable[GeoHit]): Option[(Double, Double)] = rangeOf(items.flatMap(midOf))

  /** Pure: the least and the greatest of `mids`. */
  private[web] def rangeOf(mids: Iterable[Double]): Option[(Double, Double)] =
    // `var`s: one pass over what may be thousands of mids, during a drag
    var lo = Double.MaxValue
    var hi = Double.MinValue
    for m <- mids do
      if m < lo then lo = m
      if m > hi then hi = m
    if lo <= hi then Some((lo, hi)) else None

  /** The CSS background VALUE for a group of findings: flat when they agree, a gradient across
    * their period midpoints when they do not. */
  private[web] def gradientBg(items: Iterable[GeoHit], span: (Long, Long), confidence: Confidence): String =
    gradientOf(items.flatMap(midOf), span, confidence)

  /** Pure: the same over the midpoints of any dated dots — a mixed bubble's findings, sites, events and
    * pictures alike (the undated have no say). */
  private[web] def gradientOf(mids: Iterable[Double], span: (Long, Long), confidence: Confidence): String =
    rangeOf(mids) match
      case None => colorFor(None, span, confidence)
      case Some((lo, hi)) =>
        val sLo = span._1.min(span._2).toDouble
        val sHi = span._1.max(span._2).toDouble
        def norm(v: Double): Double = if sHi == sLo then 0.5 else (v - sLo) / (sHi - sLo)
        val minC = Fmt.desaturate(Fmt.color(norm(lo)), confidence)
        val maxC = Fmt.desaturate(Fmt.color(norm(hi)), confidence)
        if minC == maxC then minC
        else s"linear-gradient(135deg,$minC,$maxC)"

  private def radiusFor(n: Int): Int =
    if n > 1 then radius + 4 + 3 * n.toString.length else radius

  private def maxDotRadius: Int = radiusFor(999)

  /** A marker. Its look is `.hd-dot` in the stylesheet; only the DATA crosses as custom properties:
    * the diameter, the label size and the background the period ramp gave it. The period-span ring
    * is one of four `ring-N` widths. */
  private def dotIcon(n: Int, background: String, extraClass: String, spanYears: Option[Long],
      says: Option[String] = None): DivIcon =
    val r = radiusFor(n)
    val ringW = if n == 1 && spanYears.exists(_ > 0) then
      math.max(1, math.min(4, math.round(math.log10(spanYears.get.toDouble + 1) * 1.3).toInt))
    else 0
    val d = 2 * r
    L.divIcon(js.Dynamic.literal(
      className = "hd-dot-wrap",
      iconSize = js.Array(d, d),
      iconAnchor = js.Array(r, r),
      html = dotHtml(n, d, background, extraClass, ringW, says)
    ).asInstanceOf[js.Object])

  /** Pure: a bubble's markup — its count, its colour, and (`says`) what it holds, to the pointer and to
    * assistive tech ("12 findings · 3 sites"). */
  private[web] def dotHtml(n: Int, d: Int, background: String, extraClass: String, ringW: Int, says: Option[String]): String =
    val label = if n > 1 then n.toString else ""
    val ring = if ringW > 0 then s" ring-$ringW" else ""
    val words = says.fold("")(w => s""" role="img" title="${Fmt.esc(w)}" aria-label="${Fmt.esc(w)}"""")
    s"""<span class="hd-dot $extraClass$ring" style="--d:${d}px;--fs:${math.min(12, d / 2)}px;--dot-bg:$background"$words>$label</span>"""

  /** Pure: the period-span ring width (0–4) of a single finding spanning `spanYears`. */
  private[web] def ringOf(n: Int, spanYears: Option[Long]): Int =
    if n == 1 && spanYears.exists(_ > 0) then
      math.max(1, math.min(4, math.round(math.log10(spanYears.get.toDouble + 1) * 1.3).toInt))
    else 0

  /** One finding's popup: the source link (identifier label, preview on hover, window on click),
    * title, period, claim and its confidence badge — the same atoms as the server's pages. */
  private[web] def popupHtml(pt: GeoHit): String =
    val conf   = pt.confidence.fold("")(c => " " + ConfidenceBadge.html(c))
    val period = Fmt.periodStr(pt.periodStart, pt.periodEnd)
    CardIcons.html(CardIcons.sourceKind(pt.sourceKind.getOrElse(SourceKinds.Unknown))) +
    s"""${SourceLink.html(pt.citekey, pt.doi, SourceLink.idLabel(pt.citekey, pt.doi))} — ${Fmt.esc(pt.title)}""" +
      s"""<br><i>$period</i><br>${Fmt.esc(pt.claim)}$conf<br>""" +
      s"""<a href="${Fmt.esc(SourceLink.href(pt.citekey))}" target="_blank">new tab ↗</a>"""

  def init(ui: UiConfig): HtmlElement =
    radius = ui.markerRadius
    div(
      idAttr := "map",
      cls := "map-container",
      onMountCallback { ctx =>
        map = L.map("map", js.Dynamic.literal(worldCopyJump = true).asInstanceOf[js.Object])
        // every layer goes into its own pane: a dot stays above a region, whichever came last
        MapPanes.ensure(map)
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
          // a bubble counts every dot in it (a finding's dot counts its findings) — only what is drawn:
          // a layer switched off or filtered leaves the group — and is coloured by all their dates
          iconCreateFunction = { (c: js.Dynamic) =>
            val held = DotCluster.contents(c.getAllChildMarkers().asInstanceOf[js.Array[js.Dynamic]])
            val cls = if DotCluster.mixed(held) then "hd-cluster hd-cluster--mixed" else "hd-cluster"
            dotIcon(held.total, gradientOf(held.mids, currentSpan, held.confidence), cls, None,
              says = Some(DotCluster.summary(held)))
          }: js.Function1[js.Dynamic, DivIcon]
        ).asInstanceOf[js.Object])
        cluster.addTo(map)

        cluster.on("clusterclick", { (e: LeafletEvent) =>
          val members = e.layer.getAllChildMarkers().asInstanceOf[js.Array[js.Dynamic]]
          val held = DotCluster.contents(members)
          val bounds = e.layer.getBounds().asInstanceOf[LatLngBounds]
          def zoomIn(): Unit = map.fitBounds(bounds, js.Dynamic.literal(
            padding = js.Array(40, 40), maxZoom = fitMaxZoom).asInstanceOf[js.Object])
          // too many to list: zoom in, unless the map cannot (dots at one point stay one bubble)
          if held.total > pickerMax && map.getZoom() < map.getMaxZoom() then zoomIn()
          else
            ResultsList.pick(held.findings, s"${DotCluster.summary(held)} in this cluster", Some(() => zoomIn()),
              DotCluster.ordered(DotCluster.others(members)))
        })

        addLegend()
        PlaceHover.install(map)
        PlaceGlyph.followZoom(map)
        MapLabels.install(map, ui)(using ctx.owner)
        // the overlays and the base map follow UiState (restored from the browser's storage by
        // UiState.init, before this mounts) — there is no other copy of that state to disagree with
        UiState.layers.signal.foreach(layerSync(_))(using ctx.owner)
        // the media layer's provider/licence filters reload it, when it is on
        UiState.mediaProvider.signal.combineWith(UiState.mediaLicense.signal).changes
          .foreach(_ => if layerSync.attached(MapLayer.Media) then loadMedia(currentSpan))(using ctx.owner)
        // the events layer (#1449): its kind/tier menus and the view reload it; the key follows the layers
        UiState.eventKind.signal.combineWith(UiState.eventTier.signal).changes
          .foreach(_ => if layerSync.attached(MapLayer.Events) then loadEvents(currentSpan))(using ctx.owner)
        UiState.siteType.signal.changes
          .foreach(_ => if layerSync.attached(MapLayer.Sites) then loadSites(currentSpan))(using ctx.owner)
        map.on("moveend", { (_: LeafletEvent) => if layerSync.attached(MapLayer.Events) then reloadEvents(currentSpan) })
        UiState.layers.signal.foreach(ls => updateKey(ls(MapLayer.Events), ls(MapLayer.Sites)))(using ctx.owner)
        // the finding markers follow their switch the same way; `render` keeps the cluster's content
        // current while it is off, so switching it back on shows the current span at once
        UiState.sources.signal.foreach(showSources)(using ctx.owner)
        UiState.baseMap.signal.foreach(b => setTileUrl(b.url.getOrElse(ui.tiles), ui.tilesMaxZoom))(using ctx.owner)

        map.getContainer().addEventListener("click", { (e: dom.MouseEvent) =>
          val target = e.target.asInstanceOf[dom.Element]
          Option(target.closest("[data-ck]")).foreach { el =>
            if !e.metaKey && !e.ctrlKey && !e.shiftKey then
              e.preventDefault()
              ArticleModal.open(el.getAttribute("data-ck"))
          }
        })
      },
      onMountUnmountCallbackWithState(
        mount = ctx =>
          // A folded timeline or resized sidebar changes this element without a window resize.
          // Leaflet caches its size, so observe the container rather than any one layout toggle.
          val mountedMap = map
          if js.typeOf(js.Dynamic.global.ResizeObserver) == "undefined" then None
          else
            val resize: js.Function1[js.Any, Unit] = _ => { val _ = mountedMap.invalidateSize() }
            val observer = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(resize)
            observer.observe(ctx.thisNode.ref)
            Some(observer),
        unmount = (_, state) => state.flatten.foreach(observer => { val _ = observer.disconnect() })
      )
    )

  /** The findings' switch, as every point layer's: their points join or leave the cluster group, which
    * the other kinds share (so it stays on the map); `render` keeps them current while they are out. */
  private def showSources(on: Boolean): Unit =
    if on then findingsLayer.attach(map) else findingsLayer.detach(map)

  private def addLegend(): Unit =
    val ctrl = L.control(js.Dynamic.literal(position = "bottomright").asInstanceOf[js.Object])
    ctrl.asInstanceOf[js.Dynamic].onAdd = { (_: js.Dynamic) =>
      legendEl = DomUtil.create("div", "hd-legend")
      val ramp = (0 until 10).map(i => s"""<i style="--c:${Fmt.color(i.toDouble / 9)}"></i>""").mkString
      val confPips = Confidence.values.map(c =>
        val wire = c.toString.toLowerCase
        s"""<i style="--c:var(${Palette.confidence(wire)})" title="$wire"></i>""").mkString
      legendEl.innerHTML =
        s"""<div class="hd-ramp">$ramp</div>""" +
        """<div class="hd-ramp-labels"><span class="lo"></span>""" +
        """<span class="mid">older ← → newer</span><span class="hi"></span></div>""" +
        s"""<div class="hd-legend-confidence"><span class="hd-conf-label">certainty</span>$confPips""" +
        """<span class="hd-conf-range">high → contested</span></div><div class="hd-key-slot"></div>"""
      DomEvent.disableClickPropagation(legendEl)
      legendEl
    }: js.Function1[js.Dynamic, dom.Element]
    ctrl.addTo(map)

  /** The legend's key to the icons (#1449), for the layers that draw them. */
  private def updateKey(events: Boolean, sites: Boolean): Unit =
    if legendEl != null then
      Option(legendEl.querySelector(".hd-key-slot")).foreach(_.innerHTML = MapKey.html(events, sites))

  private def loadEvents(span: (Long, Long)): Unit = EventMarkers.load(span, Layout.eventFetchLimit)

  /** A pan or zoom asks for the events in the new view at most once per `Layout.layerReloadMs`. */
  private val reloadEvents = Throttle[(Long, Long)](Layout.layerReloadMs, loadEvents)

  private def updateLegend(span: (Long, Long)): Unit =
    if legendEl == null then ()
    else
      val lo = legendEl.querySelector(".lo")
      val hi = legendEl.querySelector(".hi")
      if lo != null then lo.textContent = Fmt.yr(span._1.min(span._2))
      if hi != null then hi.textContent = Fmt.yr(span._1.max(span._2))

  /** Pure: the native tooltip of a dot holding `items` — "N findings here" where several coincide, none
    * for a single finding (its source's card answers the hover, [[hoverSource]]): never a citekey. */
  private[web] def markerTitle(items: List[GeoHit]): Option[String] =
    Option.when(items.size > 1)(s"${items.size} findings here")

  /** Pure: the source whose card a dot's hover shows — its one finding's; none for coincident findings
    * (the first of several would misdescribe the spot: their list opens on a click). */
  private[web] def hoverSource(items: List[GeoHit]): Option[String] = items match
    case List(one) => Some(one.citekey)
    case _         => None

  /** Pure: a single finding's dot as words, for assistive tech — the source's title and the period. */
  private[web] def markerLabel(pt: GeoHit): String =
    val title = Option(pt.title).map(_.trim).filter(_.nonEmpty).getOrElse(SourceLink.idLabel(pt.citekey, pt.doi))
    val period = Fmt.periodStr(pt.periodStart, pt.periodEnd)
    if period.isEmpty then title else s"$title ($period)"

  /** The findings of `span` (the filter's, a playback frame's): their points, as every kind's. */
  def render(points: List[GeoHit], span: (Long, Long)): Unit =
    currentSpan = span
    if cluster != null then
      updateLegend(span)
      findingsLayer.show(Spot.of(points), span)
      reloadLayers(span)

  /** Bring `m` out of its bubble (zooming in as far as it takes), then `andThen`. */
  private[web] def reveal(m: Marker, andThen: () => Unit): Unit =
    if cluster != null then cluster.zoomToShowLayer(m, () => andThen())

  /** Overlay layers follow the map at most once per `Layout.layerReloadMs`, so a timeline drag
    * (which renders on every pointer move) does not fire a request burst. */
  private val reloadLayers = Throttle[(Long, Long)](Layout.layerReloadMs, { span =>
    layerSync.attached.foreach(l => slot(l).load(span))
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

  private[web] def viridisAt(mid: Double, span: (Long, Long)): String =
    val sLo = span._1.min(span._2).toDouble
    val sHi = span._1.max(span._2).toDouble
    Fmt.color(if sHi == sLo then 0.5 else ((mid - sLo) / (sHi - sLo)).max(0).min(1))

  private def loadOverlay(span: (Long, Long)): Unit =
    val mid = (span._1 + span._2) / 2
    overlayReqId += 1
    val myId = overlayReqId
    placesGeoCached("polity", mid, Layout.overlayFetchLimit).foreach { geojson =>
      if myId == overlayReqId then
        val features = js.JSON.parse(geojson).features.asInstanceOf[js.Array[js.Dynamic]]
        // largest first, so small polities sit on top and stay hoverable
        val sorted = features.toList.sortBy(f => dynPeriod(f.properties).map((a, b) => a - b).getOrElse(99999.0))
        def colorOf(f: js.Dynamic): String =
          viridisAt(dynPeriod(f.properties).map((a, b) => (a + b) / 2).getOrElse(mid.toDouble), span)
        overlayLayer.sync[js.Dynamic](
          sorted,
          key = f => dynStr(f.properties.id).orElse(dynStr(f.properties.label)).getOrElse("?"),
          create = f => polityLayer(f, colorOf(f)),
          update = (lyr, f) =>
            val c = colorOf(f)
            lyr.asInstanceOf[js.Dynamic].setStyle(js.Dynamic.literal(color = c, fillColor = c))
        )
        // every area is named inside itself (MapLabels), in playback and out of it
        if overlayLayer.isAttached then
          MapLabels.attach()
          MapLabels.syncAreas(sorted,
            key = f => dynStr(f.properties.id).orElse(dynStr(f.properties.label)).getOrElse("?"),
            label = f => dynStr(f.properties.label).getOrElse("unnamed"),
            wrapClass = f => placeClasses("hd-map-label-wrap", dynStr(f.properties.basis)))
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /** The classes of a drawn place: `base`, plus `hd-by-findings` for a place the time view shows only
    * because findings dated then are attached to it (an undated place, [[PlaceTimeBasis.Findings]]) —
    * drawn dashed and lighter, so it never reads as a place known to exist then. */
  def placeClasses(base: String, basis: Option[String]): String =
    if basis.flatMap(PlaceTimeBasis.parse).contains(PlaceTimeBasis.Findings) then s"$base $ByFindingsClass" else base

  val ByFindingsClass = "hd-by-findings"

  private def polityLayer(feature: js.Dynamic, color: String): Layer =
    val props = feature.properties
    val label = dynStr(props.label).getOrElse("unnamed")
    val kind  = dynStr(props.kind).getOrElse("polity")
    L.geoJSON(js.Dynamic.literal(`type` = "FeatureCollection", features = js.Array(feature)), js.Dynamic.literal(
      pane = MapPanes.Areas,   // under every dot, however late it is drawn
      // a polity or region with only a point has no boundary yet: its NAME, never a pin or a dot
      pointToLayer = { (_: js.Any, latlng: js.Dynamic) =>
        val m = PlaceGlyph.labelMarker(latlng.lat.asInstanceOf[Double], latlng.lng.asInstanceOf[Double], label, kind,
          placeClasses("hd-area-point-wrap", dynStr(props.basis)), hidden = true)   // the pass fades it in
        MapLabels.trackPoint(m, dynStr(props.id).getOrElse(label))   // thinned with the area names
        m
      }: js.Function2[js.Any, js.Dynamic, Layer],
      style = { (_: js.Dynamic) =>
        js.Dynamic.literal(
          color = color, weight = 1.5, opacity = 0.8,
          fillColor = color, fillOpacity = 0.1, className = placeClasses("hd-polity", dynStr(props.basis))
        )
      }: js.Function1[js.Dynamic, js.Dynamic],
      onEachFeature = { (_: js.Dynamic, leafLayer: js.Dynamic) =>
        // the shape answers like a place: its card on hover, a click chooses it as the place filter
        // and pins the card (PlaceHover); its name is drawn by MapLabels
        dynStr(props.id).foreach(id =>
          PlaceHover.bind(leafLayer.asInstanceOf[Layer], id, label, dynStr(props.kind).getOrElse("polity"), spanMid))
      }: js.Function2[js.Dynamic, js.Dynamic, Unit]
    ).asInstanceOf[js.Object])

  /** The year a place's card reads the gazetteer at: the middle of the span the map shows. */
  private[web] def spanMid(): Option[Long] = Some((currentSpan._1 + currentSpan._2) / 2)

  private def loadMedia(span: (Long, Long)): Unit =
    mediaReqId += 1
    val myId = mediaReqId
    val q = Api.MediaGeoQuery(from = Some(span._1), to = Some(span._2), limit = Some(Layout.mediaFetchLimit),
      provider = UiState.mediaProvider.now(), license = UiState.mediaLicense.now())
    ApiClient.mediaGeo(q).foreach { hits =>
      if myId == mediaReqId then mediaLayer.show(hits, span)
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  private def loadSites(span: (Long, Long)): Unit =
    val mid = (span._1 + span._2) / 2
    sitesReqId += 1
    val myId = sitesReqId
    placesGeoCached("site", mid, Layout.siteFetchLimit).foreach { geojson =>
      if myId == sitesReqId then
        val features = js.JSON.parse(geojson).features.asInstanceOf[js.Array[js.Dynamic]]
        // the site-type menu (#1449) is the descriptor's own filter (PointKinds.Sites.keep)
        sitesLayer.show(features.toList.map(SitePoint.of), span)
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

/** Keeps the overlays attached to a map equal to a WANTED set. Idempotent — applying the same set
  * twice attaches nothing twice — which is what lets the map subscribe to `UiState.layers` and a
  * restored state, a toggle and a replay all go through one path. */
final class LayerSync(attach: MapLayer => Unit, detach: MapLayer => Unit):
  private val on = scala.collection.mutable.Set.empty[MapLayer]

  def attached: Set[MapLayer] = on.toSet

  def apply(wanted: Set[MapLayer]): Unit =
    MapLayer.values.foreach { l =>
      if wanted(l) && !on(l) then { on += l; attach(l) }
      else if !wanted(l) && on(l) then { on -= l; detach(l) }
    }

/** The media layer's popup (#1446): a source's figure names its source; a provider's picture shows
  * its local thumbnail with the credit line and licence its licence requires. Its title leads with the
  * picture icon, as every card leads with its kind's ([[CardIcons]]). Pure. */
object MediaPopup:
  def html(h: MediaGeoHit, period: String, caption: String): String =
    val kind = Fmt.display(h.kind.getOrElse("media"))
    def title(inner: String) = s"""<div class="cite-tip-title media-popup-title">${CardIcons.html(CardIcons.Media)}$inner</div>"""
    h.citekey match
      case Some(ck) =>
        // the source as the app's one source link: its card on hover, its window on a click
        title(s"""${SourceLink.html(ck, None, ck)} / ${Fmt.esc(h.assetName)}""") + s"$kind · $period$caption"
      case None =>
        val img = h.thumbUrl.fold("")(u => s"""<img class="media-popup-thumb" src="${Fmt.esc(u)}" alt="${Fmt.esc(h.assetName)}" loading="lazy"><br>""")
        val credit = h.credit.fold("")(c => s"""<span class="media-credit">${Fmt.esc(c)}</span><br>""")
        val lic = h.license.fold("")(l => s"""<code>${Fmt.esc(l)}</code> · """)
        title(Fmt.esc(h.assetName)) +
          s"""$img$credit$lic${Fmt.esc(MediaProvider.title(h.provider.getOrElse("")))}<br>$kind · $period$caption"""
