package com.histmersion.web.settings

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.instance.{MapStartConfig, MapStartMode}
import com.histmersion.web.ApiClient
import com.histmersion.web.map.{GeoBounds, LatLon, TheMap}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.catalogue.Place

/** "Where should the map open?" — where this browser last left it, or at a place: one found by its name in the
  * gazetteer, or the map's view now. The choice is held in `choice` until the form saves it.
  */
object StartPlace:

  /** The zoom that shows a box of this size whole: a continent ~3, a country ~6, a city ~10. Pure. */
  type ChooserSearchQ = ChooserSearchQ.T
  object ChooserSearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ZoomForResult = ZoomForResult.T
  object ZoomForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewOfResultPart2 = ViewOfResultPart2.T
  object ViewOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewOfGeometry = ViewOfGeometry.T
  object ViewOfGeometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangesResultPart1 = ChangesResultPart1.T
  object ChangesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangesResultPart2 = ChangesResultPart2.T
  object ChangesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundResult = RoundResult.T
  object RoundResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundV = RoundV.T
  object RoundV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooserName = ChooserName.T
  object ChooserName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooserShowLegend = ChooserShowLegend.T
  object ChooserShowLegend extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def zoomFor(b: GeoBounds): com.histmersion.web.settings.StartPlace.ZoomForResult =
    val span = math.max(b.east - b.west, (b.north - b.south) * 1.4)
    if span <= 0.01 then 9
    else math.max(2.0, math.min(10.0, math.floor(math.log(360.0 / span) / math.log(2)) + 1))

  /** The centre and zoom of a place's geometry (GeoJSON), when it has one. */
  def viewOf(geometry: com.histmersion.web.settings.StartPlace.ViewOfGeometry): Option[(LatLon, com.histmersion.web.settings.StartPlace.ViewOfResultPart2)] =
    scala.util.Try(js.JSON.parse(geometry)).toOption.flatMap(GeoBounds.ofGeometry).filter(_.valid).map { b =>
      (LatLon((b.south + b.north) / 2, (b.west + b.east) / 2), zoomFor(b))
    }

  /** The changes the choice writes (`ui.map-start.*`). Pure. */
  def changes(c: MapStartConfig): List[(com.histmersion.web.settings.StartPlace.ChangesResultPart1, com.histmersion.web.settings.StartPlace.ChangesResultPart2)] =
    List(
      "ui.map-start.mode" -> c.mode,
      "ui.map-start.lat" -> round(c.lat),
      "ui.map-start.lon" -> round(c.lon),
      "ui.map-start.zoom" -> round(c.zoom),
      "ui.map-start.label" -> c.label
    )

  private def round(v: com.histmersion.web.settings.StartPlace.RoundV): com.histmersion.web.settings.StartPlace.RoundResult =
    BigDecimal(v).setScale(4, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

  def chooser(choice: Var[MapStartConfig], name: com.histmersion.web.settings.StartPlace.ChooserName, showLegend: com.histmersion.web.settings.StartPlace.ChooserShowLegend = true): HtmlElement =
    val query = Var("")
    val hits = Var(List.empty[Place.Hit])
    val problem = Var(Option.empty[String])
    def search(q: com.histmersion.web.settings.StartPlace.ChooserSearchQ): Unit =
      query.set(q)
      if q.trim.length >= 2 then
        ApiClient.places(Some(q.trim), None, Some(6)).foreach(hs => if query.now() == q then hits.set(hs))
      else hits.set(Nil)
    def pick(h: Place.Hit): Unit =
      ApiClient.place(h.id, None).foreach { d =>
        d.geometry.flatMap(viewOf) match
          case Some((at, zoom)) =>
            choice.update(
              _.modify(_.mode).setTo(MapStartMode.Place.wire).modify(_.lat).setTo(at.lat).modify(_.lon).setTo(at.lon).modify(_.zoom).setTo(zoom).modify(_.label).setTo(h.label)
            )
            problem.set(None); hits.set(Nil); query.set(h.label)
          case None => problem.set(Some(s"${h.label} has no location on the map; choose another place."))
      }
    fieldSet(
      cls := "start-place",
      legend(cls := (if showLegend then "start-place-legend" else "sr-only"), "Where should the map open?"),
      MapStartMode.values.toList.map { m =>
        label(
          cls := "start-place-mode",
          input(
            typ := "radio",
            nameAttr := s"$name-mode",
            value := m.wire,
            controlled(
              checked <-- choice.signal.map(c => MapStartMode.parse(c.mode) == m),
              onClick.mapTo(m.wire) --> (w => choice.update(_.modify(_.mode).setTo(w)))
            )
          ),
          " ",
          m.label
        )
      },
      div(
        cls := "start-place-find",
        label(forId := s"$name-place", cls := "sr-only", "A place the map opens at"),
        input(
          typ := "search",
          idAttr := s"$name-place",
          cls := "field start-place-input",
          placeholder := "Find a place: Anatolia, the Andes, Mali…",
          controlled(value <-- query.signal, onInput.mapToValue --> search)
        ),
        Option.when(TheMap.isUp)(
          button(
            typ := "button",
            cls := "btn start-place-here",
            "Use the map's view now",
            onClick --> { _ =>
              val s = TheMap.surface
              choice.update(
                _.modify(
                  _.mode).setTo(MapStartMode.Place.wire).modify(_.lat).setTo(s.center.lat).modify(_.lon).setTo(s.center.lon).modify(_.zoom).setTo(math.round(s.zoom * 10) / 10.0).modify(_.label).setTo("the view I chose"
                )
              )
            }
          )
        ),
        ul(
          cls := "start-place-hits",
          children <-- hits.signal.map(
            _.map(h =>
              li(
                button(
                  typ := "button",
                  cls := "btn btn--bare start-place-hit",
                  h.label,
                  span(cls := "muted", s" ${com.histmersion.api.Wording.humanize(h.kind)}"),
                  onClick --> (_ => pick(h))
                )
              )
            )
          )
        )
      ),
      child.maybe <-- problem.signal.map(_.map(com.histmersion.web.ErrorLine.text)),
      p(cls := "muted start-place-now", child.text <-- choice.signal.map(describe))
    )

  /** What the choice means, in words. Pure. */
  def describe(c: MapStartConfig): com.histmersion.web.settings.StartPlace.DescribeResult =
    val at = if c.label.trim.nonEmpty then c.label else s"${round(c.lat)}, ${round(c.lon)}"
    MapStartMode.parse(c.mode) match
      case MapStartMode.Last  => s"The first time, and in a new browser, it opens at $at."
      case MapStartMode.Place => s"It opens at $at (zoom ${round(c.zoom)})."
