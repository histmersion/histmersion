package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.constraint.any.Pure

/** What the kept counts of the undated sites were built from: the version of the writes to undated sites they include,
  * the squares a tile side they count by, the deepest zoom they hold. Counts built from another version or grid are not
  * read.
  */
final case class UndatedBuild(
    version: UndatedBuild.Version,
    perSide: UndatedBuild.PerSide,
    maxZoom: UndatedBuild.MaxZoom
):
  /** Do they answer for the writes up to `current`, on a grid of `wantedPerSide` squares to `wantedMaxZoom`? */
  def answers(
      current: UndatedBuild.Version,
      wantedPerSide: UndatedBuild.PerSide,
      wantedMaxZoom: UndatedBuild.MaxZoom
  ): Boolean =
    version == current && perSide == wantedPerSide && maxZoom >= wantedMaxZoom

object UndatedBuild:

  type Version = Version.T
  object Version extends Newtype[Long, Pure]
  type PerSide = PerSide.T
  object PerSide extends Newtype[Int, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
