package com.histmersion.web.settings

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.instance.{MapStartConfig, MapStartMode}
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.web.ApiClient
import com.histmersion.web.map.{GeoBounds, LatLon, MapSurface, TheMap}
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import com.histmersion.api.catalogue.Place

/** "Where should the map open?" — where this browser last left it, or at a place: one found by its name in the
  * gazetteer, or the map's view now. The choice is held in `choice` until the form saves it.
  */
object StartPlace:
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The zoom that shows a box of this size whole: a continent ~3, a country ~6, a city ~10. Pure. */

  type ChooserName = ChooserName.T
  object ChooserName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def zoomFor(b: GeoBounds): MapSurface.Zoom =
    val span = math.max(b.east.value - b.west.value, (b.north.value - b.south.value) * 1.4)
    if span <= 0.01 then MapSurface.Zoom(9)
    else MapSurface.Zoom(math.max(2.0, math.min(10.0, math.floor(math.log(360.0 / span) / math.log(2)) + 1)))

  /** The centre and zoom of a place's geometry (GeoJSON), when it has one. */
  def viewOf(geometry: com.histmersion.api.catalogue.Place.Geometry): Option[(LatLon, MapSurface.Zoom)] =
    scala.util.Try(js.JSON.parse(geometry.value)).toOption.flatMap(GeoBounds.ofGeometry).filter(_.valid).map { b =>
      (LatLon(LatLon.Lat((b.south.value + b.north.value) / 2), LatLon.Lon((b.west.value + b.east.value) / 2)), zoomFor(b))
    }

  /** The changes the choice writes (`ui.map-start.*`). Pure. */
  def changes(c: MapStartConfig): List[(ConfigEdit.Change.Key, ConfigEdit.Change.Value)] =
    List(
      ConfigEdit.Change.Key("ui.map-start.mode") -> ConfigEdit.Change.Value(c.mode.value),
      ConfigEdit.Change.Key("ui.map-start.lat") -> ConfigEdit.Change.Value(round(c.lat.value)),
      ConfigEdit.Change.Key("ui.map-start.lon") -> ConfigEdit.Change.Value(round(c.lon.value)),
      ConfigEdit.Change.Key("ui.map-start.zoom") -> ConfigEdit.Change.Value(round(c.zoom.value)),
      ConfigEdit.Change.Key("ui.map-start.label") -> ConfigEdit.Change.Value(c.label.value)
    )

  private def round(v: Double): String =
    BigDecimal(v).setScale(4, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

  def chooser(choice: Var[MapStartConfig], name: com.histmersion.web.settings.StartPlace.ChooserName, showLegend: Boolean = true): HtmlElement =
    val query = Var("")
    val hits = Var(List.empty[Place.Hit])
    val problem = Var(Option.empty[String])
    def search(q: String): Unit =
      query.set(q)
      if q.trim.length >= 2 then
        ApiClient.places(Some(com.histmersion.api.Api.Search.Text(q.trim)), None, Some(com.histmersion.api.Paging.Limit(6))).foreach(hs => if query.now() == q then hits.set(hs))
      else hits.set(Nil)
    def pick(h: Place.Hit): Unit =
      ApiClient.place(h.id, None).foreach { d =>
        d.geometry.flatMap(viewOf) match
          case Some((at, zoom)) =>
            choice.update(
              _.modify(_.mode).setTo(MapStartMode.Place.wire).modify(_.lat).setTo(Latitude(at.lat.value)).modify(_.lon).setTo(Longitude(at.lon.value)).modify(_.zoom).setTo(MapStartConfig.Zoom(zoom.value)).modify(_.label).setTo(MapStartConfig.Label(h.label.value))
            )
            problem.set(None); hits.set(Nil); query.set(h.label.value)
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
            value := m.wire.value,
            controlled(
              checked <-- choice.signal.map(c => MapStartMode.parse(c.mode) == m),
              onClick.mapTo(m.wire) --> (w => choice.update(_.modify(_.mode).setTo(w)))
            )
          ),
          " ",
          m.label.value
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
                  _.mode).setTo(MapStartMode.Place.wire).modify(_.lat).setTo(Latitude(s.center.lat.value)).modify(_.lon).setTo(Longitude(s.center.lon.value)).modify(_.zoom).setTo(MapStartConfig.Zoom(math.round(s.zoom.value * 10) / 10.0)).modify(_.label).setTo(MapStartConfig.Label("the view I chose")
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
                  h.label.value,
                  span(cls := "muted", s" ${com.histmersion.api.Wording.humanize(com.histmersion.api.Wording.Id(h.kind.value))}"),
                  onClick --> (_ => pick(h))
                )
              )
            )
          )
        )
      ),
      child.maybe <-- problem.signal.map(_.map(m => com.histmersion.web.ErrorLine.text(com.histmersion.web.ErrorLine.Problem(m)))),
      p(cls := "muted start-place-now", child.text <-- choice.signal.map(c => describe(c).value))
    )

  /** What the choice means, in words. Pure. */
  def describe(c: MapStartConfig): com.histmersion.web.settings.StartPlace.Description =
    val at = if c.label.value.trim.nonEmpty then c.label.value else s"${round(c.lat.value)}, ${round(c.lon.value)}"
    MapStartMode.parse(c.mode) match
      case MapStartMode.Last  => com.histmersion.web.settings.StartPlace.Description(s"The first time, and in a new browser, it opens at $at.")
      case MapStartMode.Place => com.histmersion.web.settings.StartPlace.Description(s"It opens at $at (zoom ${round(c.zoom.value)}).")
