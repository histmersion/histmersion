package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.VectorStyle
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.basemap.BaseMapSource
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The base map (docs/user-guide/exploring/web-ui.md, "The base map"). `Readable` and `Physical` are the two vector
  * styles drawn from the server's own extracts ([[BaseMaps]]); `Standard`, `Muted` and `Dark` the ONLINE raster maps —
  * `Standard` the configured tile URL (`ui.tiles`), `Dark` the same tiles drawn dark (no other tile server is
  * contacted) — offered only when `ui.base-map-source` is online.
  */
enum BaseMap(
    val wire: BaseMap.Wire,
    val label: BaseMap.Label,
    val vector: Option[VectorStyle],
    val url: Option[BaseMap.Url]
) derives CanEqual:
  case Readable extends BaseMap(BaseMap.Wire("readable"), BaseMap.Label("Readable"), Some(VectorStyle.Readable), None)
  case Physical extends BaseMap(BaseMap.Wire("physical"), BaseMap.Label("Physical"), Some(VectorStyle.Physical), None)
  case Standard extends BaseMap(BaseMap.Wire("standard"), BaseMap.Label("Standard (online)"), None, None)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  case Muted
      extends BaseMap(
        BaseMap.Wire("muted"),
        BaseMap.Label("Muted (online)"),
        None,
        Some(BaseMap.Url(BaseMapSource.MutedTiles.value))
      )
  case Dark extends BaseMap(BaseMap.Wire("dark"), BaseMap.Label("Dark (online)"), None, None)

  def online: Boolean = vector.isEmpty

object BaseMap:
  /** A kept choice; `terrain` is what a browser kept for the local map before the two styles: Readable. */
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromWire(s: Wire): Option[BaseMap] =
    val w = s.value.trim.toLowerCase
    if w == "terrain" then Some(Readable) else values.find(_.wire.value == w)

  val online: List[BaseMap] = values.toList.filter(_.online)
  val vectors: List[BaseMap] = values.toList.filterNot(_.online)

  /** Pure: the tone of base map `b`, when the configured tiles (`ui.tiles`) are `configured`. */
  def tone(b: BaseMap, configured: MapTone): MapTone = b match
    case Standard => configured
    case Dark     => MapTone.Dark
    case _        => MapTone.Light

  /** Pure: is base map `b` drawn by darkening light tiles? Not when the configured tiles already are dark. */
  def darkened(b: BaseMap, configured: MapTone): Boolean = b == Dark && configured == MapTone.Light
