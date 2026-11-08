package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
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
  object TitleFingerprint extends RefinedType[Double, Pure]
  type TitleMain = TitleMain.T
  object TitleMain extends RefinedType[Double, Pure]
  type TitleSimilar = TitleSimilar.T
  object TitleSimilar extends RefinedType[Double, Pure]
  type TitleSimilarAtLeast = TitleSimilarAtLeast.T
  object TitleSimilarAtLeast extends RefinedType[Double, Pure]
  type TitleTokens = TitleTokens.T
  object TitleTokens extends RefinedType[Double, Pure]
  type TitleTokensAtLeast = TitleTokensAtLeast.T
  object TitleTokensAtLeast extends RefinedType[Double, Pure]
  type TitleDiffers = TitleDiffers.T
  object TitleDiffers extends RefinedType[Double, Pure]
  type AuthorsSame = AuthorsSame.T
  object AuthorsSame extends RefinedType[Double, Pure]
  type AuthorsFirst = AuthorsFirst.T
  object AuthorsFirst extends RefinedType[Double, Pure]
  type AuthorsOverlap = AuthorsOverlap.T
  object AuthorsOverlap extends RefinedType[Double, Pure]
  type AuthorsDisjoint = AuthorsDisjoint.T
  object AuthorsDisjoint extends RefinedType[Double, Pure]
  type YearEqual = YearEqual.T
  object YearEqual extends RefinedType[Double, Pure]
  type YearNear = YearNear.T
  object YearNear extends RefinedType[Double, Pure]
  type YearReprint = YearReprint.T
  object YearReprint extends RefinedType[Double, Pure]
  type YearFar = YearFar.T
  object YearFar extends RefinedType[Double, Pure]
  type VenueEqual = VenueEqual.T
  object VenueEqual extends RefinedType[Double, Pure]
  type VenueDiffers = VenueDiffers.T
  object VenueDiffers extends RefinedType[Double, Pure]
  type BodySame = BodySame.T
  object BodySame extends RefinedType[Double, Pure]
  type BodySameAtLeast = BodySameAtLeast.T
  object BodySameAtLeast extends RefinedType[Double, Pure]
  type BodyEdition = BodyEdition.T
  object BodyEdition extends RefinedType[Double, Pure]
  type BodyEditionAtLeast = BodyEditionAtLeast.T
  object BodyEditionAtLeast extends RefinedType[Double, Pure]
  type BodyDiffers = BodyDiffers.T
  object BodyDiffers extends RefinedType[Double, Pure]
  type BodyDiffersBelow = BodyDiffersBelow.T
  object BodyDiffersBelow extends RefinedType[Double, Pure]
  type Alias = Alias.T
  object Alias extends RefinedType[Double, Pure]
