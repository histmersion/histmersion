package com.histmersion
package domain.catalogue.maptiles

/** A layer's tile features, from its cells and the records of its items ([[com.histmersion.api.catalogue.TileProps]]).
  * Pure.
  */
import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object TileFeatures:
  import com.histmersion.api.catalogue.{EventGeo, SiteTile, TileProps as P}
