package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.scalajs.js

/** "Only sources in view" toggle: mirrors the Leaflet viewport into UiState.bbox so the server-side
  * filter and the shareable URL hash always agree with what the user is looking at.
  *
  * Ported from viewport.js. */
object ViewportToggle:

  private def r5(x: Double): Double = Math.round(x * 1e5) / 1e5

  /** Wrap longitude to [-180, 180]. Leaflet can produce unwrapped values past the antimeridian. */
  private def wrapLon(x: Double): Double =
    val y = ((x + 180) % 360 + 360) % 360 - 180
    if y == -180 && x > 0 then 180.0 else y

  /** Map bounds as "minLon,minLat,maxLon,maxLat", normalised to +/-180/+/-90.
    * A viewport straddling the antimeridian comes back with minLon > maxLon; the server splits it
    * into two envelopes. A viewport wider than the world becomes the whole world. */
  private def bboxOf(map: LeafletMap): String =
    val b = map.getBounds()
    val w = b.getWest()
    val e = b.getEast()
    val s = Math.max(-90.0, b.getSouth())
    val n = Math.min(90.0, b.getNorth())
    val lons = if e - w >= 360 then (-180.0, 180.0)
               else (wrapLon(w), wrapLon(e))
    s"${r5(lons._1)},${r5(s)},${r5(lons._2)},${r5(n)}"

  def init(config: UiConfig): HtmlElement =
    // `var`: the pending debounce timer of the moveend handler below
    var timer: Int = 0
    val initialInView = UiState.inView.now()

    def setInView(on: Boolean): Unit =
      dom.window.clearTimeout(timer)
      if on then UiState.set(UiState.inView := true, UiState.bbox := Some(bboxOf(MapView.leafletMap)))()
      else UiState.set(UiState.inView := false, UiState.bbox := None)()

    div(
      cls := "viewport-toggle",
      div(
        cls := "layer-toggles",
        // the finding markers first: they are what the map is for, the overlays come after
        LayerToggle(Icon.Sources, "sources", UiState.sources.signal, v => UiState.set(UiState.sources := v)()),
        MapLayer.values.toList.map(l => LayerToggle(l.icon, l.label, UiState.layerOn(l), UiState.setLayer(l, _))),
        select(
          cls := "map-style-select",
          aria.label := "base map",
          controlled(
            value <-- UiState.baseMap.signal.map(_.wire),
            onChange.mapToValue --> { v => BaseMap.fromWire(v).foreach(b => UiState.set(UiState.baseMap := b)()) }
          ),
          BaseMap.values.toList.map(b => option(value := b.wire, b.label))
        )
      ),
      // a highlighted toggle like the layers, not a checkbox: it shows UiState.inView itself
      LayerToggle(Icon.InView, "only sources in view", UiState.inView.signal, setInView),
      onMountCallback { ctx =>
        val map = MapView.leafletMap
        if map != null then
          // Push bbox on map moveend/zoomend when the checkbox is checked.
          val handler: js.Function1[LeafletEvent, Unit] = { (_: LeafletEvent) =>
            if UiState.inView.now() then
              dom.window.clearTimeout(timer)
              timer = dom.window.setTimeout(() => {
                UiState.set(UiState.bbox := Some(bboxOf(map)))()
              }, config.viewportDebounceMs)
          }
          map.on("moveend", handler)
          map.on("zoomend", handler)

          // If restoring from URL hash with inView = true, fit the map to the saved bbox.
          if initialInView then
            val bboxStr = UiState.bbox.now().getOrElse("")
            val parts   = bboxStr.split(",").flatMap(_.toDoubleOption)
            if parts.length == 4 && parts.forall(java.lang.Double.isFinite) then
              // minLon > maxLon means the saved viewport crossed the antimeridian.
              // Leaflet needs a monotonically increasing east bound.
              val east = if parts(0) > parts(2) then parts(2) + 360 else parts(2)
              map.fitBounds(js.Array(
                js.Array(parts(1), parts(0)),
                js.Array(parts(3), east)
              ))
            else
              UiState.set(UiState.bbox := Some(bboxOf(map)))()
      }
    )
