package com.histmersion
package api.catalogue
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Mapbox vector tiles, cut by the grid MapLibre asks for (Web Mercator, 512 px a tile). */
enum TileLayer:
  case Sites, Events, Media

  def wire: TileLayer.Wire = this match
    case Sites  => TileLayer.Wire("sites")
    case Events => TileLayer.Wire("events")
    case Media  => TileLayer.Wire("media")

object TileLayer:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[TileLayer] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
