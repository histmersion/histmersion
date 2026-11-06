package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given

/** Mapbox vector tiles, cut by the grid MapLibre asks for (Web Mercator, 512 px a tile). */
enum TileLayer:
  case Sites, Events, Media

  def wire: com.histmersion.api.catalogue.TileLayer.Wire = this match
    case Sites  => _root_.com.histmersion.api.catalogue.TileLayer.Wire("sites")
    case Events => _root_.com.histmersion.api.catalogue.TileLayer.Wire("events")
    case Media  => _root_.com.histmersion.api.catalogue.TileLayer.Wire("media")

object TileLayer:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.catalogue.TileLayer.Wire): Option[TileLayer] = values.find(_.wire == _root_.com.histmersion.api.catalogue.TileLayer.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
