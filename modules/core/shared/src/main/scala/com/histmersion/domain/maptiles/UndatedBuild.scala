package com.histmersion.domain.maptiles

/** What the kept counts of the undated sites were built from: the version of the writes to undated sites
  * they include, the squares a tile side they count by, the deepest zoom they hold. Counts built from
  * another version or grid are not read. */
final case class UndatedBuild(version: Long, perSide: Int, maxZoom: Int):
  /** Do they answer for the writes up to `current`, on a grid of `wantedPerSide` squares to `wantedMaxZoom`? */
  def answers(current: Long, wantedPerSide: Int, wantedMaxZoom: Int): Boolean =
    version == current && perSide == wantedPerSide && maxZoom >= wantedMaxZoom
