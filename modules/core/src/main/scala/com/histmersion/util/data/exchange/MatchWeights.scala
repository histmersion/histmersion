package com.histmersion.util.data.exchange

/** How much each comparison of two sources counts when a merge asks whether they are one work (`merge.weights`). */
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
