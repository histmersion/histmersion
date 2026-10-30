package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** First-run data (#720). With `auto`, a starting server imports the bundled gazetteer (`<dataDir>/places`) and period
  * presets (`<dataDir>/periods`) into whichever of those tables is EMPTY, as a background job; `histmersion init` runs
  * the same on demand, and `--fetch` adds the network sources in `fetchSources` (comma-separated: gazetteer importers,
  * `land`, `periodo`).
  */
final case class InitConfig(auto: Boolean, dataDir: String, fetchSources: String) derives ConfigReader:
  def sources: List[String] = PeriodsConfig.split(fetchSources)
