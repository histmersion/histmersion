package com.histmersion.domain.catalogue.evidence

final case class MatchWeights(
    titleFingerprint: Double,
    titleMain: Double,
    titleSimilar: Double,
    titleSimilarAtLeast: Double,
    titleTokens: Double,
    titleTokensAtLeast: Double,
    titleDiffers: Double,
    authorsSame: Double,
    authorsFirst: Double,
    authorsOverlap: Double,
    authorsDisjoint: Double,
    yearEqual: Double,
    yearNear: Double,
    yearReprint: Double,
    yearFar: Double,
    venueEqual: Double,
    venueDiffers: Double,
    bodySame: Double,
    bodySameAtLeast: Double,
    bodyEdition: Double,
    bodyEditionAtLeast: Double,
    bodyDiffers: Double,
    bodyDiffersBelow: Double,
    alias: Double
)
