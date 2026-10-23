package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

object MapView:

  private var radius: Int                   = Layout.markerRadiusDefault
  private val pickerMax                     = Layout.pickerMax
  private val fitMaxZoom                    = Layout.fitMaxZoom
  private var currentSpan: (Long, Long)     = (0L, 1L)
  private var legendEl: dom.HTMLElement         = uninitialized
  private var overlayReqId: Int             = 0
  // the four kinds of point (docs/map-layers.md): ONE layer implementation, four descriptors, one
  // cluster layer; the events' layer is EventMarkers'
  private val findingsLayer                 = PointLayer(PointKinds.Findings, () => dots, () => spanMid())
  private val mediaLayer                    = PointLayer(PointKinds.Media, () => dots, () => spanMid())
  private var mediaReqId: Int               = 0
  private val sitesLayer                    = PointLayer(PointKinds.Sites, () => dots, () => spanMid())
  private var sitesReqId: Int               = 0
  private var playbackMode: Boolean         = false

  /** The map's ONE cluster layer, for every kind of point ([[PointLayer]]): what is near on screen is one
    * bubble, whatever it is. Regions never join. */
  lazy val dots: PointSet[Dot] = PointSet[Dot](
    paneOf = d => TheMap.pane(d.layer.kind.pane), pane = () => TheMap.pane(MapStack.Pane.Markers),
    surface = () => TheMap.surface, key = _.key, at = _.at,
    radius = () => clusterRadius(maxDotRadius, PointKinds.all.map(_.box)).toDouble,
    single = (d, fadeIn) => d.layer.render(d, fadeIn), bubble = ds => bubbleMarker(ds),
    animateMax = Layout.clusterAnimateMax, exitMs = Layout.exitAnimMs, fadeOut = FadingLayer.fadeOut.value,
    repaint = (m, ds) => repaintBubble(m, ds))

  /** The boundaries layer's shapes, drawn by the map: a polity's colour is the middle of its dates on the
    * period ramp; a place the time view shows only through findings dated then is dashed and fainter. */
  private lazy val polities: ShapeSet = TheMap.surface.shapes("polities",
    ShapeStyle(fillOpacity = 0.1, lineWidth = 1.5, lineOpacity = 0.8, dash = List(3, 2), dashedLineOpacity = Some(0.6), interactive = true))
  // what each drawn polity is (the shape under the pointer answers as this place) and its point labels
  private val polityPlaces = scala.collection.mutable.HashMap.empty[String, (String, String, String)]
  private val pointLabels = scala.collection.mutable.LinkedHashMap.empty[String, DomMarker]
  private var overlayOn = false

  /** An overlay's switch: how it goes on and off the map, and how it loads the span. */
  private final case class Slot(attach: () => Unit, detach: () => Unit, load: ((Long, Long)) => Unit)

  private def slot(l: MapLayer): Slot = l match
    case MapLayer.Boundaries => Slot(() => overlayOn = true, () => clearOverlay(), loadOverlay)
    case MapLayer.Sites      => Slot(sitesLayer.attach, sitesLayer.detach, loadSites)
    case MapLayer.Media      => Slot(mediaLayer.attach, mediaLayer.detach, loadMedia)
    case MapLayer.Events     => Slot(EventMarkers.layer.attach, EventMarkers.layer.detach, loadEvents)

  /** The overlays on the map, reconciled with `UiState.layers` — the map FOLLOWS the state (it never
    * keeps a copy of its own), and replaying a state never toggles anything twice. */
  private val layerSync = LayerSync(
    attach = l => { val sl = slot(l); sl.attach(); sl.load(currentSpan) },
    detach = l => { slot(l).detach(); if l == MapLayer.Boundaries then MapLabels.detach() })

  private def clearOverlay(): Unit =
    overlayOn = false
    overlayReqId += 1
    if TheMap.isUp then
      polities.clear()
      pointLabels.values.foreach(m => TheMap.pane(MapStack.Pane.AreaPoints).remove(m))
    pointLabels.clear()
    polityPlaces.clear()

  /** A popup at a map point (a finding's, a picture's). */
  def popup(lat: Double, lon: Double, html: String): Unit =
    if TheMap.isUp then { val _ = TheMap.surface.popup(LatLon(lat, lon), s"""<div class="hd-popup-content">$html</div>""") }

  /** Playback starts or stops. Its states are drawn from every state fetched while it plays (#3764,
    * [[PlaybackAreas]]); stopped, the boundaries are drawn for the span shown, as before. */
  def setPlaybackMode(on: Boolean): Unit =
    val was = playbackMode
    playbackMode = on
    if was && !on then
      playAreas.clear()
      if overlayOn && TheMap.isUp then loadOverlay(currentSpan)

  // ── playback (#3764): every frame draws from what is held; nothing is fetched or re-made per frame ──

  /** A playback state as drawn: its outline (kept here: the names' placement drops the feature's), its
    * years, its look and the place it is. */
  private final case class PlayArea(feature: js.Dynamic, geometry: js.Any, start: Double, end: Double, dashed: Boolean)
  private val playAreas = scala.collection.mutable.LinkedHashMap.empty[String, PlayArea]
  // `var`s: how far ahead of the playhead the states are fetched, and how long one fades (years); the playhead
  private var playLead: Long = 0L
  private var playFade: Double = 0.0
  private var playhead: Double = 0.0

  /** One playback frame: the findings of the window `span` and the states at playhead `ph`, played at
    * `yearsPerSecond`. The findings are diffed (only the colour of a kept one changes); the states are
    * recoloured and faded in place. */
  def playFrame(points: List[GeoHit], span: (Long, Long), ph: Long, yearsPerSecond: Double): Unit =
    playLead = (yearsPerSecond * Layout.playbackLeadSeconds).toLong
    playFade = math.max(1.0, yearsPerSecond * Layout.playbackAreaFadeSeconds)
    playhead = ph.toDouble
    render(points, span)
    if TheMap.isUp && overlayOn then restylePlayAreas(span)

  private def restylePlayAreas(span: (Long, Long)): Unit =
    playAreas.foreach { (k, a) =>
      val alpha = PlaybackAreas.step(PlaybackAreas.alpha(playhead, a.start, a.end, playFade))
      polities.restyle(k, viridisAt((a.start + a.end) / 2, span), if a.dashed then alpha * 0.6 else alpha)
    }

  /** A year's states fetched in playback: added to those held (the ones over for longer than a fade let
    * go), the shapes sent to the map only when that changed what is held. */
  private def addPlayAreas(features: js.Array[js.Dynamic], span: (Long, Long), keyOf: js.Dynamic => String): Unit =
    def byFindings(f: js.Dynamic) = dynStr(f.properties.basis).flatMap(PlaceTimeBasis.parse).contains(PlaceTimeBasis.Findings)
    val before = playAreas.keySet.toSet
    playAreas.filterInPlace { (k, a) =>
      val keep = PlaybackAreas.live(playhead, a.end, playFade)
      if !keep then polityPlaces.remove(k)
      keep
    }
    features.foreach { f =>
      val g = f.geometry
      val isArea = !js.isUndefined(g) && g != null && g.selectDynamic("type").toString != "Point" && g.selectDynamic("type").toString != "MultiPoint"
      dynPeriod(f.properties).foreach { (a, b) =>
        val k = PlaybackAreas.key(keyOf(f), Some(a), Some(b))
        if isArea && !playAreas.contains(k) && PlaybackAreas.live(playhead, b, playFade) then
          playAreas(k) = PlayArea(f, g, a, b, byFindings(f))
          dynStr(f.properties.id).foreach(id =>
            polityPlaces(k) = (id, dynStr(f.properties.label).getOrElse("unnamed"), dynStr(f.properties.kind).getOrElse("polity")))
      }
    }
    if playAreas.keySet != before then
      polities.set(playAreas.toSeq.map { (k, a) =>
        val alpha = PlaybackAreas.step(PlaybackAreas.alpha(playhead, a.start, a.end, playFade))
        ShapeFeature(k, a.geometry, viridisAt((a.start + a.end) / 2, span), dashed = a.dashed, alpha = if a.dashed then alpha * 0.6 else alpha)
      })

  private[web] def midOf(pt: GeoHit): Option[Double] =
    for a <- pt.periodStart; b <- pt.periodEnd yield (a + b).toDouble / 2

  private[web] def avgMid(items: Iterable[GeoHit]): Option[Double] =
    val (sum, n) = items.foldLeft((0.0, 0)) { case ((s, c), pt) =>
      midOf(pt) match
        case Some(m) => (s + m, c + 1)
        case None    => (s, c)
    }
    if n > 0 then Some(sum / n) else None

  private[web] def colorFor(mid: Option[Double], span: (Long, Long), certainty: Certainty): String =
    val lo = span._1.min(span._2).toDouble
    val hi = span._1.max(span._2).toDouble
    val base = mid match
      case None    => Fmt.color(0)
      case Some(m) => Fmt.color(if hi == lo then 0.5 else (m - lo) / (hi - lo))
    Certainty.desaturate(base, certainty)

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
  private[web] def gradientBg(items: Iterable[GeoHit], span: (Long, Long), certainty: Certainty): String =
    gradientOf(items.flatMap(midOf), span, certainty)

  /** Pure: the same over the midpoints of any dated dots — a mixed bubble's findings, sites, events and
    * pictures alike (the undated have no say). */
  private[web] def gradientOf(mids: Iterable[Double], span: (Long, Long), certainty: Certainty): String =
    rampEnds(mids, span, certainty) match
      case (a, b) if a == b => a
      case (a, b)           => s"linear-gradient(135deg,$a,$b)"

  /** Pure: the colours of the oldest and the newest of `mids` on the ramp (desaturated by `certainty`);
    * the undated colour twice when none is dated. */
  private[web] def rampEnds(mids: Iterable[Double], span: (Long, Long), certainty: Certainty): (String, String) =
    rangeOf(mids) match
      case None =>
        val c = colorFor(None, span, certainty)
        (c, c)
      case Some((lo, hi)) =>
        val sLo = span._1.min(span._2).toDouble
        val sHi = span._1.max(span._2).toDouble
        def norm(v: Double): Double = if sHi == sLo then 0.5 else (v - sLo) / (sHi - sLo)
        (Certainty.desaturate(Fmt.color(norm(lo)), certainty), Certainty.desaturate(Fmt.color(norm(hi)), certainty))

  private def radiusFor(n: Int): Int =
    if n > 1 then radius + 4 + 3 * n.toString.length else radius

  private def maxDotRadius: Int = radiusFor(999)

  /** Pure: how near (px) two points' centres are when they join a bubble — the widest bubble's diameter
    * (`maxDot` its radius), or the largest marker box if that is wider: two markers drawn apart never
    * overlap. */
  private[web] def clusterRadius(maxDot: Int, boxes: Iterable[(Int, Int)]): Int =
    math.max(2 * maxDot, boxes.map((w, h) => math.max(w, h)).maxOption.getOrElse(0))

  // `var`: the next bubble's gradient id (unique on the page)
  private var bubbleSeq = 0

  /** Pure: the period-span ring width (0–4) of a single finding spanning `spanYears`. */
  private[web] def ringOf(n: Int, spanYears: Option[Long]): Int =
    if n == 1 && spanYears.exists(_ > 0) then
      math.max(1, math.min(4, math.round(math.log10(spanYears.get.toDouble + 1) * 1.3).toInt))
    else 0

  /** One finding's popup: the source link (identifier label, preview on hover, window on click),
    * title, period, claim and its confidence badge — the same atoms as the server's pages. */
  private[web] def popupHtml(pt: GeoHit): String =
    // a finding that states no confidence says so — it is not drawn as high
    val conf   = pt.confidence.fold(s""" <span class="hd-conf">confidence ${Certainty.UnstatedLabel}</span>""")(c =>
      " " + ConfidenceBadge.html(c))
    val period = Fmt.periodStr(pt.periodStart, pt.periodEnd)
    CardIcons.html(CardIcons.sourceKind(pt.sourceKind.getOrElse(SourceKinds.Unknown))) +
    s"""${SourceLink.html(pt.citekey, pt.doi, SourceLink.idLabel(pt.citekey, pt.doi))} — ${Fmt.esc(pt.title)}""" +
      s"""<br><i>$period</i><br>${Fmt.esc(pt.claim)}$conf<br>""" +
      s"""<a href="${Fmt.esc(SourceLink.href(pt.citekey))}" target="_blank">new tab ↗</a>"""

  /** A bubble's marker: its count, colours and outline, what it holds; a click lists them (or zooms in). */
  private def bubbleMarker(ds: Seq[Dot]): DomMarker =
    val held = DotCluster.contents(ds)
    val n = held.total
    val d = 2 * radiusFor(n)
    bubbleSeq += 1
    val extra = List(if DotCluster.mixed(held) then "hd-cluster--mixed" else "", Certainty.markerClass(held.certainty))
      .filter(_.nonEmpty).mkString(" ")
    val el = DomMarker.element("hd-dot-wrap", DotCluster.bubbleHtml(n, d, rampEnds(held.mids, currentSpan, held.certainty),
      s"hdb$bubbleSeq", extra, Some(DotCluster.summary(held)), DotCluster.form(held)))
    val lat = ds.map(_.at.lat).sum / ds.size
    val lon = ds.map(_.at.lon).sum / ds.size
    val m = DomMarker(lat, lon, el, Some((d, d)))
    m.handlers = new MarkerHandlers:
      override def click(e: dom.MouseEvent): Unit =
        e.stopPropagation()
        open(ds)
    m

  /** A drawn bubble's colours set again in place (its members' colours moved with the span). */
  private def repaintBubble(m: DomMarker, ds: Seq[Dot]): Unit =
    val held = DotCluster.contents(ds)
    val (a, b) = rampEnds(held.mids, currentSpan, held.certainty)
    Option(m.el.querySelector(".hd-cluster")).map(_.asInstanceOf[dom.html.Element]).foreach { c =>
      c.style.setProperty("--g0", a)
      c.style.setProperty("--g1", b)
    }

  /** A bubble chosen: its list, or (too many to list) the map zoomed in on it. */
  private def open(ds: Seq[Dot]): Unit =
      val all = DotCluster.contents(ds)
      val pts = ds.map(_.at)
      val b = GeoBounds(pts.map(_.lat).min, pts.map(_.lon).min, pts.map(_.lat).max, pts.map(_.lon).max)
      def zoomIn(): Unit = TheMap.surface.fitBounds(b, 40, fitMaxZoom)
      // too many to list: zoom in, unless the map cannot (dots at one point stay one bubble)
      if all.total > pickerMax && TheMap.surface.zoom < TheMap.surface.maxZoom - 0.01 then zoomIn()
      else ResultsList.pick(all.findings, s"${DotCluster.summary(all)} in this cluster", Some(() => zoomIn()),
        DotCluster.ordered(DotCluster.others(ds)))

  def init(ui: UiConfig): HtmlElement =
    radius = ui.markerRadius
    BaseMaps.configure(ui)
    // the span the page opens on (UiState is restored before this mounts): the overlays switched on
    // from the browser's storage load at once, and loaded the placeholder span (0, 1) — a wasted request
    // and a wrong first drawing — until the first findings arrived
    currentSpan = (UiState.from.now().getOrElse(ui.time.defaultFrom), UiState.to.now().getOrElse(ui.time.defaultTo))
    div(
      idAttr := "map",
      cls := "map-container",
      onMountCallback { ctx =>
        // without a GPU the map is drawn at one pixel per CSS pixel: a quarter of the work on a 2× screen;
        // without WebGL 2 it is not drawn at all, and its place says why (NoMapSurface)
        val surface = Engines.surface(ctx.thisNode.ref, ui.tilesMaxZoom.toDouble, caps => Option.when(caps.software)(1.0))
        TheMap.install(surface)
        surface.setView(LatLon(37.0, 30.0), 5)
        PlaceHover.shapes("polities", PlaceHover.Places(polityPlaces.get, spanMid))
        addLegend(surface)
        PlaceHover.install(surface)
        PlaceGlyph.followZoom(surface)
        MapLabels.install(surface, ui)(using ctx.owner)
        BaseMaps.install(surface, ui)(using ctx.owner)
        MapHandle.install(surface)
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
        // the "States shown" menu: the boundaries, and the sites' year for a snapshot
        UiState.areaPick.signal.changes.foreach { _ =>
          if layerSync.attached(MapLayer.Boundaries) then loadOverlay(currentSpan)
          if layerSync.attached(MapLayer.Sites) then loadSites(currentSpan)
        }(using ctx.owner)
        surface.on(MapEventKind.MoveEnd)(_ => if layerSync.attached(MapLayer.Events) then reloadEvents(currentSpan))
        UiState.layers.signal.foreach(ls => updateKey(ls(MapLayer.Events), ls(MapLayer.Sites)))(using ctx.owner)
        // the finding markers follow their switch the same way; `render` keeps their content current
        // while they are off, so switching them back on shows the current span at once
        UiState.sources.signal.foreach(showSources)(using ctx.owner)
        surface.container.addEventListener("click", { (e: dom.MouseEvent) =>
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
          // A folded timeline or resized sidebar changes this element without a window resize: the
          // map caches its size, so observe the container rather than any one layout toggle.
          if js.typeOf(js.Dynamic.global.ResizeObserver) == "undefined" then None
          else
            val resize: js.Function1[js.Any, Unit] = _ => if TheMap.isUp then TheMap.surface.resize()
            val observer = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(resize)
            observer.observe(ctx.thisNode.ref)
            Some(observer),
        unmount = (_, state) => state.flatten.foreach(observer => { val _ = observer.disconnect() })
      )
    )

  /** The findings' switch, as every point layer's: their points join or leave the cluster group, which
    * the other kinds share (so it stays on the map); `render` keeps them current while they are out. */
  private def showSources(on: Boolean): Unit =
    if on then findingsLayer.attach() else findingsLayer.detach()

  private def addLegend(surface: MapSurface): Unit =
    legendEl = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    legendEl.className = "hd-legend hd-map-control"
    val ramp = (0 until 10).map(i => s"""<i style="--c:${Fmt.color(i.toDouble / 9)}"></i>""").mkString
    val confPips = Confidence.values.map(c =>
      val wire = c.toString.toLowerCase
      s"""<i style="--c:var(${Palette.confidence(wire)})" title="$wire"></i>""").mkString
    // a finding with no confidence: its own pip, apart from the scale (it is not a level of it)
    val unstated = Certainty.UnstatedLabel
    val unstatedPip =
      s"""<i class="is-unstated" style="--c:var(--conf-unstated)" title="$unstated"></i>""" +
        s"""<span class="hd-conf-range">$unstated</span>"""
    legendEl.innerHTML =
      s"""<div class="hd-ramp">$ramp</div>""" +
      """<div class="hd-ramp-labels"><span class="lo"></span>""" +
      """<span class="mid">older ← → newer</span><span class="hi"></span></div>""" +
      s"""<div class="hd-legend-confidence"><span class="hd-conf-label">certainty</span>$confPips""" +
      """<span class="hd-conf-range">high → contested</span>""" +
      s"""$unstatedPip</div><div class="hd-key-slot"></div>"""
    val corner = surface.corner(Corner.BottomRight)
    corner.insertBefore(legendEl, corner.firstChild)

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
    if TheMap.isUp then
      updateLegend(span)
      findingsLayer.show(Spot.of(points), span)
      reloadLayers(span)
      if layerSync.attached.exists(waitsForQuiet(_, UiState.areaPick.now(), playbackMode)) then reloadRangeAreas(span)

  /** Bring the point `key` out of its bubble (zooming in as far as it takes), then `andThen`. */
  private[web] def reveal(key: String, andThen: () => Unit): Unit =
    if TheMap.isUp then dots.zoomToShow(key, andThen)

  /** Overlay layers follow the map at most once per `Layout.layerReloadMs`, so a timeline drag
    * (which renders on every pointer move) does not fire a request burst — except the one that waits
    * for the span to settle ([[waitsForQuiet]]). */
  private val reloadLayers = Throttle[(Long, Long)](Layout.layerReloadMs, { span =>
    layerSync.attached.foreach(l => if !waitsForQuiet(l, UiState.areaPick.now(), playbackMode) then slot(l).load(span))
  })

  /** The whole-range boundaries, once the span has stopped moving for `Layout.rangeReloadMs`. */
  private val reloadRangeAreas = Debounce[(Long, Long)](Layout.rangeReloadMs, span =>
    if layerSync.attached(MapLayer.Boundaries) then loadOverlay(span))

  /** Pure: does `layer` wait until the span settles instead of following a drag (#1949)? The boundaries
    * over a whole range ("All states in period") do: every span is a new answer of megabytes (1 600
    * shapes, ~7 MB), which a throttled drag fetched, parsed and drew five times a second — 300 ms frames
    * and a request queue. A snapshot year and playback follow the drag as before. */
  private[web] def waitsForQuiet(layer: MapLayer, pick: GeoPick, playback: Boolean): Boolean =
    layer == MapLayer.Boundaries && !pick.snapshot && !playback

  /** Place GeoJSON by (kind, time): a drag back and forth over the same years is served locally. The
    * time is what the "States shown" menu made of the span ([[areaTime]]), so a snapshot at the same
    * year is one entry whichever choice led to it, and a whole-range answer is its own. Bounded by
    * count AND by size (#1949): a whole range's answer is megabytes, and 48 of them were kept. */
  private val geoCache = BoundedCache[(String, GeoTime), scala.concurrent.Future[String]](
    Layout.geoCacheSize, Layout.geoCacheChars)

  /** The place-GeoJSON cache's figures, for [[Diagnostics]]. */
  def geoCacheStats: BoundedCache.Stats = BoundedCache.stats(geoCache)

  /** Pure: the query of a layer of `kind` drawn for `time`. */
  private[web] def geoQuery(kind: String, time: GeoTime, limit: Int): Api.PlacesGeoQuery =
    val q = Api.PlacesGeoQuery(kind = Some(kind), limit = Some(limit))
    time match
      case GeoTime.At(y)                 => q.copy(asOf = Some(y))
      case GeoTime.Within(lo, hi, latest) =>
        q.copy(from = Some(lo), to = Some(hi), pick = Some((if latest then GeoPick.Latest else GeoPick.Earliest).wire))
      case GeoTime.Always                => q

  /** Pure: the time the boundaries layer is drawn for over `span`, as `pick` reads it; playback
    * always draws the playhead's one year (the middle of its window). */
  private[web] def areaTime(span: (Long, Long), pick: GeoPick, playback: Boolean): GeoTime =
    GeoPick.resolve(None, Some(span._1), Some(span._2), if playback then GeoPick.Mid else pick)

  /** Pure: the year the sites layer is drawn at: a snapshot pick's year, else (a whole range, or
    * playback) the middle — sites over a range are not built. */
  private[web] def siteYear(span: (Long, Long), pick: GeoPick, playback: Boolean): Long =
    val lo = span._1.min(span._2)
    val hi = span._1.max(span._2)
    GeoPick.yearOf(if playback || !pick.snapshot then GeoPick.Mid else pick, lo, hi)

  /** Pure: how many areas the boundaries layer asks for at `time` — a year's few hundred at most, or
    * every state of a whole range. */
  private[web] def overlayLimit(time: GeoTime): Int = time match
    case _: GeoTime.Within => Layout.overlayRangeFetchLimit
    case _                 => Layout.overlayFetchLimit

  private def placesGeoCached(kind: String, time: GeoTime, limit: Int): scala.concurrent.Future[String] =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    val k = (kind, time)
    geoCache.get(k) match
      case Some(f) => f
      case None =>
        val f = ApiClient.placesGeo(geoQuery(kind, time, limit))
        geoCache.put(k, f)
        // weighed once it is in: its length in characters (the size of the string kept)
        f.onComplete {
          case scala.util.Success(s) => if geoCache.peek(k).contains(f) then geoCache.reweigh(k, s.length.toLong)
          case scala.util.Failure(_) => if geoCache.peek(k).contains(f) then { val _ = geoCache.remove(k) }
        }
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
    val time0 = areaTime(span, UiState.areaPick.now(), playbackMode)
    // in playback the year ahead of the playhead (the states it reaches next are held before it does)
    val time = time0 match
      case GeoTime.At(y) if playbackMode && playAreas.nonEmpty => GeoTime.At(y + playLead)
      case t => t
    val playing = playbackMode
    placesGeoCached("polity", time, overlayLimit(time)).foreach { geojson =>
      if myId == overlayReqId && overlayOn && TheMap.isUp && playing == playbackMode then
        val features = js.JSON.parse(geojson).features.asInstanceOf[js.Array[js.Dynamic]]
        if playing then
          def keyOf(f: js.Dynamic) = dynStr(f.properties.id).orElse(dynStr(f.properties.label)).getOrElse("?")
          addPlayAreas(features, currentSpan, keyOf)
          val shown = playAreas.toSeq.filter((_, a) => PlaybackAreas.alpha(playhead, a.start, a.end, playFade) > 0).map(_._2.feature)
          val (points, _) = features.toList.partition { f =>
            val g = f.geometry
            !js.isUndefined(g) && g != null && (g.selectDynamic("type").toString == "Point" || g.selectDynamic("type").toString == "MultiPoint")
          }
          syncPointLabels(points, keyOf)
          MapLabels.attach()
          MapLabels.syncAreas(shown,
            key = f => PlaybackAreas.key(keyOf(f), dynPeriod(f.properties).map(_._1), dynPeriod(f.properties).map(_._2)),
            label = f => dynStr(f.properties.label).getOrElse("unnamed"),
            wrapClass = f => placeClasses("hd-map-label-wrap", dynStr(f.properties.basis)),
            place = f => polityPlaces.get(PlaybackAreas.key(keyOf(f), dynPeriod(f.properties).map(_._1), dynPeriod(f.properties).map(_._2))),
            asOf = () => spanMid())
        else
          // largest first, so small polities sit on top and stay hoverable
          val sorted = features.toList.sortBy(f => dynPeriod(f.properties).map((a, b) => a - b).getOrElse(99999.0))
          def keyOf(f: js.Dynamic) = dynStr(f.properties.id).orElse(dynStr(f.properties.label)).getOrElse("?")
          def colorOf(f: js.Dynamic): String =
            viridisAt(dynPeriod(f.properties).map((a, b) => (a + b) / 2).getOrElse(mid.toDouble), span)
          def byFindings(f: js.Dynamic) = dynStr(f.properties.basis).flatMap(PlaceTimeBasis.parse).contains(PlaceTimeBasis.Findings)
          def isPoint(f: js.Dynamic) =
            val g = f.geometry
            !js.isUndefined(g) && g != null && (g.selectDynamic("type").toString == "Point" || g.selectDynamic("type").toString == "MultiPoint")
          val (points, areas) = sorted.partition(isPoint)
          polityPlaces.clear()
          sorted.foreach(f => dynStr(f.properties.id).foreach(id =>
            polityPlaces(keyOf(f)) = (id, dynStr(f.properties.label).getOrElse("unnamed"), dynStr(f.properties.kind).getOrElse("polity"))))
          polities.set(areas.map(f => ShapeFeature(keyOf(f), f.geometry, colorOf(f), dashed = byFindings(f), alpha = if byFindings(f) then 0.6 else 1.0)))
          syncPointLabels(points, keyOf)
          // every area is named inside itself (MapLabels), in playback and out of it
          MapLabels.attach()
          MapLabels.syncAreas(areas,
            key = keyOf,
            label = f => dynStr(f.properties.label).getOrElse("unnamed"),
            wrapClass = f => placeClasses("hd-map-label-wrap", dynStr(f.properties.basis)),
            place = f => polityPlaces.get(keyOf(f)),
            asOf = () => spanMid(),
            // once its name is placed nothing here reads the outline: the map holds its own copy
            release = dropGeometry)
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /** A polity or region with only a point has no boundary yet: its NAME, never a pin or a dot. */
  private def syncPointLabels(points: List[js.Dynamic], keyOf: js.Dynamic => String): Unit =
    val pane = TheMap.pane(MapStack.Pane.AreaPoints)
    val want = points.map(f => keyOf(f) -> f).toMap
    pointLabels.filterInPlace { (k, m) =>
      val keep = want.contains(k)
      if !keep then pane.remove(m)
      keep
    }
    points.foreach { f =>
      val k = keyOf(f)
      if !pointLabels.contains(k) then
        val c = f.geometry.coordinates
        val (lon, lat) =
          if f.geometry.selectDynamic("type").toString == "Point" then (c.selectDynamic("0").asInstanceOf[Double], c.selectDynamic("1").asInstanceOf[Double])
          else (c.selectDynamic("0").selectDynamic("0").asInstanceOf[Double], c.selectDynamic("0").selectDynamic("1").asInstanceOf[Double])
        val p = f.properties
        val label = dynStr(p.label).getOrElse("unnamed")
        val kind = dynStr(p.kind).getOrElse("polity")
        val m = PlaceGlyph.labelMarker(lat, lon, label, kind, placeClasses("hd-area-point-wrap", dynStr(p.basis)), hidden = true)
        dynStr(p.id).foreach(id => PlaceHover.bind(m, id, label, kind, spanMid))
        MapLabels.trackPoint(m, dynStr(p.id).getOrElse(label))   // thinned with the area names
        pointLabels(k) = m
        pane.add(m)
    }

  /** Forget a drawn feature's outline (its layer has its own copy); its point is kept (a label's). */
  private def dropGeometry(f: js.Dynamic): Unit =
    val g = f.geometry
    if !js.isUndefined(g) && g != null && g.selectDynamic("type").toString != "Point" then
      f.updateDynamic("geometry")(null)

  /** The classes of a drawn place: `base`, plus `hd-by-findings` for a place the time view shows only
    * because findings dated then are attached to it (an undated place, [[PlaceTimeBasis.Findings]]) —
    * drawn dashed and lighter, so it never reads as a place known to exist then. */
  def placeClasses(base: String, basis: Option[String]): String =
    if basis.flatMap(PlaceTimeBasis.parse).contains(PlaceTimeBasis.Findings) then s"$base $ByFindingsClass" else base

  val ByFindingsClass = "hd-by-findings"

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
    sitesReqId += 1
    val myId = sitesReqId
    val year = siteYear(span, UiState.areaPick.now(), playbackMode)
    placesGeoCached("site", GeoTime.At(year), Layout.siteFetchLimit).foreach { geojson =>
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
