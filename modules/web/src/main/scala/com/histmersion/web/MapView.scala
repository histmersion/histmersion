package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, GeoPick, MapStack, Palette, Years}
import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.api.library.Confidence
import com.histmersion.api.catalogue.{GeoTime, MapTilesUiConfig, Period, TileLayer, TilesApi}
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.library.{Filter, GeoHit}
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Source

object MapView:
  type ClassName = ClassName.T
  object ClassName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Color = Color.T
  object Color extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HoldsCell = HoldsCell.T
  object HoldsCell extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Html = Html.T
  object Html extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mid = DotCluster.Mid
  val Mid: DotCluster.Mid.type = DotCluster.Mid
  type Playback = Playback.T
  object Playback extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ring = Ring.T
  object Ring extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowSources = ShowSources.T
  object ShowSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceLabel = SourceLabel.T
  object SourceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearsPerSecond = PlaybackBuckets.YearsPerSecond
  val YearsPerSecond: PlaybackBuckets.YearsPerSecond.type = PlaybackBuckets.YearsPerSecond



  private var radius: Int = Layout.markerRadiusDefault.value
  private val pickerMax = Layout.pickerMax
  private val fitMaxZoom = Layout.fitMaxZoom
  private var currentSpan: (Years.Astronomical, Years.Astronomical) = (Years.Astronomical(0L), Years.Astronomical(1L))
  private var legendEl: dom.HTMLElement = uninitialized
  private var overlayReqId: Int = 0
  // the four kinds of point (docs/map-layers.md): ONE layer implementation, four descriptors, one
  // cluster layer; the events' layer is EventMarkers'
  private val findingsLayer = PointLayer(PointKinds.Findings, () => dots, () => spanMid())
  // the sites and the pictures: every one of the span, read from the server's tiles (docs/map-layers.md)
  private[web] val sitesLayer = TiledPoints(
    TileLayer.Sites,
    PointLayer(PointKinds.Sites, () => dots, () => spanMid()),
    DotKind.Site,
    TileDecode.site,
    () => tilesConfig.maxZoom
  )
  private[web] val mediaLayer = TiledPoints(
    TileLayer.Media,
    PointLayer(PointKinds.Media, () => dots, () => spanMid()),
    DotKind.Media,
    TileDecode.media,
    () => tilesConfig.maxZoom
  )
  private var playbackMode: com.histmersion.web.MapView.Playback = com.histmersion.web.MapView.Playback(false)
  // `var`s: the tiles' settings (from the page's config), and playback's speed (its time buckets)
  private[web] var tilesConfig: MapTilesUiConfig = MapTilesUiConfig()
  private var playYearsPerSecond: com.histmersion.web.MapView.YearsPerSecond = com.histmersion.web.MapView.YearsPerSecond(0.0)

  /** The map's ONE cluster layer, for every kind of point ([[PointLayer]]): what is near on screen is one bubble,
    * whatever it is. Regions never join.
    */
  lazy val dots: PointSet[Dot] = PointSet[Dot](
    paneOf = d => TheMap.pane(d.layer.kind.pane),
    pane = () => TheMap.pane(MapStack.Pane.Markers),
    surface = () => TheMap.surface,
    key = d => PointSet.Key(d.key.value),
    at = _.at,
    radius = () => Clustering.GroupRadius(clusterRadius(maxDotRadius, PointKinds.all.map(_.box)).toDouble),
    single = (d, fadeIn) => if d.member.aggregate then bubbleMarker(Seq(d)) else d.layer.render(d, PointLayer.FadeIn(fadeIn.value)),
    bubble = ds => bubbleMarker(ds),
    animateMax = PointSet.AnimateMax(Layout.clusterAnimateMax.value),
    exitMs = PointSet.ExitMs(Layout.exitAnimMs.value),
    fadeOut = PointSet.FadeOut(FadingLayer.fadeOut.value),
    repaint = (m, ds) => repaintBubble(m, ds)
  )

  /** The boundaries layer's shapes, drawn by the map: a polity's colour is the middle of its dates on the period ramp;
    * a place the time view shows only through findings dated then is dashed and fainter.
    */
  private lazy val polities: ShapeSet = TheMap.surface.shapes(
    com.histmersion.web.map.MapSurface.ShapesId("polities"),
    ShapeStyle(
      fillOpacity = ShapeStyle.FillOpacity(0.1),
      lineWidth = ShapeStyle.LineWidth(1.5),
      lineOpacity = ShapeStyle.LineOpacity(0.8),
      dash = List(com.histmersion.web.map.ShapeStyle.Dash(3), com.histmersion.web.map.ShapeStyle.Dash(2)),
      dashedLineOpacity = Some(ShapeStyle.DashedLineOpacity(0.6)),
      interactive = ShapeStyle.Interactive(true)
    )
  )
  // what each drawn polity is (the shape under the pointer answers as this place) and its point labels
  private val polityPlaces = scala.collection.mutable.HashMap.empty[PlaceHover.PlaceId, (PlaceHover.PlaceId, com.histmersion.api.UiLabels.Text, PlaceHover.PlaceKind)]
  private val pointLabels = scala.collection.mutable.LinkedHashMap.empty[String, DomMarker]
  private var overlayOn = false

  /** An overlay's switch: how it goes on and off the map, and how it loads the span. */
  final private case class Slot(attach: () => Unit, detach: () => Unit, load: ((Years.Astronomical, Years.Astronomical)) => Unit)

  private def slot(l: MapLayer): Slot = l match
    case MapLayer.Boundaries => Slot(() => overlayOn = true, () => clearOverlay(), loadOverlay)
    case MapLayer.Sites      => Slot(sitesLayer.attach, sitesLayer.detach, loadSites)
    case MapLayer.Media      => Slot(mediaLayer.attach, mediaLayer.detach, loadMedia)
    case MapLayer.Events     => Slot(EventMarkers.layer.attach, EventMarkers.layer.detach, loadEvents)
    case MapLayer.Notes      => Slot(marks.NoteMarkers.attach, marks.NoteMarkers.detach, _ => ())

  /** The overlays on the map, reconciled with `UiState.layers` — the map FOLLOWS the state (it never keeps a copy of
    * its own), and replaying a state never toggles anything twice.
    */
  private val layerSync = LayerSync(
    attach = l => { val sl = slot(l); sl.attach(); sl.load(currentSpan) },
    detach = l => { slot(l).detach(); if l == MapLayer.Boundaries then MapLabels.detach() }
  )

  private def clearOverlay(): Unit =
    overlayOn = false
    overlayReqId += 1
    if TheMap.isUp then
      polities.clear()
      pointLabels.values.foreach(m => TheMap.pane(MapStack.Pane.AreaPoints).remove(m))
    pointLabels.clear()
    polityPlaces.clear()

  /** A popup at a map point (a finding's, a picture's). */
  def popup(lat: com.histmersion.api.catalogue.Latitude, lon: com.histmersion.api.catalogue.Longitude, html: com.histmersion.web.MapView.Html): Unit =
    if TheMap.isUp then {
      val _ = TheMap.surface.popup(LatLon(LatLon.Lat(lat.value), LatLon.Lon(lon.value)), MapSurface.PopupHtml(s"""<div class="hd-popup-content">$html</div>"""))
    }

  /** Playback starts or stops. Its states are drawn from every state fetched while it plays (#3764, [[PlaybackAreas]]).
    * Stopped, the map is drawn for the span shown again (the caller's [[App.restoreSpan]]: the findings, the legend,
    * the tiles and the boundaries of the whole span) — this alone only lets go of playback's states.
    */
  def setPlaybackMode(on: com.histmersion.web.MapView.Playback): Unit =
    val was = playbackMode
    playbackMode = on
    if was.value && !on.value then playAreas.clear()

  // ── playback (#3764): every frame draws from what is held; nothing is fetched or re-made per frame ──

  /** A playback state as drawn: its outline (kept here: the names' placement drops the feature's), its years, its look
    * and the place it is.
    */
  final private case class PlayArea(feature: js.Dynamic, geometry: js.Any, start: PlaybackAreas.FractionalYear, end: PlaybackAreas.FractionalYear, dashed: Boolean)

  private val playAreas = scala.collection.mutable.LinkedHashMap.empty[PlaybackAreas.AreaKey, PlayArea]
  // `var`s: how far ahead of the playhead the states are fetched, and how long one fades (years); the playhead
  private var playLead: com.histmersion.api.catalogue.Year = com.histmersion.api.catalogue.Year(0L)
  private var playFade: PlaybackAreas.FadeYears = PlaybackAreas.FadeYears(0.0)
  private var playhead: PlaybackAreas.FractionalYear = PlaybackAreas.FractionalYear(0.0)

  /** One playback frame: the findings of the window `span` and the states at playhead `ph`, played at `yearsPerSecond`.
    * The findings are diffed (only the colour of a kept one changes); the states are recoloured and faded in place.
    */
  def playFrame(points: List[GeoHit], span: (Years.Astronomical, Years.Astronomical), ph: com.histmersion.api.catalogue.Year, yearsPerSecond: com.histmersion.web.MapView.YearsPerSecond): Unit =
    playLead = com.histmersion.api.catalogue.Year((yearsPerSecond.value * Layout.playbackLeadSeconds.value).toLong)
    playYearsPerSecond = yearsPerSecond
    playFade = PlaybackAreas.FadeYears(math.max(1.0, yearsPerSecond.value * Layout.playbackAreaFadeSeconds.value))
    playhead = PlaybackAreas.FractionalYear(ph.value.toDouble)
    render(points, span)
    if TheMap.isUp && overlayOn then restylePlayAreas(span)

  private def restylePlayAreas(span: (Years.Astronomical, Years.Astronomical)): Unit =
    playAreas.foreach { (k, a) =>
      val alpha = PlaybackAreas.step(PlaybackAreas.alpha(playhead, a.start, a.end, playFade)).value
      polities.restyle(
        ShapeFeature.Id(k.value),
        ShapeFeature.Colour(viridisAt(Mid((a.start.value + a.end.value) / 2), span).value),
        ShapeFeature.Alpha(if a.dashed then alpha * 0.6 else alpha)
      )
    }

  /** A year's states fetched in playback: added to those held (the ones over for longer than a fade let go), the shapes
    * sent to the map only when that changed what is held.
    */
  private def addPlayAreas(features: js.Array[js.Dynamic], span: (Years.Astronomical, Years.Astronomical), keyOf: js.Dynamic => String): Unit =
    def byFindings(f: js.Dynamic) =
      dynStr(f.properties.basis).map(Place.TimeBasis.Wire(_)).flatMap(Place.TimeBasis.parse).contains(Place.TimeBasis.Findings)
    val before = playAreas.keySet.toSet
    playAreas.filterInPlace { (k, a) =>
      val keep = PlaybackAreas.live(playhead, a.end, playFade)
      if !keep then polityPlaces.remove(PlaceHover.PlaceId(k.value))
      keep
    }
    features.foreach { f =>
      val g = f.geometry
      val isArea = !js.isUndefined(g) && g != null && g.selectDynamic("type").toString != "Point" && g
        .selectDynamic("type")
        .toString != "MultiPoint"
      dynPeriod(f.properties).foreach { (a, b) =>
        val k = PlaybackAreas.key(PlaybackAreas.AreaKey(keyOf(f)), Some(PlaybackAreas.FractionalYear(a)), Some(PlaybackAreas.FractionalYear(b)))
        if isArea && !playAreas.contains(k) && PlaybackAreas.live(playhead, PlaybackAreas.FractionalYear(b), playFade) then
          playAreas(k) = PlayArea(f, g, PlaybackAreas.FractionalYear(a), PlaybackAreas.FractionalYear(b), byFindings(f))
          dynStr(f.properties.id).foreach(id =>
            polityPlaces(PlaceHover.PlaceId(k.value)) =
              (PlaceHover.PlaceId(id), com.histmersion.api.UiLabels.Text(dynStr(f.properties.label).getOrElse("unnamed")), PlaceHover.PlaceKind(dynStr(f.properties.kind).getOrElse("polity")))
          )
      }
    }
    if playAreas.keySet != before then
      polities.set(playAreas.toSeq.map { (k, a) =>
        val alpha = PlaybackAreas.step(PlaybackAreas.alpha(playhead, a.start, a.end, playFade)).value
        ShapeFeature(
          ShapeFeature.Id(k.value),
          a.geometry,
          ShapeFeature.Colour(viridisAt(Mid((a.start.value + a.end.value) / 2), span).value),
          dashed = ShapeFeature.Dashed(a.dashed),
          alpha = ShapeFeature.Alpha(if a.dashed then alpha * 0.6 else alpha)
        )
      })

  private[web] def midOf(pt: GeoHit): Option[com.histmersion.web.MapView.Mid] =
    for a <- pt.periodStart; b <- pt.periodEnd yield Mid((a.value + b.value).toDouble / 2)

  private[web] def avgMid(items: Iterable[GeoHit]): Option[com.histmersion.web.MapView.Mid] =
    val points = items.iterator
    @tailrec def loop(sum: Double, n: Int): Option[com.histmersion.web.MapView.Mid] =
      if points.hasNext then
        midOf(points.next()) match
          case Some(m) => loop(sum + m.value, n + 1)
          case None    => loop(sum, n)
      else if n > 0 then Some(Mid(sum / n))
      else None
    loop(0.0, 0)

  private[web] def colorFor(mid: Option[com.histmersion.web.MapView.Mid], span: (Years.Astronomical, Years.Astronomical), certainty: Certainty): com.histmersion.web.MapView.Color =
    val lo = span._1.value.min(span._2.value).toDouble
    val hi = span._1.value.max(span._2.value).toDouble
    val base = mid match
      case None    => Fmt.color(com.histmersion.web.Fmt.ColorT(0))
      case Some(m) => Fmt.color(Fmt.ColorT(if hi == lo then 0.5 else (m.value - lo) / (hi - lo)))
    tinted(base, certainty)

  private def tinted(base: com.histmersion.api.Wcag.Hex, certainty: Certainty): com.histmersion.web.MapView.Color =
    Color(Certainty.desaturate(com.histmersion.api.ConfidenceStyle.Rgb(base.value), certainty).value)

  private[web] def midRange(items: Iterable[GeoHit]): Option[(com.histmersion.web.MapView.Mid, com.histmersion.web.MapView.Mid)] = rangeOf(items.flatMap(midOf))

  /** Pure: the least and the greatest of `mids`. */
  private[web] def rangeOf(mids: Iterable[com.histmersion.web.MapView.Mid]): Option[(com.histmersion.web.MapView.Mid, com.histmersion.web.MapView.Mid)] =
    // `var`s: one pass over what may be thousands of mids, during a drag
    var lo = Double.MaxValue
    var hi = Double.MinValue
    for m <- mids do
      if m.value < lo then lo = m.value
      if m.value > hi then hi = m.value
    if lo <= hi then Some((Mid(lo), Mid(hi))) else None

  /** The CSS background VALUE for a group of findings: flat when they agree, a gradient across their period midpoints
    * when they do not.
    */
  private[web] def gradientBg(items: Iterable[GeoHit], span: (Years.Astronomical, Years.Astronomical), certainty: Certainty): com.histmersion.web.MapView.Color =
    gradientOf(items.flatMap(midOf), span, certainty)

  /** Pure: the same over the midpoints of any dated dots — a mixed bubble's findings, sites, events and pictures alike
    * (the undated have no say).
    */
  private[web] def gradientOf(mids: Iterable[com.histmersion.web.MapView.Mid], span: (Years.Astronomical, Years.Astronomical), certainty: Certainty): com.histmersion.web.MapView.Color =
    rampEnds(mids, span, certainty) match
      case (a, b) if a == b => a
      case (a, b)           => Color(s"linear-gradient(135deg,$a,$b)")

  /** Pure: the colours of the oldest and the newest of `mids` on the ramp (desaturated by `certainty`); the undated
    * colour twice when none is dated.
    */
  private[web] def rampEnds(mids: Iterable[com.histmersion.web.MapView.Mid], span: (Years.Astronomical, Years.Astronomical), certainty: Certainty): (com.histmersion.web.MapView.Color, com.histmersion.web.MapView.Color) =
    rangeOf(mids) match
      case None =>
        val c = colorFor(None, span, certainty)
        (c, c)
      case Some((lo, hi)) =>
        val sLo = span._1.value.min(span._2.value).toDouble
        val sHi = span._1.value.max(span._2.value).toDouble
        def norm(v: com.histmersion.web.MapView.Mid): com.histmersion.web.Fmt.ColorT = Fmt.ColorT(if sHi == sLo then 0.5 else (v.value - sLo) / (sHi - sLo))
        (tinted(Fmt.color(norm(lo)), certainty), tinted(Fmt.color(norm(hi)), certainty))

  private def radiusFor(n: DotCluster.Total): Int =
    if n.value > 1 then radius + 4 + 3 * n.value.toString.length else radius

  private def maxDotRadius: Int = radiusFor(DotCluster.Total(999))

  /** Pure: how near (px) two points' centres are when they join a bubble — the widest bubble's diameter (`maxDot` its
    * radius), or the largest marker box if that is wider: two markers drawn apart never overlap.
    */
  private[web] def clusterRadius(maxDot: Int, boxes: Iterable[(com.histmersion.web.PointKind.BoxPixels, com.histmersion.web.PointKind.BoxPixels)]): Int =
    math.max(2 * maxDot, boxes.map((w, h) => math.max(w.value, h.value)).maxOption.getOrElse(0))

  // `var`: the next bubble's gradient id (unique on the page)
  private var bubbleSeq = 0

  /** Pure: the period-span ring width (0–4) of a single finding spanning `spanYears`. */
  private[web] def ringOf(n: DotCluster.Total, spanYears: Option[com.histmersion.api.Years.Astronomical]): com.histmersion.web.MapView.Ring =
    if n.value == 1 && spanYears.exists(_.value > 0) then
      Ring(math.max(1, math.min(4, math.round(math.log10(spanYears.get.value.toDouble + 1) * 1.3).toInt)))
    else com.histmersion.web.MapView.Ring(0)

  /** One finding's popup: the source link (identifier label, preview on hover, window on click), title, period, claim
    * and its confidence badge — the same atoms as the server's pages.
    */
  private[web] def popupHtml(pt: GeoHit): com.histmersion.web.MapView.Html =
    // a finding that states no confidence says so — it is not drawn as high
    val conf = pt.confidence.fold(s""" <span class="hd-conf">confidence ${Certainty.UnstatedLabel}</span>""")(c =>
      " " + ConfidenceBadge.html(c)
    )
    val period = Fmt.periodStr(pt.periodStart, pt.periodEnd)
    Html(
      CardIcons.html(CardIcons.sourceKind(pt.sourceKind.map(k => Source.Kinds.Wire(k.value)).getOrElse(Source.Kinds.Wire(Source.Kinds.Unknown)))) +
        s"""${SourceLink.html(pt.citekey, pt.doi, SourceLink.idLabel(pt.citekey, pt.doi))} — ${Fmt.esc(pt.title.value)}""" +
        s"""<br><i>$period</i><br>${Fmt.esc(pt.claim.value)}$conf<br>""" +
        s"""<a href="${Fmt.esc(SourceLink.href(pt.citekey))}" target="_blank">new tab ↗</a>"""
    )

  /** A bubble's marker: its count, colours and outline, what it holds; a click lists them (or zooms in). */
  private def bubbleMarker(ds: Seq[Dot]): DomMarker =
    val held = DotCluster.contents(ds)
    val n = held.total
    val d = 2 * radiusFor(n)
    bubbleSeq += 1
    val extra = List(if DotCluster.mixed(held) then "hd-cluster--mixed" else "", Certainty.markerClass(held.certainty).value)
      .filter(_.nonEmpty)
      .mkString(" ")
    val (from, to) = rampEnds(held.mids, currentSpan, held.certainty)
    val el = DomMarker.element(
      "hd-dot-wrap",
      DotCluster
        .bubbleHtml(
          DotCluster.Count(n.value),
          DotCluster.Size(d),
          (DotCluster.StartLabel(from.value), DotCluster.EndLabel(to.value)),
          DotCluster.Id(s"hdb$bubbleSeq"),
          DotCluster.ExtraClass(extra),
          Some(DotCluster.SpokenText(DotCluster.summary(held).value)),
          DotCluster.form(held)
        )
        .value
    )
    val lat = LatLon.Lat(ds.map(_.at.lat.value).sum / ds.size)
    val lon = LatLon.Lon(ds.map(_.at.lon.value).sum / ds.size)
    val m = DomMarker(lat, lon, el, Some((DomMarker.BoxSize(d), DomMarker.BoxSize(d))))
    m.handlers = new MarkerHandlers:
      override def click(e: dom.MouseEvent): Unit =
        e.stopPropagation()
        open(ds)
    m

  /** A drawn bubble's colours set again in place (its members' colours moved with the span). */
  private def repaintBubble(m: DomMarker, ds: Seq[Dot]): Unit = repaintHeld(m, DotCluster.contents(ds))

  /** A tile's cell drawn alone, recoloured in place. */
  private[web] def repaintCell(m: DomMarker, member: Member): Unit = repaintHeld(m, DotCluster.Contents(List(member)))

  private def repaintHeld(m: DomMarker, held: DotCluster.Contents): Unit =
    val (a, b) = rampEnds(held.mids, currentSpan, held.certainty)
    Option(m.el.querySelector(".hd-cluster")).map(_.asInstanceOf[dom.html.Element]).foreach { c =>
      c.style.setProperty("--g0", a.value)
      c.style.setProperty("--g1", b.value)
    }

  /** The map zoomed in on `boxes` (a bubble's, or a tile's cells', items), as far as it takes to part them — deeper
    * than a list's fit, since a cell holds items the page has not got yet.
    */
  private[web] def zoomInto(boxes: List[GeoBounds]): Unit =
    if TheMap.isUp && boxes.nonEmpty then
      val b = boxes.reduce(_.extend(_))
      val s = TheMap.surface
      // at least one level deeper: a cell's items, all at one point, still part at the deepest tile
      if b.north.value - b.south.value < 1e-9 && b.east.value - b.west.value < 1e-9 then
        s.setView(
          LatLon(LatLon.Lat(b.south.value), LatLon.Lon(b.west.value)),
          MapSurface.Zoom(math.min(s.maxZoom.value, s.zoom.value + 2)),
          animate = com.histmersion.web.map.MapSurface.SetViewAnimate(true)
        )
      else s.fitBounds(b, com.histmersion.web.map.MapSurface.FitBoundsPadding(40), s.maxZoom)

  /** A bubble chosen: its list, or (too many to list, or some only counted in a tile's cell) the map zoomed in on it. A
    * bubble no zoom parts — its dots at one spot, or the deepest zoom reached — is also fanned out around its spot
    * ([[PointSet.spiderfy]]), so each of its dots can be reached on the map.
    */
  private def open(ds: Seq[Dot]): Unit =
    val all = DotCluster.contents(ds)
    val pts = ds.map(_.at)
    val b = GeoBounds(
      GeoBounds.South(pts.map(_.lat.value).min),
      GeoBounds.West(pts.map(_.lon.value).min),
      GeoBounds.North(pts.map(_.lat.value).max),
      GeoBounds.East(pts.map(_.lon.value).max)
    )
    def zoomIn(): Unit = if all.counted then zoomInto(b :: all.cells) else TheMap.surface.fitBounds(b, com.histmersion.web.map.MapSurface.FitBoundsPadding(40), MapSurface.MaxZoom(fitMaxZoom.value.toDouble))
    val deepest = TheMap.surface.zoom.value >= TheMap.surface.maxZoom.value - 0.01
    // a tile's cell is only counted: zooming in fetches its dots, wherever they stand
    val inseparable = deepest || (!all.counted && Clustering.onePlace(pts))
    // too many to list, or counted and not listed: zoom in, unless the map cannot (dots at one point stay one bubble)
    if (all.total.value > pickerMax.value || all.counted) && !inseparable then zoomIn()
    else
      if inseparable && spiderfies(DotCluster.Total(ds.size), HoldsCell(ds.exists(_.member.aggregate))) then
        dots.spiderfy(ds.map(d => PointSet.Key(d.key.value)), spiderSeparation)
      ResultsList.pick(
        all.findings,
        com.histmersion.api.UiLabels.Text(s"${DotCluster.summary(all)} in this cluster"),
        Some(() => zoomIn()),
        DotCluster.ordered(DotCluster.others(ds))
      )

  /** Pure: is a bubble of `n` dots fanned out (not when it holds a tile's counted cell, which is no one dot, or more
    * than a spiral around the spot shows legibly)?
    */
  private[web] def spiderfies(n: DotCluster.Total, holdsCell: com.histmersion.web.MapView.HoldsCell): Boolean = n.value >= 2 && n.value <= Layout.spiderMax.value && !holdsCell.value

  /** How far apart fanned-out dots stand: the widest marker box and a gap. */
  private def spiderSeparation: Spiderfy.Separation = Spiderfy.Separation((PointKinds.all.map(k => math.max(k.box._1.value, k.box._2.value)).max + 8).toDouble)

  def init(ui: UiConfig): HtmlElement =
    radius = ui.markerRadius.value
    tilesConfig = ui.mapTiles
    BaseMaps.configure(ui)
    // the span the page opens on (UiState is restored before this mounts): the overlays switched on
    // from the browser's storage load at once, and loaded the placeholder span (0, 1) — a wasted request
    // and a wrong first drawing — until the first findings arrived
    currentSpan = (Years.Astronomical(UiState.from.now().getOrElse(ui.time.defaultFrom.value)), Years.Astronomical(UiState.to.now().getOrElse(ui.time.defaultTo.value)))
    div(
      idAttr := "map",
      cls := "map-container",
      onMountCallback { ctx =>
        // without a GPU the map is drawn at one pixel per CSS pixel: a quarter of the work on a 2× screen;
        // without WebGL 2 it is not drawn at all, and its place says why (NoMapSurface)
        val surface =
          Engines.surface(ctx.thisNode.ref, MapSurface.MaxZoom(ui.tilesMaxZoom.value.toDouble), caps => Option.when(caps.software.value)(MapEngine.PixelRatio(1.0)))
        TheMap.install(surface)
        val (startAt, startZoom) = com.histmersion.web.map.MapStart
          .initial(ui.mapStart, Store.Browser.get(com.histmersion.web.map.MapStart.StorageKey).map(com.histmersion.web.map.MapStart.Encoded(_)))
        surface.setView(startAt, startZoom)
        val _ = surface.on(MapEventKind.MoveEnd)(_ =>
          com.histmersion.web.map.MapStart.remember(ui.mapStart, surface.center, surface.zoom)
        )
        PlaceHover.shapes(com.histmersion.web.PlaceHover.ShapeSetName("polities"), PlaceHover.Places(polityPlaces.get, spanMid))
        addLegend(surface)
        PlaceHover.install(surface)
        PlaceGlyph.followZoom(surface)
        MapLabels.install(surface, ui)(using ctx.owner)
        BaseMaps.install(surface, ui)(using ctx.owner)
        MapHandle.install(surface)
        marks.MapMenu.install(surface)
        marks.NoteMarkers.install()
        // the overlays and the base map follow UiState (restored from the browser's storage by
        // UiState.init, before this mounts) — there is no other copy of that state to disagree with
        UiState.layers.signal.foreach(layerSync(_))(using ctx.owner)
        // the media layer's provider/licence filters reload it, when it is on
        UiState.mediaProvider.signal
          .combineWith(UiState.mediaLicense.signal)
          .changes
          .foreach(_ => if layerSync.attached(MapLayer.Media) then loadMedia(currentSpan))(using ctx.owner)
        // the events layer (#1449): its kind/tier menus reload it; the key follows the layers
        UiState.eventKind.signal
          .combineWith(UiState.eventTier.signal)
          .changes
          .foreach(_ => if layerSync.attached(MapLayer.Events) then loadEvents(currentSpan))(using ctx.owner)
        UiState.siteType.signal
          .combineWith(UiState.sitesUndated.signal)
          .changes
          .foreach(_ => if layerSync.attached(MapLayer.Sites) then loadSites(currentSpan))(using ctx.owner)
        UiState.eventsUndated.signal.changes
          .foreach(_ => if layerSync.attached(MapLayer.Events) then loadEvents(currentSpan))(using ctx.owner)
        // the "States shown" menu: the boundaries (the sites, events and pictures are every one of the span)
        UiState.areaPick.signal.changes.foreach { _ =>
          if layerSync.attached(MapLayer.Boundaries) then loadOverlay(currentSpan)
        }(using ctx.owner)
        UiState.layers.signal
          .combineWith(UiState.sitesUndated.signal, UiState.eventsUndated.signal)
          .foreach((ls, su, eu) =>
            updateKey(
              MapKey.Events(ls(MapLayer.Events)),
              MapKey.Sites(ls(MapLayer.Sites)),
              MapKey.Undated((ls(MapLayer.Sites) && su) || (ls(MapLayer.Events) && eu))
            )
          )(using ctx.owner)
        // the finding markers follow their switch the same way; `render` keeps their content current
        // while they are off, so switching them back on shows the current span at once
        UiState.sources.signal.foreach(on => showSources(ShowSources(on)))(using ctx.owner)
        surface.container.addEventListener(
          "click",
          { (e: dom.MouseEvent) =>
            val target = e.target.asInstanceOf[dom.Element]
            Option(target.closest("[data-ck]")).foreach { el =>
              if !e.metaKey && !e.ctrlKey && !e.shiftKey then
                e.preventDefault()
                ArticleModal.open(com.histmersion.api.library.Citekey(el.getAttribute("data-ck")))
            }
          }
        )
      },
      onMountUnmountCallbackWithState(
        mount = ctx =>
          // A folded timeline or resized sidebar changes this element without a window resize: the
          // map caches its size, so observe the container rather than any one layout toggle. The ONE
          // observer (MapLibre's own is off): the map takes the size and is drawn in the same frame.
          if js.typeOf(js.Dynamic.global.ResizeObserver) == "undefined" then None
          else
            val resize: js.Function1[js.Any, Unit] = _ => if TheMap.isUp then TheMap.surface.resize()
            val observer = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(resize)
            observer.observe(ctx.thisNode.ref)
            Some(observer)
        ,
        unmount = (_, state) =>
          state.flatten.foreach { observer =>
            val _ = observer.disconnect()
          }
      )
    )

  /** The findings' switch, as every point layer's: their points join or leave the cluster group, which the other kinds
    * share (so it stays on the map); `render` keeps them current while they are out.
    */
  private def showSources(on: com.histmersion.web.MapView.ShowSources): Unit =
    if on.value then findingsLayer.attach() else findingsLayer.detach()

  private def addLegend(surface: MapSurface): Unit =
    legendEl = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    legendEl.className = "hd-legend hd-map-control"
    val ramp = (0 until 10).map(i => s"""<i style="--c:${Fmt.color(Fmt.ColorT(i.toDouble / 9))}"></i>""").mkString
    val confPips = Confidence.values
      .map(c =>
        val wire = c.toString.toLowerCase
        s"""<i style="--c:var(${Palette.confidence(Confidence.Wire(wire))})" title="$wire"></i>"""
      )
      .mkString
    // a finding with no confidence: its own pip, apart from the scale (it is not a level of it)
    val unstated = Certainty.UnstatedLabel
    val unstatedPip =
      s"""<i class="is-unstated" style="--c:var(--conf-unstated)" title="$unstated"></i>""" +
        s"""<span class="hd-conf-range">$unstated</span>"""
    legendEl.innerHTML = MapFold.toggleHtml(
      com.histmersion.web.MapFold.ClassName("hd-legend-toggle"),
      com.histmersion.web.MapFold.ControlsId("hd-legend-body"),
      com.histmersion.web.MapFold.Label("map legend"),
      com.histmersion.web.MapFold.InnerHtml(s"""<span class="hd-ramp hd-ramp--mini" aria-hidden="true">$ramp</span><span>legend</span>"""),
      open = com.histmersion.web.MapFold.Open(false)
    ).value +
      """<div class="hd-legend-body" id="hd-legend-body">""" +
      s"""<div class="hd-ramp">$ramp</div>""" +
      """<div class="hd-ramp-labels"><span class="lo"></span>""" +
      """<span class="mid">older ← → newer</span><span class="hi"></span></div>""" +
      s"""<div class="hd-legend-confidence"><span class="hd-conf-label">certainty</span>$confPips""" +
      """<span class="hd-conf-range">high → contested</span>""" +
      s"""$unstatedPip</div><div class="hd-key-slot"></div></div>"""
    MapFold.wire(legendEl)
    MapFold.followSize(surface)
    val corner = surface.corner(Corner.BottomRight)
    corner.insertBefore(legendEl, corner.firstChild)

  /** The legend's key to the icons (#1449), for the layers that draw them. */
  private def updateKey(events: com.histmersion.web.MapKey.Events, sites: com.histmersion.web.MapKey.Sites, undated: com.histmersion.web.MapKey.Undated): Unit =
    if legendEl != null then
      Option(legendEl.querySelector(".hd-key-slot")).foreach(_.innerHTML = MapKey.html(events, sites, undated).value)

  private def loadEvents(span: (Years.Astronomical, Years.Astronomical)): Unit =
    val tiles = tileSpan(span)
    EventMarkers.load((com.histmersion.api.catalogue.Year(tiles._1.value), com.histmersion.api.catalogue.Year(tiles._2.value)), span)

  /** Pure: the span the tiled layers are asked for — the span itself, or in playback the playhead's window widened to
    * whole time buckets ([[PlaybackBuckets]]), so their tiles change once a bucket.
    */
  private[web] def tileSpanOf(
      span: (Years.Astronomical, Years.Astronomical),
      playback: com.histmersion.web.MapView.Playback,
      yearsPerSecond: com.histmersion.web.MapView.YearsPerSecond,
      bucketSeconds: com.histmersion.api.catalogue.MapTilesUiConfig.PlaybackBucketSeconds
  ): (Years.Astronomical, Years.Astronomical) =
    val ordered = (Years.Astronomical(span._1.value.min(span._2.value)), Years.Astronomical(span._1.value.max(span._2.value)))
    if playback.value then PlaybackBuckets.widen(ordered, PlaybackBuckets.years(yearsPerSecond, PlaybackBuckets.Seconds(bucketSeconds.value))) else ordered

  private def tileSpan(span: (Years.Astronomical, Years.Astronomical)): (Years.Astronomical, Years.Astronomical) =
    tileSpanOf(span, playbackMode, playYearsPerSecond, tilesConfig.playbackBucketSeconds)

  /** The tiled layers on the map (sites, pictures, events). */
  private def tiledOn: List[TiledPoints[?]] =
    List(MapLayer.Sites -> sitesLayer, MapLayer.Media -> mediaLayer, MapLayer.Events -> EventMarkers.layer)
      .collect { case (l, t) if layerSync.attached(l) => t }

  private def updateLegend(span: (Years.Astronomical, Years.Astronomical)): Unit =
    if legendEl == null then ()
    else
      val lo = legendEl.querySelector(".lo")
      val hi = legendEl.querySelector(".hi")
      if lo != null then lo.textContent = Fmt.yr(com.histmersion.api.catalogue.Year(span._1.value.min(span._2.value))).value
      if hi != null then hi.textContent = Fmt.yr(com.histmersion.api.catalogue.Year(span._1.value.max(span._2.value))).value

  /** Pure: the native tooltip of a dot holding `items` — "N findings here" where several coincide, none for a single
    * finding (its source's card answers the hover, [[hoverSource]]): never a citekey.
    */
  private[web] def markerTitle(items: List[GeoHit]): Option[com.histmersion.web.MapView.Title] =
    Option.when(items.size > 1)(Title(s"${items.size} findings here"))

  /** Pure: the source whose card a dot's hover shows — its one finding's; none for coincident findings (the first of
    * several would misdescribe the spot: their list opens on a click).
    */
  private[web] def hoverSource(items: List[GeoHit]): Option[com.histmersion.web.MapView.SourceLabel] = items match
    case List(one) => Some(SourceLabel(one.citekey.value))
    case _         => None

  /** Pure: a single finding's dot as words, for assistive tech — the source's title and the period. */
  private[web] def markerLabel(pt: GeoHit): com.histmersion.web.MapView.Label =
    val title = Option(pt.title.value).map(_.trim).filter(_.nonEmpty).getOrElse(SourceLink.idLabel(pt.citekey, pt.doi).value)
    val period = Fmt.periodStr(pt.periodStart, pt.periodEnd)
    Label(if period.value.isEmpty then title else s"$title ($period)")

  /** The findings of `span` (the filter's, a playback frame's): their points, as every kind's. */
  def render(points: List[GeoHit], span: (Years.Astronomical, Years.Astronomical)): Unit =
    currentSpan = span
    if TheMap.isUp then
      updateLegend(span)
      findingsLayer.show(Spot.of(points), span)
      // the tiled layers keep what they show, recoloured, until the tiles of the new span are in
      tiledOn.foreach(_.recolour(span))
      reloadLayers(span)
      if layerSync.attached.exists(waitsForQuiet(_, UiState.areaPick.now(), playbackMode)) then reloadQuiet(span)

  /** Bring the point `key` out of its bubble (zooming in as far as it takes), then `andThen`. */
  private[web] def reveal(key: PointSet.Key, andThen: () => Unit): Unit =
    if TheMap.isUp then dots.zoomToShow(key, andThen)

  /** Overlay layers follow the map at most once per `Layout.layerReloadMs`, so a timeline drag (which renders on every
    * pointer move) does not fire a request burst — except the one that waits for the span to settle
    * ([[waitsForQuiet]]).
    */
  private val reloadLayers = Throttle[(Years.Astronomical, Years.Astronomical)](
    Throttle.IntervalMs(Layout.layerReloadMs.value),
    span =>
      layerSync.attached.foreach(l =>
        if !waitsForQuiet(l, UiState.areaPick.now(), playbackMode) then slot(l).load(span)
      )
  )

  /** The layers that wait for the span to settle, once it has stopped moving for `Layout.rangeReloadMs`. */
  private val reloadQuiet = Debounce[(Years.Astronomical, Years.Astronomical)](
    Debounce.QuietMs(Layout.rangeReloadMs.value),
    span =>
      layerSync.attached.foreach(l => if waitsForQuiet(l, UiState.areaPick.now(), playbackMode) then slot(l).load(span))
  )

  /** Pure: does `layer` wait until the span settles instead of following a drag (#1949)? The boundaries over a whole
    * range ("All states in period") do: every span is a new answer of megabytes (1 600 shapes, ~7 MB), which a
    * throttled drag fetched, parsed and drew five times a second — 300 ms frames and a request queue. So do the sites,
    * events and pictures: a new span is every tile of the view cut again, for each of them — while the span moves they
    * keep what they show, recoloured. A snapshot year follows the drag as before; in playback every layer follows the
    * playhead (the tiled ones by whole time buckets, [[tileSpanOf]]).
    */
  private[web] def waitsForQuiet(layer: MapLayer, pick: GeoPick, playback: com.histmersion.web.MapView.Playback): Boolean =
    !playback.value && (layer match
      case MapLayer.Boundaries => !pick.snapshot
      case _                   => true)

  /** Place GeoJSON by (kind, time): a drag back and forth over the same years is served locally. The time is what the
    * "States shown" menu made of the span ([[areaTime]]), so a snapshot at the same year is one entry whichever choice
    * led to it, and a whole-range answer is its own. Bounded by count AND by size (#1949): a whole range's answer is
    * megabytes, and 48 of them were kept.
    */
  private val geoCache =
    BoundedCache[(com.histmersion.api.catalogue.Place.Kind.Wire, GeoTime), scala.concurrent.Future[String]](BoundedCache.MaxEntries(Layout.geoCacheSize.value), BoundedCache.MaxWeight(Layout.geoCacheChars.value))

  /** The place-GeoJSON cache's figures, for [[Diagnostics]]. */
  def geoCacheStats: BoundedCache.Stats = BoundedCache.stats(geoCache)

  /** Pure: the query of a layer of `kind` drawn for `time`. */
  private[web] def geoQuery(kind: com.histmersion.api.catalogue.Place.Kind.Wire, time: GeoTime, limit: com.histmersion.api.Paging.Limit): Api.PlacesGeoQuery =
    val q = Api.PlacesGeoQuery(kind = Some(kind), limit = Some(Api.PlacesGeoQuery.Limit(limit.value)))
    time match
      case GeoTime.At(y)                  => q.modify(_.asOf).setTo(Some(Api.PlacesGeoQuery.AsOf(y.value)))
      case GeoTime.Within(lo, hi, latest) =>
        q.modify(_.from).setTo(Some(Api.PlacesGeoQuery.From(lo.value))).modify(_.to).setTo(Some(Api.PlacesGeoQuery.To(hi.value))).modify(_.pick).setTo(Some((if latest.value then GeoPick.Latest else GeoPick.Earliest).wire))
      case GeoTime.Always => q

  /** Pure: the time the boundaries layer is drawn for over `span`, as `pick` reads it; playback always draws the
    * playhead's one year (the middle of its window).
    */
  private[web] def areaTime(span: (Years.Astronomical, Years.Astronomical), pick: GeoPick, playback: com.histmersion.web.MapView.Playback): GeoTime =
    GeoPick.resolve(None, Some(com.histmersion.api.catalogue.Year(span._1.value)), Some(com.histmersion.api.catalogue.Year(span._2.value)), if playback.value then GeoPick.Mid else pick)

  /** Pure: how many areas the boundaries layer asks for at `time` — a year's few hundred at most, or every state of a
    * whole range.
    */
  private[web] def overlayLimit(time: GeoTime): com.histmersion.api.Paging.Limit = time match
    case _: GeoTime.Within => com.histmersion.api.Paging.Limit(Layout.overlayRangeFetchLimit.value)
    case _                 => com.histmersion.api.Paging.Limit(Layout.overlayFetchLimit.value)

  private def placesGeoCached(kind: com.histmersion.api.catalogue.Place.Kind.Wire, time: GeoTime, limit: com.histmersion.api.Paging.Limit): scala.concurrent.Future[String] =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    val k = (kind, time)
    geoCache.get(k) match
      case Some(f) => f
      case None    =>
        val f = ApiClient.placesGeo(geoQuery(kind, time, limit)).map(_.value)
        geoCache.put(k, f)
        // weighed once it is in: its length in characters (the size of the string kept)
        f.onComplete {
          case scala.util.Success(s) => if geoCache.peek(k).contains(f) then geoCache.reweigh(k, BoundedCache.ReweighW(s.length.toLong))
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

  private[web] def viridisAt(mid: com.histmersion.web.MapView.Mid, span: (Years.Astronomical, Years.Astronomical)): com.histmersion.web.MapView.Color =
    val sLo = span._1.value.min(span._2.value).toDouble
    val sHi = span._1.value.max(span._2.value).toDouble
    Color(Fmt.color(Fmt.ColorT(if sHi == sLo then 0.5 else ((mid.value - sLo) / (sHi - sLo)).max(0).min(1))).value)

  private def loadOverlay(span: (Years.Astronomical, Years.Astronomical)): Unit =
    val mid = (span._1.value + span._2.value) / 2
    overlayReqId += 1
    val myId = overlayReqId
    val time0 = areaTime(span, UiState.areaPick.now(), playbackMode)
    // in playback the year ahead of the playhead (the states it reaches next are held before it does)
    val time = time0 match
      case GeoTime.At(y) if playbackMode.value && playAreas.nonEmpty => GeoTime.At(com.histmersion.api.catalogue.Year(y.value + playLead.value))
      case t                                                   => t
    val playing = playbackMode
    placesGeoCached(com.histmersion.api.catalogue.Place.Kind.Wire("polity"), time, overlayLimit(time)).foreach { geojson =>
      if myId == overlayReqId && overlayOn && TheMap.isUp && playing == playbackMode then
        val features = js.JSON.parse(geojson).features.asInstanceOf[js.Array[js.Dynamic]]
        if playing.value then
          def keyOf(f: js.Dynamic) = dynStr(f.properties.id).orElse(dynStr(f.properties.label)).getOrElse("?")
          addPlayAreas(features, currentSpan, keyOf)
          val shown = playAreas.toSeq
            .filter((_, a) => PlaybackAreas.alpha(playhead, a.start, a.end, playFade).value > 0)
            .map(_._2.feature)
          val (points, _) = features.toList.partition { f =>
            val g = f.geometry
            !js.isUndefined(g) && g != null && (g.selectDynamic("type").toString == "Point" || g
              .selectDynamic("type")
              .toString == "MultiPoint")
          }
          syncPointLabels(points, keyOf)
          MapLabels.attach()
          MapLabels.syncAreas(
            shown,
            key = f => MapLabels.Key(areaKeyOf(keyOf, f).value),
            label = f => MapLabels.Label(dynStr(f.properties.label).getOrElse("unnamed")),
            wrapClass = f => MapLabels.WrapClass(placeClasses(com.histmersion.web.MapView.ClassName("hd-map-label-wrap"), dynStr(f.properties.basis).map(Place.TimeBasis.Wire(_))).value),
            place = f => polityPlaces.get(PlaceHover.PlaceId(areaKeyOf(keyOf, f).value)),
            asOf = () => spanMid()
          )
        else
          // largest first, so small polities sit on top and stay hoverable
          val sorted = features.toList.sortBy(f => dynPeriod(f.properties).map((a, b) => a - b).getOrElse(99999.0))
          def keyOf(f: js.Dynamic) = dynStr(f.properties.id).orElse(dynStr(f.properties.label)).getOrElse("?")
          def colorOf(f: js.Dynamic): com.histmersion.web.MapView.Color =
            viridisAt(Mid(dynPeriod(f.properties).map((a, b) => (a + b) / 2).getOrElse(mid.toDouble)), span)
          def byFindings(f: js.Dynamic) =
            dynStr(f.properties.basis).map(Place.TimeBasis.Wire(_)).flatMap(Place.TimeBasis.parse).contains(Place.TimeBasis.Findings)
          def isPoint(f: js.Dynamic) =
            val g = f.geometry
            !js.isUndefined(g) && g != null && (g.selectDynamic("type").toString == "Point" || g
              .selectDynamic("type")
              .toString == "MultiPoint")
          val (points, areas) = sorted.partition(isPoint)
          polityPlaces.clear()
          sorted.foreach(f =>
            dynStr(f.properties.id).foreach(id =>
              polityPlaces(PlaceHover.PlaceId(keyOf(f))) =
                (PlaceHover.PlaceId(id), com.histmersion.api.UiLabels.Text(dynStr(f.properties.label).getOrElse("unnamed")), PlaceHover.PlaceKind(dynStr(f.properties.kind).getOrElse("polity")))
            )
          )
          polities.set(
            areas.map(f =>
              ShapeFeature(
                ShapeFeature.Id(keyOf(f)),
                f.geometry,
                ShapeFeature.Colour(colorOf(f).value),
                dashed = ShapeFeature.Dashed(byFindings(f)),
                alpha = ShapeFeature.Alpha(if byFindings(f) then 0.6 else 1.0)
              )
            )
          )
          syncPointLabels(points, keyOf)
          // every area is named inside itself (MapLabels), in playback and out of it
          MapLabels.attach()
          MapLabels.syncAreas(
            areas,
            key = f => MapLabels.Key(keyOf(f)),
            label = f => MapLabels.Label(dynStr(f.properties.label).getOrElse("unnamed")),
            wrapClass = f => MapLabels.WrapClass(placeClasses(com.histmersion.web.MapView.ClassName("hd-map-label-wrap"), dynStr(f.properties.basis).map(Place.TimeBasis.Wire(_))).value),
            place = f => polityPlaces.get(PlaceHover.PlaceId(keyOf(f))),
            asOf = () => spanMid(),
            // once its name is placed nothing here reads the outline: the map holds its own copy
            release = dropGeometry
          )
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  private def areaKeyOf(keyOf: js.Dynamic => String, f: js.Dynamic): PlaybackAreas.AreaKey =
    PlaybackAreas.key(
      PlaybackAreas.AreaKey(keyOf(f)),
      dynPeriod(f.properties).map(p => PlaybackAreas.FractionalYear(p._1)),
      dynPeriod(f.properties).map(p => PlaybackAreas.FractionalYear(p._2))
    )

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
          if f.geometry.selectDynamic("type").toString == "Point" then
            (c.selectDynamic("0").asInstanceOf[Double], c.selectDynamic("1").asInstanceOf[Double])
          else
            (
              c.selectDynamic("0").selectDynamic("0").asInstanceOf[Double],
              c.selectDynamic("0").selectDynamic("1").asInstanceOf[Double]
            )
        val p = f.properties
        val label = dynStr(p.label).getOrElse("unnamed")
        val kind = dynStr(p.kind).getOrElse("polity")
        val m = PlaceGlyph.labelMarker(
          LatLon.Lat(lat),
          LatLon.Lon(lon),
          com.histmersion.api.UiLabels.Text(label),
          Place.Summary.Kind(kind),
          placeClasses(com.histmersion.web.MapView.ClassName("hd-area-point-wrap"), dynStr(p.basis).map(Place.TimeBasis.Wire(_))).value,
          hidden = PlaceGlyph.LabelHidden(true)
        )
        dynStr(p.id).foreach(id => PlaceHover.bind(m, PlaceHover.PlaceId(id), com.histmersion.api.UiLabels.Text(label), PlaceHover.PlaceKind(kind), () => spanMid()))
        MapLabels.trackPoint(m, MapLabels.Key(dynStr(p.id).getOrElse(label))) // thinned with the area names
        pointLabels(k) = m
        pane.add(m)
    }

  /** Forget a drawn feature's outline (its layer has its own copy); its point is kept (a label's). */
  private def dropGeometry(f: js.Dynamic): Unit =
    val g = f.geometry
    if !js.isUndefined(g) && g != null && g.selectDynamic("type").toString != "Point" then
      f.updateDynamic("geometry")(null)

  /** The classes of a drawn place: `base`, plus `hd-by-findings` for a place the time view shows only because findings
    * dated then are attached to it (an undated place, [[PlaceTimeBasis.Findings]]) — drawn dashed and lighter, so it
    * never reads as a place known to exist then.
    */
  def placeClasses(base: com.histmersion.web.MapView.ClassName, basis: Option[com.histmersion.api.catalogue.Place.TimeBasis.Wire]): com.histmersion.web.MapView.ClassName =
    if basis.flatMap(Place.TimeBasis.parse).contains(Place.TimeBasis.Findings) then ClassName(s"$base $ByFindingsClass") else base

  val ByFindingsClass = "hd-by-findings"

  /** A site or an event with no known date (drawn only when asked for): outlined dashed, beneath the dated. */
  val UndatedClass = "hd-undated"

  /** The year a place's card reads the gazetteer at: the middle of the span the map shows. */
  private[web] def spanMid(): Option[com.histmersion.api.Years.Astronomical] = Some(Years.Astronomical((currentSpan._1.value + currentSpan._2.value) / 2))

  /** Pure: a tile query's span — `from`/`to` of the shared filter. */
  private[web] def tileFilter(span: (Years.Astronomical, Years.Astronomical)): Filter = Filter(period = Some(Period(com.histmersion.api.catalogue.Year(span._1.value), com.histmersion.api.catalogue.Year(span._2.value))))

  /** Every picture of the span, by provider and licence. */
  private def loadMedia(span: (Years.Astronomical, Years.Astronomical)): Unit =
    mediaLayer.load(
      ApiClient.tileTemplate(
        TilesApi.TileQuery(
          TileLayer.Media.wire,
          com.histmersion.api.catalogue.TilesApi.TileQuery.Z(0),
          com.histmersion.api.catalogue.TilesApi.TileQuery.X(0),
          com.histmersion.api.catalogue.TilesApi.TileQuery.Y(0),
          tileFilter(tileSpan(span)),
          provider = UiState.mediaProvider.now().map(TilesApi.TileQuery.Provider(_)),
          license = UiState.mediaLicense.now().map(TilesApi.TileQuery.License(_))
        )
      ),
      span
    )

  /** Every site of the span, of the type the site-type menu (#1449) keeps. */
  private def loadSites(span: (Years.Astronomical, Years.Astronomical)): Unit =
    sitesLayer.load(
      ApiClient.tileTemplate(
        TilesApi.TileQuery(
          TileLayer.Sites.wire,
          com.histmersion.api.catalogue.TilesApi.TileQuery.Z(0),
          com.histmersion.api.catalogue.TilesApi.TileQuery.X(0),
          com.histmersion.api.catalogue.TilesApi.TileQuery.Y(0),
          tileFilter(tileSpan(span)),
          siteType = UiState.siteType.now().map(TilesApi.TileQuery.SiteType(_)),
          undated = Option.when(UiState.sitesUndated.now())(TilesApi.TileQuery.Undated(true))
        )
      ),
      span
    )
