package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given

/** Mapbox vector tiles, cut by the grid MapLibre asks for (Web Mercator, 512 px a tile). */
enum TileLayer:
  case Sites, Events, Media

  def wire: com.histmersion.api.catalogue.TileLayer.WireResult = this match
    case Sites  => _root_.com.histmersion.api.catalogue.TileLayer.WireResult("sites")
    case Events => _root_.com.histmersion.api.catalogue.TileLayer.WireResult("events")
    case Media  => _root_.com.histmersion.api.catalogue.TileLayer.WireResult("media")

object TileLayer:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.catalogue.TileLayer.ParseS): Option[TileLayer] = values.find(_.wire == _root_.com.histmersion.api.catalogue.TileLayer.WireResult((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
