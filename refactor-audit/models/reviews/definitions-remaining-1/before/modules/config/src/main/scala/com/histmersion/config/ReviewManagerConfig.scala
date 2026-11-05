package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The review manager's defaults (a value saved in the manager's config wins). `staleAfterDays`: a converged article is
  * selected again (lowest priority) after this many days, or when its filter gained sources (0 = never); looked for at
  * most every `staleScanMinutes`. `inferFilter`: an article without a coverage filter gets an infer-filter item before
  * its review. `extendMinNew`: an article whose filter returns at least this many sources it neither cites nor gives a
  * reason for gets an `article-extend` item (0 = never), at most every `extendCooldownDays` per article.
  */
final case class ReviewManagerConfig(
    maxRuns: Int,
    rounds: Int,
    maxRunsPerArticle: Int,
    noProgressRuns: Int,
    reopenLimit: Int,
    cooldownMinutes: Int,
    lowWater: Int,
    refillBatch: Int,
    staleAfterDays: Int,
    inferFilter: Boolean = true,
    staleScanMinutes: Int = 60,
    extendMinNew: Int = 0,
    extendCooldownDays: Int = 14
) derives ConfigReader
