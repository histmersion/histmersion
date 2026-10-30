package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*

/** The map's point layers as Mapbox vector tiles (`GET /api/tiles/{layer}/{z}/{x}/{y}`): every site, event and picture
  * of the time span, cut by the tile grid MapLibre asks for (Web Mercator, 512 px a tile), never capped. A tile holding
  * more items than the server's `ui.map-tiles.max-points` is GROUPED: its items are counted by square cells of the
  * grid, and a cell of several items is one feature with their count, the range of their dates and their bounding box —
  * so the counts of a view always add up to every item in it. Below that, and at the deepest tile the server cuts,
  * every item is a feature of its own.
  */
enum TileLayer:
  case Sites, Events, Media

  def wire: String = this match
    case Sites  => "sites"
    case Events => "events"
    case Media  => "media"

object TileLayer:
  def parse(s: String): Option[TileLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
