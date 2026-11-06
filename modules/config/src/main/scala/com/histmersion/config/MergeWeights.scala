package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

final case class MergeWeights(
    titleFingerprint: MergeWeights.TitleFingerprint,
    titleMain: MergeWeights.TitleMain,
    titleSimilar: MergeWeights.TitleSimilar,
    titleSimilarAtLeast: MergeWeights.TitleSimilarAtLeast,
    titleTokens: MergeWeights.TitleTokens,
    titleTokensAtLeast: MergeWeights.TitleTokensAtLeast,
    titleDiffers: MergeWeights.TitleDiffers,
    authorsSame: MergeWeights.AuthorsSame,
    authorsFirst: MergeWeights.AuthorsFirst,
    authorsOverlap: MergeWeights.AuthorsOverlap,
    authorsDisjoint: MergeWeights.AuthorsDisjoint,
    yearEqual: MergeWeights.YearEqual,
    yearNear: MergeWeights.YearNear,
    yearReprint: MergeWeights.YearReprint,
    yearFar: MergeWeights.YearFar,
    venueEqual: MergeWeights.VenueEqual,
    venueDiffers: MergeWeights.VenueDiffers,
    bodySame: MergeWeights.BodySame,
    bodySameAtLeast: MergeWeights.BodySameAtLeast,
    bodyEdition: MergeWeights.BodyEdition,
    bodyEditionAtLeast: MergeWeights.BodyEditionAtLeast,
    bodyDiffers: MergeWeights.BodyDiffers,
    bodyDiffersBelow: MergeWeights.BodyDiffersBelow,
    alias: MergeWeights.Alias
) derives ConfigReader

object MergeWeights:
  type TitleFingerprint = TitleFingerprint.T
  object TitleFingerprint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleMain = TitleMain.T
  object TitleMain extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleSimilar = TitleSimilar.T
  object TitleSimilar extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleSimilarAtLeast = TitleSimilarAtLeast.T
  object TitleSimilarAtLeast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleTokens = TitleTokens.T
  object TitleTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleTokensAtLeast = TitleTokensAtLeast.T
  object TitleTokensAtLeast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleDiffers = TitleDiffers.T
  object TitleDiffers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsSame = AuthorsSame.T
  object AuthorsSame extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsFirst = AuthorsFirst.T
  object AuthorsFirst extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsOverlap = AuthorsOverlap.T
  object AuthorsOverlap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsDisjoint = AuthorsDisjoint.T
  object AuthorsDisjoint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearEqual = YearEqual.T
  object YearEqual extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearNear = YearNear.T
  object YearNear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearReprint = YearReprint.T
  object YearReprint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearFar = YearFar.T
  object YearFar extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VenueEqual = VenueEqual.T
  object VenueEqual extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VenueDiffers = VenueDiffers.T
  object VenueDiffers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodySame = BodySame.T
  object BodySame extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodySameAtLeast = BodySameAtLeast.T
  object BodySameAtLeast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyEdition = BodyEdition.T
  object BodyEdition extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyEditionAtLeast = BodyEditionAtLeast.T
  object BodyEditionAtLeast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyDiffers = BodyDiffers.T
  object BodyDiffers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyDiffersBelow = BodyDiffersBelow.T
  object BodyDiffersBelow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Alias = Alias.T
  object Alias extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
