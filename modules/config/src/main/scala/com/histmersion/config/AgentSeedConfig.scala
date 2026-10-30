package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `propose-articles`: at most `maxArticles` per plan; each one's filter returns at least `minSources` sources and
  * shares less than `maxOverlap` (0..1) of them with any article of the research area.
  */
final case class AgentSeedConfig(maxArticles: Int, minSources: Int, maxOverlap: Double) derives ConfigReader
