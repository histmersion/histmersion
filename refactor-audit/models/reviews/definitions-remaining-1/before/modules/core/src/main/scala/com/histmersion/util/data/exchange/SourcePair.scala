package com.histmersion.util.data.exchange

/** A source of the bundle (`right`) and one here (`left`) that the sources' rules found to be, or perhaps to be, one
  * work: `gate` is the question that decides it when a person must, `byKey` whether a strong key (a DOI, the original
  * file, an ISBN) made them one, `basis` how they were matched as the plan says it (the keys, or the score).
  */
final case class SourcePair(
    right: com.histmersion.api.exchange.Bundle.Source,
    left: com.histmersion.api.exchange.Bundle.Source,
    gate: Option[String],
    byKey: Boolean,
    basis: String
)
