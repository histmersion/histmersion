package com.histmersion.util.basemap

/** The grid the sea floor's extract is made from, as the settings choose it. */
enum SeaFloorGrid(val wire: String):
  /** NOAA's ETOPO1 through Mapzen's terrain tiles: one GET a tile. */
  case Etopo1 extends SeaFloorGrid("etopo1")

  /** The GEBCO grid: its GeoTIFFs read by range and made into tiles by the fetch. */
  case Gebco extends SeaFloorGrid("gebco")
