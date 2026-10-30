package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import com.histmersion.web.map.VectorStyle
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

/** The base map (docs/user-guide/exploring/web-ui.md, "The base map"). `Readable` and `Physical` are the two vector
  * styles drawn from the server's own extracts ([[BaseMaps]]); `Standard`, `Muted` and `Dark` the ONLINE raster maps —
  * `Standard` the configured tile URL (`ui.tiles`), `Dark` the same tiles drawn dark (no other tile server is
  * contacted) — offered only when `ui.base-map-source` is online.
  */
enum BaseMap(
    val wire: String,
    val label: String,
    val vector: Option[com.histmersion.web.map.VectorStyle],
    val url: Option[String]
):
  case Readable extends BaseMap("readable", "Readable", Some(com.histmersion.web.map.VectorStyle.Readable), None)
  case Physical extends BaseMap("physical", "Physical", Some(com.histmersion.web.map.VectorStyle.Physical), None)
  case Standard extends BaseMap("standard", "Standard (online)", None, None)
  case Muted
      extends BaseMap("muted", "Muted (online)", None, Some("https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"))
  case Dark extends BaseMap("dark", "Dark (online)", None, None)

  def online: Boolean = vector.isEmpty

object BaseMap:
  /** A kept choice; `terrain` is what a browser kept for the local map before the two styles: Readable. */
  def fromWire(s: String): Option[BaseMap] =
    val w = s.trim.toLowerCase
    if w == "terrain" then Some(Readable) else values.find(_.wire == w)

  val online: List[BaseMap] = values.toList.filter(_.online)
  val vectors: List[BaseMap] = values.toList.filterNot(_.online)

  /** Pure: the tone of base map `b`, when the configured tiles (`ui.tiles`) are `configured`. */
  def tone(b: BaseMap, configured: MapTone): MapTone = b match
    case Standard => configured
    case Dark     => MapTone.Dark
    case _        => MapTone.Light

  /** Pure: is base map `b` drawn by darkening light tiles? Not when the configured tiles already are dark. */
  def darkened(b: BaseMap, configured: MapTone): Boolean = b == Dark && configured == MapTone.Light
