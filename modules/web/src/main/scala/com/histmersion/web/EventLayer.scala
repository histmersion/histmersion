package com.histmersion.web

import com.histmersion.api.{Api, MapStack}
import com.histmersion.api.MapStack
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventScale}
import com.histmersion.api.instance.EventsUiConfig

/** The events on the map while the timeline plays (#1440). Fetched ONCE when playback starts (the filter's events over
  * the span played — `/api/events/geo`); every frame after that is local: each event's visibility is computed from the
  * playhead, and only the events that appear, disappear or change visibility touch the page. A LOCAL event is a dot and
  * its label at its site, fading out over `ui.events.local-fade-seconds` of playback after it ends; a REGIONAL one is
  * its label at the region's point-on-surface and the region's faint outline for its span (an area known only by a
  * point: the label at the point).
  */
object EventLayer:

  // `var`s: a shown event's visibility and stacking slot change frame by frame, in place
  final private case class Shown(marker: DomMarker, outline: Boolean, var alpha: Double, var slot: Int)

  /** The pane the labels go in: above the finding markers, so a bubble never hides an event. */
  private def pane = TheMap.pane(MapStack.Pane.Events)

  /** The regional events' outlines, drawn by the map (`ShapeSet` "event-outlines"). */
  val OutlineSet = "event-outlines"
  private def outlines: Option[ShapeSet] = Option.when(TheMap.isUp)(
    TheMap.surface.shapes(
      OutlineSet,
      // `.hd-event-outline`'s look: the accent, dashed, faint — its strength the event's visibility
      ShapeStyle(
        fillOpacity = 0.06,
        lineWidth = 1.5,
        lineOpacity = 0.55,
        colour = Some(com.histmersion.web.ui.ThemeTokens("--accent")),
        dash = List(2.7, 2.7)
      )
    )
  )

  private var running = false
  private var config: EventsUiConfig = null
  private var items: Vector[(EventGeo, (Long, Long))] = Vector.empty
  // the events at each anchor, by start: who stacks over whom
  private var anchors: Map[String, Vector[String]] = Map.empty
  private val shown = mutable.HashMap.empty[String, Shown]
  // the outlines drawn now, by event id, with their geometry
  private val outlined = mutable.LinkedHashMap.empty[String, js.Any]
  // the events request a late answer must belong to: `stop` supersedes it
  private val latest = Latest()
  // the last frame drawn, so the events that arrive after it are drawn at once
  private var lastFrame: Option[(Long, Double)] = None

  /** Start: fetch the events of `query` and draw them at the next frame. */
  def start(cfg: EventsUiConfig, query: Api.EventQuery): Unit =
    stop()
    running = true
    config = cfg
    latest.onCurrent(ApiClient.eventsGeo(query))(_.foreach { list =>
      if running then
        items = EventPlayback.playable(list.events)
        anchors = items.sortBy(_._2._1).groupMap((e, _) => EventPlayback.anchorKey(e))((e, _) => e.id)
        lastFrame.foreach((ph, yps) => frame(ph, yps))
    })(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /** One frame: the playhead at `playhead`, playing `yearsPerSecond`. */
  def frame(playhead: Long, yearsPerSecond: Double): Unit =
    lastFrame = Some((playhead, yearsPerSecond))
    if running && config != null && TheMap.isUp then
      val localFade = EventPlayback.fadeYears(config.localFadeSeconds, yearsPerSecond)
      val regionalFade = EventPlayback.fadeYears(config.regionalFadeSeconds, yearsPerSecond)
      val moved = mutable.Set.empty[String]
      var outlinesChanged = false
      items.foreach { (e, span) =>
        val fade = if e.scale == EventScale.Regional then regionalFade else localFade
        val a = EventPlayback.step(EventPlayback.alpha(span, playhead, fade))
        shown.get(e.id) match
          case None if a > 0 =>
            val s = add(e, span._1, a)
            shown(e.id) = s
            if s.outline then outlinesChanged = true
            moved += EventPlayback.anchorKey(e)
          case Some(s) if a <= 0 =>
            remove(e.id, s)
            shown.remove(e.id)
            if s.outline then outlinesChanged = true
            moved += EventPlayback.anchorKey(e)
          case Some(s) if s.alpha != a =>
            s.alpha = a
            setAlpha(e.id, s, a)
          case _ => ()
      }
      if outlinesChanged then drawOutlines()
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
    latest.cancel()
    running = false
    if TheMap.isUp then shown.values.foreach(s => pane.remove(s.marker))
    MapLabels.untrackEvents()
    items = Vector.empty
    anchors = Map.empty
    shown.clear()
    outlined.clear()
    outlines.foreach(_.clear())
    lastFrame = None

  private def drawOutlines(): Unit =
    outlines.foreach(
      _.set(outlined.toSeq.map((id, g) => ShapeFeature(id, g, dashed = true, alpha = shown.get(id).fold(0.0)(_.alpha))))
    )

  private def add(e: EventGeo, start: Long, a: Double): Shown =
    val outline = e.outline
      .filter(_ => e.scale == EventScale.Regional)
      .flatMap { geometry =>
        scala.util.Try(js.JSON.parse(geometry)).toOption.map { g =>
          // a Feature or a bare geometry
          val geom = if !js.isUndefined(g.geometry) then g.geometry else g
          outlined(e.id) = geom
          true
        }
      }
      .getOrElse(false)
    val el = DomMarker.element("hd-event-wrap", EventPlayback.html(e, a))
    val marker = DomMarker(e.lat, e.lon, el)
    // a regional label under the local ones: the battle stays readable inside its war
    marker.setZ(if e.scale == EventScale.Regional then 1 else 2)
    pane.add(marker)
    val s = Shown(marker, outline, a, 0)
    MapLabels.trackEvent(e.id, marker, EventPlayback.rank(e, start), () => nameOf(s))
    s

  private def remove(id: String, s: Shown): Unit =
    MapLabels.untrackEvent(id)
    pane.remove(s.marker)
    outlined.remove(id)

  /** The label's element (`.hd-event`). */
  private def labelOf(s: Shown): Option[dom.html.Element] =
    Option(s.marker.el.querySelector(".hd-event")).map(_.asInstanceOf[dom.html.Element])

  /** The label's name (`.hd-area-point`): what the collision pass hides, leaving the dot. */
  private def nameOf(s: Shown): Option[dom.HTMLElement] =
    labelOf(s).flatMap(l => Option(l.querySelector(".hd-area-point"))).map(_.asInstanceOf[dom.HTMLElement])

  /** The visibility, as `--a` on the label and the outline's own. */
  private def setAlpha(id: String, s: Shown, a: Double): Unit =
    labelOf(s).foreach(_.style.setProperty("--a", a.toString))
    if s.outline then outlines.foreach(_.restyle(id, "", a))

  /** The stacking slot, as `--i`, on the label. */
  private def setSlot(s: Shown, slot: Int): Unit =
    labelOf(s).foreach(_.style.setProperty("--i", slot.toString))
