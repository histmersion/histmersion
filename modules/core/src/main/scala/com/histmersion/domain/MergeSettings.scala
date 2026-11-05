package com.histmersion.domain

import com.histmersion.util.data.exchange.MatchWeights

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
  object AutoMerge extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAtMost = NewAtMost.T
  object NewAtMost extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinhashSize = MinhashSize.T
  object MinhashSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingNearAtLeast = FindingNearAtLeast.T
  object FindingNearAtLeast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingWithinKm = FindingWithinKm.T
  object FindingWithinKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceWithinKm = PlaceWithinKm.T
  object PlaceWithinKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceOverlapAtLeast = PlaceOverlapAtLeast.T
  object PlaceOverlapAtLeast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
