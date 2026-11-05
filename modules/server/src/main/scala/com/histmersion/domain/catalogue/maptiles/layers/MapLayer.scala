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
  def candidates(q: TilesApi.TileQuery, window: Option[(com.histmersion.domain.catalogue.maptiles.layers.MapLayer.CandidatesWindowPart1, com.histmersion.domain.catalogue.maptiles.layers.MapLayer.CandidatesWindowPart2)], envelope: Sql): Sql

  /** How each of the items `keys` is drawn, from the cell it is drawn in; an item gone since its cell was read is
    * absent.
    */
  def features(keys: List[com.histmersion.domain.catalogue.maptiles.layers.MapLayer.FeaturesKeys]): Map[com.histmersion.domain.catalogue.maptiles.layers.MapLayer.FeaturesResultPart1, TileCell => TilePoint]

object MapLayer:
  type CandidatesWindowPart1 = CandidatesWindowPart1.T
  object CandidatesWindowPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesWindowPart2 = CandidatesWindowPart2.T
  object CandidatesWindowPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesResultPart1 = FeaturesResultPart1.T
  object FeaturesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesKeys = FeaturesKeys.T
  object FeaturesKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
