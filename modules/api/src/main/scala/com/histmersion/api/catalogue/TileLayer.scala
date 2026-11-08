package com.histmersion
package api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Mapbox vector tiles, cut by the grid MapLibre asks for (Web Mercator, 512 px a tile). */
enum TileLayer:
  case Sites, Events, Media

  def wire: TileLayer.Wire = this match
    case Sites  => TileLayer.Wire("sites")
    case Events => TileLayer.Wire("events")
    case Media  => TileLayer.Wire("media")

object TileLayer:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]

  def parse(s: Wire): Option[TileLayer] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
