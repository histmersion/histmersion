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

  private def layerButton(icon: String, lbl: String, on: Var[Boolean], toggle: () => Unit): HtmlElement =
    button(
      cls <-- on.signal.map(v => if v then "layer-btn active" else "layer-btn"),
      title := lbl,
      span(cls := "layer-icon", icon),
      span(cls := "layer-label", lbl),
      onClick --> { _ => toggle() }
    )

  private val mapStyle = Var("standard")
  private val MAP_STYLES: List[(String, String, String)] = List(
    ("standard", "Standard", ""),
    ("muted", "Muted", "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png")
  )

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
    val checked = Var(false)
    var timer: Int = 0

    // Restore state from URL hash.
    val initialInView = UiState.inView.now()
    if initialInView then checked.set(true)

    val overlayOn = Var(MapView.isOverlayOn)
    val sitesOn = Var(false)
    val mediaOn = Var(false)

    div(
      cls := "viewport-toggle",
      div(
        cls := "layer-toggles",
        layerButton("⬡", "boundaries", overlayOn, () => { MapView.toggleOverlay(); overlayOn.set(MapView.isOverlayOn) }),
        layerButton("🏛", "sites", sitesOn, () => { MapView.toggleSites(); sitesOn.update(!_) }),
        layerButton("🖼", "media", mediaOn, () => { MapView.toggleMedia(); mediaOn.update(!_) }),
        select(
          cls := "map-style-select",
          controlled(
            value <-- mapStyle.signal,
            onChange.mapToValue --> { v =>
              mapStyle.set(v)
              MAP_STYLES.find(_._1 == v).foreach { (_, _, url) =>
                val tileUrl = if url.isEmpty then config.tiles else url
                MapView.setTileUrl(tileUrl, config.tilesMaxZoom)
              }
            }
          ),
          MAP_STYLES.map { (key, label, _) =>
            option(value := key, label)
          }
        )
      ),
      label(
        cls := "check",
        input(
          typ := "checkbox",
          idAttr := "inview",
          controlled(
            com.raquo.laminar.api.L.checked <-- checked.signal,
            onInput.mapToChecked --> { isChecked =>
              checked.set(isChecked)
              dom.window.clearTimeout(timer)
              val map = MapView.leafletMap
              if isChecked then
                UiState.set(("inView", true), ("bbox", bboxOf(map)))()
              else
                UiState.set(("inView", false), ("bbox", Option.empty[String]))()
            }
          )
        ),
        span("only sources in view")
      ),
      onMountCallback { ctx =>
        val map = MapView.leafletMap
        if map != null then
          // Push bbox on map moveend/zoomend when the checkbox is checked.
          val handler: js.Function1[LeafletEvent, Unit] = { (_: LeafletEvent) =>
            if checked.now() then
              dom.window.clearTimeout(timer)
              timer = dom.window.setTimeout(() => {
                UiState.set(("bbox", bboxOf(map)))()
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
              UiState.set(("bbox", bboxOf(map)))()
      }
    )
