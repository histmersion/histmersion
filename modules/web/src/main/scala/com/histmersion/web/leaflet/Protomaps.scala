package com.histmersion.web.leaflet

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** protomaps-leaflet 5.1.0 (BSD-3-Clause, vendored at `web/vendor/protomaps-leaflet/`): vector tiles
  * drawn on canvas, as a Leaflet grid layer. Loaded on demand — only the terrain base map uses it
  * ([[com.histmersion.web.TerrainMap]]) — so it is reached through the global it defines. */
@js.native
@JSGlobal("protomapsL")
object Protomaps extends js.Object:
  /** A Leaflet layer: `url` (a `{z}/{x}/{y}` template or a `.pmtiles` file), `paintRules`,
    * `labelRules`, `backgroundColor`, `maxDataZoom`, `attribution`, `bounds`, any GridLayer option. */
  def leafletLayer(options: js.Object): Layer = js.native

/** A paint rule's symbolizer: a filled polygon (`fill`, `opacity`, `stroke`, `width`). */
@js.native
@JSGlobal("protomapsL.PolygonSymbolizer")
class PolygonSymbolizer(options: js.Object) extends js.Object

/** A line (`color`, `width` — a number or a function of the zoom — `opacity`). */
@js.native
@JSGlobal("protomapsL.LineSymbolizer")
class LineSymbolizer(options: js.Object) extends js.Object

/** A feature as a paint rule's filter sees it: `props` (its tags) and `geomType` (1 point, 2 line,
  * 3 polygon). */
@js.native
trait PmFeature extends js.Object:
  val props: js.Dictionary[js.Any] = js.native
  val geomType: Int                = js.native

/** Leaflet's grid layer, extended with our own `createTile` (the relief). */
@js.native
@JSGlobal("L.GridLayer")
object GridLayer extends js.Object:
  def extend(props: js.Object): js.Dynamic = js.native
