package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The pure half of the events in playback (#1440): when an event shows and how strongly. */
object EventPlayback:

  /** Pure: how visible an event is with the playhead at `playhead` — 0 before its span, 1 inside it,
    * then falling linearly to 0 over `fadeYears` after it ends (a battle of one year stays on screen
    * for the fade, not for one frame). */
  def alpha(span: (Long, Long), playhead: Long, fadeYears: Double): Double =
    val (start, end) = span
    if playhead < start then 0.0
    else if playhead <= end then 1.0
    else if fadeYears <= 0 then 0.0
    else (1.0 - (playhead - end).toDouble / fadeYears).max(0.0)

  /** Pure: the years a fade of `seconds` of playback takes at `yearsPerSecond`. */
  def fadeYears(seconds: Double, yearsPerSecond: Double): Double = (seconds * yearsPerSecond).max(0.0)

  /** Pure: `alpha` rounded to twentieths — what the page is told, so a slow fade does not rewrite the
    * same element on every frame. */
  def step(alpha: Double): Double = math.round(alpha * 20).toDouble / 20

  /** Pure: the events playback draws, with the years each covers: the dated ones precise enough to
    * stand at a year ([[EventPrecision.mapped]] — the feed leaves the imprecise out already). */
  def playable(events: List[EventGeo]): Vector[(EventGeo, (Long, Long))] =
    events.toVector.filter(e => EventPrecision.mapped(e.precision)).flatMap(e => EventGeo.outer(e.timeSpan).map(span => (e, span)))

  /** Pure: the line under an event's name — its kind and its years. */
  def note(e: EventGeo): String =
    s"${Fmt.display(e.kind)} · ${YearFmt.span(e.timeSpan)}"

  /** Pure: the key events share when they are drawn at the same point (a site with several
    * destructions): their labels are stacked there rather than drawn over each other. */
  def anchorKey(e: EventGeo): String = f"${e.lat}%.4f|${e.lon}%.4f"

  /** Pure: the stacking slot of each SHOWN event at one anchor — `ids` in their order there (by start),
    * numbered 0, 1, 2… among those shown, so a label moves up when the one above it goes. */
  def slots(ids: Seq[String], shown: String => Boolean): Map[String, Int] =
    ids.filter(shown).zipWithIndex.toMap

  /** Pure: an event name's rank in the map's collision pass ([[MapLabels]], [[LabelPlacement.cull]]):
    * above every area name (whose rank is its zoom-0 area, at most the 256² px world) and point name
    * — an event is on screen for a moment, the static names return when it goes; a local event above
    * a regional one (the battle stays readable inside its war); among each, the later start first
    * (the event just arrived wins over the one fading out). */
  def rank(e: EventGeo, start: Long): Double =
    (if e.scale == EventScale.Regional then RegionalRank else LocalRank) + start.toDouble

  private val RegionalRank = 1e9
  private val LocalRank    = 2e9

  /** Pure: the label marker's markup. It is the point-only region label of [[PlaceGlyph]] — the same
    * `.hd-area-point` halo name and note — inside an `.hd-event` wrapper that says local (with the dot
    * at the site) or regional; the wrapper carries the visibility as `--a`. The name starts hidden
    * (`is-culled`): the map's label pass shows it once it collides with no higher-ranked name. */
  def html(e: EventGeo, alpha: Double): String =
    val local = e.scale != EventScale.Regional
    val title = s"${e.label} — ${note(e)}${e.dating.fold("")(d => s" · $d")}${e.placeLabel.fold("")(p => s" · $p")} (${e.historicity})"
    // the dot carries the kind's icon (#1449): what happened reads before the name does
    val dot = if local then s"""<span class="hd-event-dot hd-badge">${Glyphs.badge(EventTaxonomy.icon(e.kind))}</span>""" else ""
    val approx = if e.precision == EventPrecision.Approximate then " hd-event--approximate" else ""
    s"""<span class="hd-event ${if local then "hd-event--local" else "hd-event--regional"}$approx" style="--a:$alpha" """ +
      s"""title="${Fmt.esc(title)}">$dot<span class="hd-area-point is-culled">""" +
      s"""<span class="hd-area-point-name">${Fmt.esc(e.label)}</span>""" +
      s"""<span class="hd-area-point-note">${Fmt.esc(note(e))}</span></span></span>"""

/** The events on the map while the timeline plays (#1440). Fetched ONCE when playback starts (the
  * filter's events over the span played — `/api/events/geo`); every frame after that is local: each
  * event's visibility is computed from the playhead, and only the events that appear, disappear or
  * change visibility touch the page. A LOCAL event is a dot and its label at its site, fading out over
  * `ui.events.local-fade-seconds` of playback after it ends; a REGIONAL one is its label at the
  * region's point-on-surface and the region's faint outline for its span (an area known only by a
  * point: the label at the point). */
object EventLayer:

  // `var`s: a shown event's visibility and stacking slot change frame by frame, in place
  private final case class Shown(marker: Marker, outline: Option[Layer], var alpha: Double, var slot: Int)

  /** The Leaflet pane the labels go in: above the finding markers, so a cluster never hides an event. */
  private val Pane = MapPanes.Events

  private var map: LeafletMap         = null
  private var group: LayerGroup       = null
  private var config: EventsUiConfig  = null
  private var items: Vector[(EventGeo, (Long, Long))] = Vector.empty
  // the events at each anchor, by start: who stacks over whom
  private var anchors: Map[String, Vector[String]] = Map.empty
  private val shown = mutable.HashMap.empty[String, Shown]
  private var reqId: Int              = 0
  // the last frame drawn, so the events that arrive after it are drawn at once
  private var lastFrame: Option[(Long, Double)] = None

  /** Start: fetch the events of `query` and draw them at the next frame. */
  def start(m: LeafletMap, cfg: EventsUiConfig, query: Api.EventQuery): Unit =
    stop()
    map = m
    config = cfg
    MapPanes.ensure(map)
    group = L.layerGroup()
    group.addTo(map)
    reqId += 1
    val mine = reqId
    ApiClient.eventsGeo(query).foreach { list =>
      if mine == reqId && group != null then
        items = EventPlayback.playable(list.events)
        anchors = items.sortBy(_._2._1).groupMap((e, _) => EventPlayback.anchorKey(e))((e, _) => e.id)
        lastFrame.foreach((ph, yps) => frame(ph, yps))
    }(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /** One frame: the playhead at `playhead`, playing `yearsPerSecond`. */
  def frame(playhead: Long, yearsPerSecond: Double): Unit =
    lastFrame = Some((playhead, yearsPerSecond))
    if group != null && config != null then
      val localFade    = EventPlayback.fadeYears(config.localFadeSeconds, yearsPerSecond)
      val regionalFade = EventPlayback.fadeYears(config.regionalFadeSeconds, yearsPerSecond)
      val moved = mutable.Set.empty[String]
      items.foreach { (e, span) =>
        val fade = if e.scale == EventScale.Regional then regionalFade else localFade
        val a = EventPlayback.step(EventPlayback.alpha(span, playhead, fade))
        shown.get(e.id) match
          case None if a > 0 =>
            shown(e.id) = add(e, span._1, a)
            moved += EventPlayback.anchorKey(e)
          case Some(s) if a <= 0 =>
            remove(e.id, s)
            shown.remove(e.id)
            moved += EventPlayback.anchorKey(e)
          case Some(s) if s.alpha != a =>
            s.alpha = a
            setAlpha(s, a)
          case _ => ()
      }
      // only where an event came or went: the labels at that point are restacked
      moved.foreach { key =>
        EventPlayback.slots(anchors.getOrElse(key, Vector.empty), shown.contains).foreach { (id, slot) =>
          shown.get(id).filter(_.slot != slot).foreach { s =>
            s.slot = slot
            setSlot(s, slot)
          }
        }
      }
      // a name came, went or moved: one collision pass at the next frame (a fade alone needs none)
      if moved.nonEmpty then MapLabels.schedule()

  /** Stop: every event off the map. */
  def stop(): Unit =
    reqId += 1
    if group != null && map != null then map.removeLayer(group)
    MapLabels.untrackEvents()
    group = null
    items = Vector.empty
    anchors = Map.empty
    shown.clear()
    lastFrame = None

  private def add(e: EventGeo, start: Long, a: Double): Shown =
    val outline = e.outline.filter(_ => e.scale == EventScale.Regional).map { geometry =>
      val layer = L.geoJSON(js.JSON.parse(geometry), js.Dynamic.literal(
        pane = MapPanes.Areas,
        // its look is `path.hd-event-outline`; the visibility crosses as `--a`
        style = { (_: js.Dynamic) => js.Dynamic.literal(className = "hd-event-outline", interactive = false) }
          : js.Function1[js.Dynamic, js.Dynamic],
        interactive = false
      ).asInstanceOf[js.Object])
      group.addLayer(layer)
      layer
    }
    val marker = L.marker(js.Array(e.lat, e.lon), js.Dynamic.literal(
      icon = L.divIcon(js.Dynamic.literal(
        className = "hd-event-wrap", iconSize = null, html = EventPlayback.html(e, a)).asInstanceOf[js.Object]),
      keyboard = false,
      interactive = false,
      pane = Pane,
      // a regional label under the local ones: the battle stays readable inside its war
      zIndexOffset = if e.scale == EventScale.Regional then -1000 else 1000
    ).asInstanceOf[js.Object])
    group.addLayer(marker)
    val s = Shown(marker, outline, a, 0)
    MapLabels.trackEvent(e.id, marker, EventPlayback.rank(e, start), () => nameOf(s))
    s

  private def remove(id: String, s: Shown): Unit =
    MapLabels.untrackEvent(id)
    group.removeLayer(s.marker)
    s.outline.foreach(group.removeLayer)

  /** The label's element (`.hd-event`), once Leaflet has drawn it. */
  private def labelOf(s: Shown): Option[dom.html.Element] =
    val m = s.marker.asInstanceOf[js.Dynamic].getElement()
    if js.isUndefined(m) || m == null then None
    else Option(m.asInstanceOf[dom.Element].querySelector(".hd-event")).map(_.asInstanceOf[dom.html.Element])

  /** The label's name (`.hd-area-point`): what the collision pass hides, leaving the dot. */
  private def nameOf(s: Shown): Option[dom.HTMLElement] =
    labelOf(s).flatMap(l => Option(l.querySelector(".hd-area-point"))).map(_.asInstanceOf[dom.HTMLElement])

  /** The visibility, as `--a`, on the label and on the outline's paths. */
  private def setAlpha(s: Shown, a: Double): Unit =
    labelOf(s).foreach(_.style.setProperty("--a", a.toString))
    s.outline.foreach { o =>
      o.asInstanceOf[GeoJSONLayer].getLayers().foreach { l =>
        val el = l.asInstanceOf[js.Dynamic].getElement()
        if !js.isUndefined(el) && el != null then el.asInstanceOf[dom.html.Element].style.setProperty("--a", a.toString)
      }
    }

  /** The stacking slot, as `--i`, on the label. */
  private def setSlot(s: Shown, slot: Int): Unit =
    labelOf(s).foreach(_.style.setProperty("--i", slot.toString))
