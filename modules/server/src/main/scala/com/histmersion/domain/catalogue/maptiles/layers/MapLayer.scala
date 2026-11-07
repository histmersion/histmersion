package com.histmersion.domain.catalogue.maptiles.layers

import com.histmersion.api.catalogue.TilesApi
import com.histmersion.util.database.Sql
import com.histmersion.domain.catalogue.maptiles.{TileCell, TilePoint}

/** A point layer of the map that another domain draws: the map cuts its tiles, the domain says which of its items lie
  * in a tile's box and how a drawn item reads.
  */
trait MapLayer:
  /** The SQL selecting the layer's items in the box `envelope` under the tile's query `q` (its span `window`, if any):
    * the columns `key`, `lon`, `lat`, `ps`, `pe` (the item's dates, if any) and `ub` (whether it has no known date).
    */
  def candidates(q: TilesApi.TileQuery, window: Option[(com.histmersion.domain.catalogue.periods.BPeriodRow.PLo, com.histmersion.domain.catalogue.periods.BPeriodRow.PHi)], envelope: Sql): Sql

  /** How each of the items `keys` is drawn, from the cell it is drawn in; an item gone since its cell was read is
    * absent.
    */
  def features(keys: List[com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey]): Map[com.histmersion.domain.catalogue.maptiles.MapTiles.TileKey, TileCell => TilePoint]

