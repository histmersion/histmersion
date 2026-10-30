package com.histmersion.domain

final case class MergeSettings(
    autoMerge: Double,
    newAtMost: Double,
    minhashSize: Int,
    findingNearAtLeast: Double,
    findingWithinKm: Double,
    weights: MatchWeights,
    placeWithinKm: Double,
    placeOverlapAtLeast: Double
)
