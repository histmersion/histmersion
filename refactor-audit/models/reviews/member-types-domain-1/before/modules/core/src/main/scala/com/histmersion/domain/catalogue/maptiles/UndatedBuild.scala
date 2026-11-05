package com.histmersion.domain.catalogue.maptiles

/** What the kept counts of the undated sites were built from: the version of the writes to undated sites they include,
  * the squares a tile side they count by, the deepest zoom they hold. Counts built from another version or grid are not
  * read.
  */
final case class UndatedBuild(version: UndatedBuild.Version, perSide: UndatedBuild.PerSide, maxZoom: UndatedBuild.MaxZoom):
  /** Do they answer for the writes up to `current`, on a grid of `wantedPerSide` squares to `wantedMaxZoom`? */
  def answers(current: Long, wantedPerSide: Int, wantedMaxZoom: Int): Boolean =
    version == current && perSide == wantedPerSide && maxZoom >= wantedMaxZoom

object UndatedBuild:
  type Version = Version.T
  object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerSide = PerSide.T
  object PerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
