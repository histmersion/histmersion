package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** THE MAP, as the app sees it (docs/user-guide/contributing/architecture.md, "The map"): a thin layer shaped by what
  * the app needs, so that changing the map library changes one package (the adapter `map.maplibre`, the only one that
  * may name a map library's types — `MapEngineBoundarySuite` fails the build otherwise). Everything else talks to these
  * types:
  *
  *   - [[MapSurface]]: the view (centre, zoom, bounds, fitting, panning), its events, projection, the base map
  *     ([[BaseMapSpec]]), the corners for controls, popups, the GL-drawn shapes ([[ShapeSet]]) and what is under the
  *     pointer;
  *   - [[MapEngine]]: makes a surface, and says before that what the browser can draw ([[Capabilities]]);
  *   - the points, bubbles and names are DOM markers in panes over the map ([[MarkerPane]]), placed with the surface's
  *     projection — library-neutral, so they keep the cards, the keyboard and the stylesheet's look whatever draws the
  *     base map.
  *
  * ZOOM is the app's unit: the zoom of the 256-pixel raster grid (OSM Carto's, every `Layout` value and link the app
  * had). An adapter converts to its library's own.
  */
final case class LatLon(lat: LatLon.Lat, lon: LatLon.Lon)

object LatLon:
  type Lat = Lat.T
  object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lon = Lon.T
  object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
