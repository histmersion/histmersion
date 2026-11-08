package com.histmersion
package web

import com.histmersion.api.{Api, MapStack}
import com.histmersion.api.MapStack
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventScale}
import com.histmersion.api.instance.EventsUiConfig
import com.histmersion.web.ui.ThemeTokens
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Event

/** The events on the map while the timeline plays (#1440). Fetched ONCE when playback starts (the filter's events over
  * the span played — `/api/events/geo`); every frame after that is local: each event's visibility is computed from the
  * playhead, and only the events that appear, disappear or change visibility touch the page. A LOCAL event is a dot and
  * its label at its site, fading out over `ui.events.local-fade-seconds` of playback after it ends; a REGIONAL one is
  * its label at the region's point-on-surface and the region's faint outline for its span (an area known only by a
  * point: the label at the point).
  */
object EventLayer:
  export EventPlayback.{Alpha, Slot, YearsPerSecond}

  // `var`s: a shown event's visibility and stacking slot change frame by frame, in place

  final private case class Shown(marker: DomMarker, outline: Boolean, var alpha: Alpha, var slot: Slot)

  /** The pane the labels go in: above the finding markers, so a bubble never hides an event. */
  private def pane = TheMap.pane(MapStack.Pane.Events)

  /** The regional events' outlines, drawn by the map (`ShapeSet` "event-outlines"). */
  val OutlineSet = "event-outlines"
  private def outlines: Option[ShapeSet] = Option.when(TheMap.isUp)(
    TheMap.surface.shapes(
      MapSurface.ShapesId(OutlineSet),
      // `.hd-event-outline`'s look: the accent, dashed, faint — its strength the event's visibility
      ShapeStyle(
        fillOpacity = ShapeStyle.FillOpacity(0.06),
        lineWidth = ShapeStyle.LineWidth(1.5),
        lineOpacity = ShapeStyle.LineOpacity(0.55),
        colour = Some(ShapeStyle.Colour(ThemeTokens(ThemeTokens.Name("--accent")).value)),
        dash = List(ShapeStyle.Dash(2.7), ShapeStyle.Dash(2.7))
      )
    )
  )

  private var running = false
  private var config: EventsUiConfig = null
  private var items: Vector[(EventGeo, (Year, Year))] = Vector.empty
  // the events at each anchor, by start: who stacks over whom
  private var anchors: Map[EventPlayback.AnchorKey, Vector[Event.Id]] = Map.empty
  private val shown = mutable.HashMap.empty[Event.Id, Shown]
  // the outlines drawn now, by event id, with their geometry
  private val outlined = mutable.LinkedHashMap.empty[Event.Id, js.Any]
  // the events request a late answer must belong to: `stop` supersedes it
  private val latest = Latest()
  // the last frame drawn, so the events that arrive after it are drawn at once
  private var lastFrame: Option[(Year, YearsPerSecond)] = None

  /** Start: fetch the events of `query` and draw them at the next frame. */
  def start(cfg: EventsUiConfig, query: Api.EventQuery): Unit =
    stop()
    running = true
    config = cfg
    latest.onCurrent(ApiClient.eventsGeo(query))(_.foreach { list =>
      if running then
        items = EventPlayback.playable(list.events)
        anchors = items.sortBy(t => t._2._1.value: Long).groupMap((e, _) => EventPlayback.anchorKey(e))((e, _) => e.id)
        lastFrame.foreach((ph, yps) => frame(ph, yps))
    })(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  /** One frame: the playhead at `playhead`, playing `yearsPerSecond`. */
  def frame(playhead: Year, yearsPerSecond: YearsPerSecond): Unit =
    lastFrame = Some((playhead, yearsPerSecond))
    if running && config != null && TheMap.isUp then
      val localFade = EventPlayback.fadeYears(EventPlayback.Seconds(config.localFadeSeconds.value), yearsPerSecond)
      val regionalFade = EventPlayback.fadeYears(EventPlayback.Seconds(config.regionalFadeSeconds.value), yearsPerSecond)
      val moved = mutable.Set.empty[EventPlayback.AnchorKey]
      var outlinesChanged = false
      items.foreach { (e, span) =>
        val fade = if e.scale.value == EventScale.Regional then regionalFade else localFade
        val a = EventPlayback.step(EventPlayback.alpha(span, playhead, fade))
        shown.get(e.id) match
          case None if a.value > 0 =>
            val s = add(e, span._1, a)
            shown(e.id) = s
            if s.outline then outlinesChanged = true
            moved += EventPlayback.anchorKey(e)
          case Some(s) if a.value <= 0 =>
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
      _.set(outlined.toSeq.map((id, g) => ShapeFeature(ShapeFeature.Id(id.value), g, dashed = ShapeFeature.Dashed(true), alpha = ShapeFeature.Alpha(shown.get(id).fold(0.0)(_.alpha.value)))))
    )

  private def add(e: EventGeo, start: Year, a: Alpha): Shown =
    val outline = e.outline
      .filter(_ => e.scale.value == EventScale.Regional)
      .flatMap { geometry =>
        scala.util.Try(js.JSON.parse(geometry.value)).toOption.map { g =>
          // a Feature or a bare geometry
          val geom = if !js.isUndefined(g.geometry) then g.geometry else g
          outlined(e.id) = geom
          true
        }
      }
      .getOrElse(false)
    val el = DomMarker.element("hd-event-wrap", EventPlayback.html(e, a).value)
    val marker = new DomMarker(LatLon.Lat(e.lat.value), LatLon.Lon(e.lon.value), el)
    // a regional label under the local ones: the battle stays readable inside its war
    marker.setZ(if e.scale.value == EventScale.Regional then DomMarker.ZIndex(1) else DomMarker.ZIndex(2))
    pane.add(marker)
    val s = Shown(marker, outline, a, Slot(0))
    MapLabels.trackEvent(e.id, marker, MapLabels.Entry.Rank(EventPlayback.rank(e, start).value), () => nameOf(s))
    s

  private def remove(id: Event.Id, s: Shown): Unit =
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
  private def setAlpha(id: Event.Id, s: Shown, a: Alpha): Unit =
    labelOf(s).foreach(_.style.setProperty("--a", a.toString))
    if s.outline then outlines.foreach(_.restyle(ShapeFeature.Id(id.value), ShapeFeature.Colour(""), ShapeFeature.Alpha(a.value)))

  /** The stacking slot, as `--i`, on the label. */
  private def setSlot(s: Shown, slot: Slot): Unit =
    labelOf(s).foreach(_.style.setProperty("--i", slot.toString))
