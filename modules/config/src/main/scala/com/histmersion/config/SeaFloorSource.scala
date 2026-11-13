package com.histmersion
package config

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** Where the sea floor's extract comes from (`basemap.bathymetry.source`). */
enum SeaFloorSource(val wire: SeaFloorSource.Wire) derives CanEqual:
  /** NOAA's ETOPO1 (1 arc-minute, ~1.8 km) through Mapzen's terrain tiles: one GET a tile. */
  case Etopo1 extends SeaFloorSource(SeaFloorSource.Wire("etopo1"))

  /** The GEBCO grid (15 arc-seconds, ~460 m): its GeoTIFFs read by range and made into tiles by the fetch. */
  case Gebco extends SeaFloorSource(SeaFloorSource.Wire("gebco"))

object SeaFloorSource:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Either[Problem, SeaFloorSource] =
    values
      .find(_.wire.value == s.value.trim.toLowerCase(java.util.Locale.ROOT))
      .toRight(
        Problem.Invalid.Unknown(
          Problem.TypeName.named[SeaFloorSource],
          Problem.Raw(s"$s"),
          values.toVector.map(v => Problem.Raw(s"${v.wire}"))
        )
      )
