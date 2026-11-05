package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `infer-filter`: a proposed filter must return at least `minHits` and at most `maxHits` sources, and at least
  * `minCitedShare` (0..1) of the sources the article cites; `sample` sources it returns that the article does not cite
  * are shown with the proposal.
  */
final case class AgentInferFilterConfig(minHits: Int, maxHits: Int, minCitedShare: Double, sample: Int)
    derives ConfigReader
