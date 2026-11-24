package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{FeaturePlace, movesTo}

/** Tunables that used to be `discover:` defaults and inline thresholds in `assess.py`. */
/** Deterministic new-source search (#3761): the `indexes` a goal's queries are read from, how far back a watch reads
  * the first time, how many results a watch reads, how many of the goal's sources (and of their authors) are watched,
  * and arXiv's API.
  */
@movesTo(FeaturePlace.LibraryAcquisition)
final case class CandidatesConfig(
    indexes: List[IndexName],
    lookbackDays: Days,
    perWatch: ItemCount,
    sources: ItemCount,
    authorsPerSource: ItemCount,
    arxivApiUrl: ServiceUrl
) derives ConfigReader
