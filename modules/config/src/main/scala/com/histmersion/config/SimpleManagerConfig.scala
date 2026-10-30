package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A library-wide or per-area manager with nothing but its runs at once to set: the enrich manager
  * (`agents.managers.enrich`) and the seeding managers (`agents.managers.seed`).
  */
final case class SimpleManagerConfig(maxRuns: Int) derives ConfigReader
