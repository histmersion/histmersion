package com.histmersion
package config

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Where the sea floor's extract comes from (`basemap.bathymetry.source`). */
@movesTo(FeaturePlace.UtilBasemap)
enum SeaFloorSource(val wire: SeaFloorSource.Wire):
  /** NOAA's ETOPO1 (1 arc-minute, ~1.8 km) through Mapzen's terrain tiles: one GET a tile. */
  case Etopo1 extends SeaFloorSource(SeaFloorSource.Wire("etopo1"))

  /** The GEBCO grid (15 arc-seconds, ~460 m): its GeoTIFFs read by range and made into tiles by the fetch. */
  case Gebco extends SeaFloorSource(SeaFloorSource.Wire("gebco"))

object SeaFloorSource:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Either[Problem, SeaFloorSource] =
    values
      .find(_.wire.value == s.value.trim.toLowerCase(java.util.Locale.ROOT))
      .toRight(
        Problem.Invalid.Unknown.here(
          Problem.TypeName.named[SeaFloorSource],
          Problem.Raw(s"$s"),
          values.toVector.map(v => Problem.Raw(s"${v.wire}"))
        )
      )
