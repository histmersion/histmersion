package com.histmersion
package web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSGlobal("maplibregl.Map")
class GlMap(options: js.Object) extends js.Object:
  def on(event: String, handler: js.Function1[GlEvent, Unit]): this.type = js.native
  def off(event: String, handler: js.Function1[GlEvent, Unit]): this.type = js.native
  def once(event: String, handler: js.Function1[GlEvent, Unit]): this.type = js.native
  def getZoom(): Double = js.native
  def getCenter(): LngLat = js.native
  def getBounds(): LngLatBounds = js.native
  def getMaxZoom(): Double = js.native
  def setMaxZoom(z: Double): this.type = js.native
  def jumpTo(options: js.Object): this.type = js.native
  def easeTo(options: js.Object): this.type = js.native
  def fitBounds(bounds: js.Array[js.Array[Double]], options: js.UndefOr[js.Object] = js.undefined): this.type =
    js.native
  def panBy(offset: js.Array[Double], options: js.UndefOr[js.Object] = js.undefined): this.type = js.native
  def project(lngLat: js.Array[Double]): ScreenPoint = js.native
  def unproject(point: js.Array[Double]): LngLat = js.native
  def getContainer(): dom.HTMLElement = js.native
  def getCanvasContainer(): dom.HTMLElement = js.native
  def getCanvas(): dom.HTMLCanvasElement = js.native
  def resize(): this.type = js.native
  def redraw(): this.type = js.native
  def remove(): Unit = js.native
  def isStyleLoaded(): Boolean = js.native
  def loaded(): Boolean = js.native
  def isMoving(): Boolean = js.native
  def areTilesLoaded(): Boolean = js.native
  def setStyle(style: js.Any, options: js.UndefOr[js.Object] = js.undefined): this.type = js.native
  def getStyle(): js.Dynamic = js.native
  def addSource(id: String, source: js.Object): this.type = js.native
  def getSource(id: String): js.UndefOr[js.Dynamic] = js.native
  def removeSource(id: String): this.type = js.native
  def addLayer(layer: js.Object, before: js.UndefOr[String] = js.undefined): this.type = js.native
  def getLayer(id: String): js.UndefOr[js.Dynamic] = js.native
  def removeLayer(id: String): this.type = js.native
  def setPaintProperty(layer: String, name: String, value: js.Any): this.type = js.native
  def setLayoutProperty(layer: String, name: String, value: js.Any): this.type = js.native
  def getLayoutProperty(layer: String, name: String): js.Any = js.native
  def setFilter(layer: String, filter: js.Any): this.type = js.native
  def setFeatureState(feature: js.Object, state: js.Object): this.type = js.native
  def queryRenderedFeatures(
      point: js.Array[Double],
      options: js.UndefOr[js.Object] = js.undefined
  ): js.Array[RenderedFeature] = js.native

  /** The features of a source's tiles held for the view (a vector source: of one `sourceLayer`). */
  def querySourceFeatures(source: String, options: js.UndefOr[js.Object] = js.undefined): js.Array[js.Dynamic] =
    js.native

  /** Every tile the source needs for the view is in. */
  def isSourceLoaded(id: String): Boolean = js.native
  def addControl(control: js.Any, position: js.UndefOr[String] = js.undefined): this.type = js.native
  def removeControl(control: js.Any): this.type = js.native
  def triggerRepaint(): Unit = js.native

  /** The map's own handlers (drag, zoom), each with `enable()`/`disable()`. */
  val touchZoomRotate: js.Dynamic = js.native
  val dragRotate: js.Dynamic = js.native
  val keyboard: js.Dynamic = js.native
  val dragPan: js.Dynamic = js.native
  val scrollZoom: js.Dynamic = js.native
  val boxZoom: js.Dynamic = js.native
  val doubleClickZoom: js.Dynamic = js.native
