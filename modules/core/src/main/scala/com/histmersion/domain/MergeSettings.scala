package com.histmersion
package domain

import com.histmersion.util.data.exchange.MatchWeights
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.UtilDataExchange)
final case class MergeSettings(
    autoMerge: MergeSettings.AutoMerge,
    newAtMost: MergeSettings.NewAtMost,
    minhashSize: MergeSettings.MinhashSize,
    findingNearAtLeast: MergeSettings.FindingNearAtLeast,
    findingWithinKm: MergeSettings.FindingWithinKm,
    weights: MatchWeights,
    placeWithinKm: MergeSettings.PlaceWithinKm,
    placeOverlapAtLeast: MergeSettings.PlaceOverlapAtLeast
)

object MergeSettings:
  type AutoMerge = AutoMerge.T
  object AutoMerge extends Newtype[Double, Pure]
  type NewAtMost = NewAtMost.T
  object NewAtMost extends Newtype[Double, Pure]
  type MinhashSize = MinhashSize.T
  object MinhashSize extends Newtype[Int, Pure]
  type FindingNearAtLeast = FindingNearAtLeast.T
  object FindingNearAtLeast extends Newtype[Double, Pure]
  type FindingWithinKm = FindingWithinKm.T
  object FindingWithinKm extends Newtype[Double, Pure]
  type PlaceWithinKm = PlaceWithinKm.T
  object PlaceWithinKm extends Newtype[Double, Pure]
  type PlaceOverlapAtLeast = PlaceOverlapAtLeast.T
  object PlaceOverlapAtLeast extends Newtype[Double, Pure]
