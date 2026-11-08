package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.constraint.any.Pure

/** How much each comparison of two sources counts when a merge asks whether they are one work (`merge.weights`). */
final case class MatchWeights(
    titleFingerprint: MatchWeights.TitleFingerprint,
    titleMain: MatchWeights.TitleMain,
    titleSimilar: MatchWeights.TitleSimilar,
    titleSimilarAtLeast: MatchWeights.TitleSimilarAtLeast,
    titleTokens: MatchWeights.TitleTokens,
    titleTokensAtLeast: MatchWeights.TitleTokensAtLeast,
    titleDiffers: MatchWeights.TitleDiffers,
    authorsSame: MatchWeights.AuthorsSame,
    authorsFirst: MatchWeights.AuthorsFirst,
    authorsOverlap: MatchWeights.AuthorsOverlap,
    authorsDisjoint: MatchWeights.AuthorsDisjoint,
    yearEqual: MatchWeights.YearEqual,
    yearNear: MatchWeights.YearNear,
    yearReprint: MatchWeights.YearReprint,
    yearFar: MatchWeights.YearFar,
    venueEqual: MatchWeights.VenueEqual,
    venueDiffers: MatchWeights.VenueDiffers,
    bodySame: MatchWeights.BodySame,
    bodySameAtLeast: MatchWeights.BodySameAtLeast,
    bodyEdition: MatchWeights.BodyEdition,
    bodyEditionAtLeast: MatchWeights.BodyEditionAtLeast,
    bodyDiffers: MatchWeights.BodyDiffers,
    bodyDiffersBelow: MatchWeights.BodyDiffersBelow,
    alias: MatchWeights.Alias
)

object MatchWeights:
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
