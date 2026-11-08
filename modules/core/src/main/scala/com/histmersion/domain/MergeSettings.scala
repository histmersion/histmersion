package com.histmersion
package domain

import com.histmersion.util.data.exchange.MatchWeights
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object AutoMerge extends RefinedType[Double, Pure]
  type NewAtMost = NewAtMost.T
  object NewAtMost extends RefinedType[Double, Pure]
  type MinhashSize = MinhashSize.T
  object MinhashSize extends RefinedType[Int, Pure]
  type FindingNearAtLeast = FindingNearAtLeast.T
  object FindingNearAtLeast extends RefinedType[Double, Pure]
  type FindingWithinKm = FindingWithinKm.T
  object FindingWithinKm extends RefinedType[Double, Pure]
  type PlaceWithinKm = PlaceWithinKm.T
  object PlaceWithinKm extends RefinedType[Double, Pure]
  type PlaceOverlapAtLeast = PlaceOverlapAtLeast.T
  object PlaceOverlapAtLeast extends RefinedType[Double, Pure]
