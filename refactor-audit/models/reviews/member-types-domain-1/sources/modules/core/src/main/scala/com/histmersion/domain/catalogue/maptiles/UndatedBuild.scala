package com.histmersion.domain.catalogue.maptiles

/** What the kept counts of the undated sites were built from: the version of the writes to undated sites they include,
  * the squares a tile side they count by, the deepest zoom they hold. Counts built from another version or grid are not
  * read.
  */
final case class UndatedBuild(version: UndatedBuild.Version, perSide: UndatedBuild.PerSide, maxZoom: UndatedBuild.MaxZoom):
  /** Do they answer for the writes up to `current`, on a grid of `wantedPerSide` squares to `wantedMaxZoom`? */
  def answers(current: com.histmersion.domain.catalogue.maptiles.UndatedBuild.AnswersCurrent, wantedPerSide: com.histmersion.domain.catalogue.maptiles.UndatedBuild.AnswersWantedPerSide, wantedMaxZoom: com.histmersion.domain.catalogue.maptiles.UndatedBuild.AnswersWantedMaxZoom): com.histmersion.domain.catalogue.maptiles.UndatedBuild.AnswersResult =
    version == current && perSide == wantedPerSide && maxZoom >= wantedMaxZoom

object UndatedBuild:
  type AnswersResult = AnswersResult.T
  object AnswersResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswersCurrent = AnswersCurrent.T
  object AnswersCurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswersWantedPerSide = AnswersWantedPerSide.T
  object AnswersWantedPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswersWantedMaxZoom = AnswersWantedMaxZoom.T
  object AnswersWantedMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Version = Version.T
  object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerSide = PerSide.T
  object PerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
