package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*

/** Mapbox vector tiles, cut by the grid MapLibre asks for (Web Mercator, 512 px a tile). */
enum TileLayer:
  case Sites, Events, Media

  def wire: String = this match
    case Sites  => "sites"
    case Events => "events"
    case Media  => "media"

object TileLayer:
  def parse(s: String): Option[TileLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
