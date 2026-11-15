package com.histmersion
package util.basemap

import io.github.iltotore.iron.constraint.any.Pure

/** The grid the sea floor's extract is made from, as the settings choose it. */
enum SeaFloorGrid(val wire: SeaFloorGrid.Wire):
  /** NOAA's ETOPO1 through Mapzen's terrain tiles: one GET a tile. */
  case Etopo1 extends SeaFloorGrid(SeaFloorGrid.Wire("etopo1"))

  /** The GEBCO grid: its GeoTIFFs read by range and made into tiles by the fetch. */
  case Gebco extends SeaFloorGrid(SeaFloorGrid.Wire("gebco"))

object SeaFloorGrid:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
