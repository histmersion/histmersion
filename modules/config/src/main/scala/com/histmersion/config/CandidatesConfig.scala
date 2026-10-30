package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Tunables that used to be `discover:` defaults and inline thresholds in `assess.py`. */
/** Deterministic new-source search (#3761): the `indexes` a goal's queries are read from, how far back a watch reads
  * the first time, how many results a watch reads, how many of the goal's sources (and of their authors) are watched,
  * and arXiv's API.
  */
final case class CandidatesConfig(
    indexes: List[String],
    lookbackDays: Int,
    perWatch: Int,
    sources: Int,
    authorsPerSource: Int,
    arxivApiUrl: String
) derives ConfigReader
