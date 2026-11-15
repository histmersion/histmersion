package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

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
  object TitleFingerprint extends Newtype[Double, Pure]
  type TitleMain = TitleMain.T
  object TitleMain extends Newtype[Double, Pure]
  type TitleSimilar = TitleSimilar.T
  object TitleSimilar extends Newtype[Double, Pure]
  type TitleSimilarAtLeast = TitleSimilarAtLeast.T
  object TitleSimilarAtLeast extends Newtype[Double, Pure]
  type TitleTokens = TitleTokens.T
  object TitleTokens extends Newtype[Double, Pure]
  type TitleTokensAtLeast = TitleTokensAtLeast.T
  object TitleTokensAtLeast extends Newtype[Double, Pure]
  type TitleDiffers = TitleDiffers.T
  object TitleDiffers extends Newtype[Double, Pure]
  type AuthorsSame = AuthorsSame.T
  object AuthorsSame extends Newtype[Double, Pure]
  type AuthorsFirst = AuthorsFirst.T
  object AuthorsFirst extends Newtype[Double, Pure]
  type AuthorsOverlap = AuthorsOverlap.T
  object AuthorsOverlap extends Newtype[Double, Pure]
  type AuthorsDisjoint = AuthorsDisjoint.T
  object AuthorsDisjoint extends Newtype[Double, Pure]
  type YearEqual = YearEqual.T
  object YearEqual extends Newtype[Double, Pure]
  type YearNear = YearNear.T
  object YearNear extends Newtype[Double, Pure]
  type YearReprint = YearReprint.T
  object YearReprint extends Newtype[Double, Pure]
  type YearFar = YearFar.T
  object YearFar extends Newtype[Double, Pure]
  type VenueEqual = VenueEqual.T
  object VenueEqual extends Newtype[Double, Pure]
  type VenueDiffers = VenueDiffers.T
  object VenueDiffers extends Newtype[Double, Pure]
  type BodySame = BodySame.T
  object BodySame extends Newtype[Double, Pure]
  type BodySameAtLeast = BodySameAtLeast.T
  object BodySameAtLeast extends Newtype[Double, Pure]
  type BodyEdition = BodyEdition.T
  object BodyEdition extends Newtype[Double, Pure]
  type BodyEditionAtLeast = BodyEditionAtLeast.T
  object BodyEditionAtLeast extends Newtype[Double, Pure]
  type BodyDiffers = BodyDiffers.T
  object BodyDiffers extends Newtype[Double, Pure]
  type BodyDiffersBelow = BodyDiffersBelow.T
  object BodyDiffersBelow extends Newtype[Double, Pure]
  type Alias = Alias.T
  object Alias extends Newtype[Double, Pure]
