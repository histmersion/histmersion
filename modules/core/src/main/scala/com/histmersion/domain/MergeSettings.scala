package com.histmersion.domain

import com.histmersion.domain.catalogue.evidence.MatchWeights

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
