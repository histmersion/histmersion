package com.histmersion
package web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSGlobal("maplibregl.Popup")
class GlPopup(options: js.Object) extends js.Object:
  def setLngLat(at: js.Array[Double]): this.type = js.native
  // no setHTML: a popup's markup arrives as nodes (HtmlSink.fragment), from an Html only
  def setDOMContent(content: dom.Node): this.type = js.native
  def addTo(map: GlMap): this.type = js.native
  def remove(): this.type = js.native
  def isOpen(): Boolean = js.native
  def getElement(): dom.HTMLElement = js.native
  def on(event: String, handler: js.Function1[js.Any, Unit]): this.type = js.native
