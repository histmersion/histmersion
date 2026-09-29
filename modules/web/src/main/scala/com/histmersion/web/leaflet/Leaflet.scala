package com.histmersion.web.leaflet

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom

@js.native
trait LatLng extends js.Object:
  val lat: Double = js.native
  val lng: Double = js.native

@js.native
trait LatLngBounds extends js.Object:
  def getSouth(): Double     = js.native
  def getWest(): Double      = js.native
  def getNorth(): Double     = js.native
  def getEast(): Double      = js.native
  def contains(latlng: LatLng): Boolean    = js.native
  def extend(other: LatLngBounds): LatLngBounds = js.native
  def isValid(): Boolean     = js.native

@js.native
trait LeafletEvent extends js.Object:
  val target: js.Dynamic = js.native
  val layer: js.Dynamic  = js.native

@js.native
trait Layer extends js.Object:
  def addTo(map: LeafletMap): this.type       = js.native
  def addTo(group: LayerGroup): this.type     = js.native
  def remove(): this.type                     = js.native
  def bindPopup(content: String): this.type   = js.native
  def bindPopup(content: dom.Element): this.type = js.native
  def bindTooltip(content: String, options: js.UndefOr[js.Object] = js.undefined): this.type = js.native
  def openPopup(): this.type                  = js.native
  def on(event: String, handler: js.Function1[LeafletEvent, Unit]): this.type = js.native

@js.native
trait LeafletMap extends js.Object:
  def setView(center: js.Array[Double], zoom: Int): this.type = js.native
  def fitBounds(bounds: LatLngBounds, options: js.UndefOr[js.Object] = js.undefined): this.type = js.native
  def fitBounds(bounds: js.Array[js.Array[Double]]): this.type = js.native
  def getZoom(): Int                           = js.native
  def getBounds(): LatLngBounds                = js.native
  def getCenter(): LatLng                      = js.native
  def getContainer(): dom.Element              = js.native
  def on(event: String, handler: js.Function1[LeafletEvent, Unit]): this.type = js.native
  def invalidateSize(): this.type              = js.native
  def removeLayer(layer: Layer): this.type     = js.native
  def hasLayer(layer: Layer): Boolean          = js.native

@js.native
trait TileLayer extends Layer

@js.native
trait Marker extends Layer:
  def getLatLng(): LatLng                      = js.native
  def setLatLng(latlng: js.Array[Double]): this.type = js.native

@js.native
trait CircleMarker extends Layer:
  def getLatLng(): LatLng                      = js.native
  def setRadius(r: Double): this.type          = js.native

@js.native
trait LayerGroup extends Layer:
  def addLayer(layer: Layer): this.type        = js.native
  def addLayers(layers: js.Array[Layer]): this.type = js.native
  def removeLayer(layer: Layer): this.type     = js.native
  def clearLayers(): this.type                 = js.native
  def getLayers(): js.Array[Layer]              = js.native

@js.native
trait MarkerClusterGroup extends LayerGroup:
  def getAllChildMarkers(): js.Array[Marker]    = js.native

@js.native
trait GeoJSONLayer extends LayerGroup:
  def getBounds(): LatLngBounds                = js.native

@js.native
trait DivIcon extends js.Object

@js.native
trait LeafletControl extends js.Object:
  def addTo(map: LeafletMap): this.type        = js.native

@js.native
@JSGlobal("L")
object L extends js.Object:
  def map(id: String | dom.Element, options: js.UndefOr[js.Object] = js.undefined): LeafletMap = js.native
  def tileLayer(url: String, options: js.UndefOr[js.Object] = js.undefined): TileLayer = js.native
  def marker(latlng: js.Array[Double], options: js.UndefOr[js.Object] = js.undefined): Marker = js.native
  def circleMarker(latlng: js.Array[Double], options: js.UndefOr[js.Object] = js.undefined): CircleMarker = js.native
  def layerGroup(): LayerGroup                 = js.native
  def latLng(lat: Double, lng: Double): LatLng = js.native
  def latLngBounds(sw: js.Array[Double], ne: js.Array[Double]): LatLngBounds = js.native
  def markerClusterGroup(options: js.UndefOr[js.Object] = js.undefined): MarkerClusterGroup = js.native
  def divIcon(options: js.Object): DivIcon     = js.native
  def control(options: js.Object): LeafletControl = js.native
  def geoJSON(data: js.Any, options: js.UndefOr[js.Object] = js.undefined): GeoJSONLayer = js.native

@js.native
@JSGlobal("L.DomUtil")
object DomUtil extends js.Object:
  def create(tag: String, className: String): dom.Element = js.native

@js.native
@JSGlobal("L.DomEvent")
object DomEvent extends js.Object:
  def disableClickPropagation(el: dom.Element): Unit = js.native
