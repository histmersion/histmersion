package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Media from outside providers (#1446): the polite-client settings, the stored sizes and, per provider, its API and
  * the licence families it may be stored under ([[MediaLicence]]).
  */
final case class MediaConfig(
    userAgent: String,
    timeoutSeconds: Int,
    requestIntervalMs: Int,
    maxRetries: Int,
    maxRetryAfterSeconds: Int,
    defaultLimit: Int,
    maxLimit: Int,
    displayPx: Int,
    thumbPx: Int,
    maxBytes: Long,
    perProperty: Int,
    importBatch: Int,
    sameSiteKm: Double,
    wikidata: MediaWikidataConfig,
    commons: MediaCommonsConfig,
    covers: MediaCoversConfig,
    illustrations: MediaIllustrationsConfig,
    depicts: MediaDepictsConfig,
    met: MediaCollectionConfig,
    cleveland: MediaCollectionConfig,
    aic: MediaCollectionConfig,
    loc: MediaCollectionConfig,
    pleiades: MediaCollectionConfig,
    openContext: MediaCollectionConfig,
    smithsonian: MediaCollectionConfig,
    europeana: MediaCollectionConfig,
    collections: MediaPluginCollectionsConfig,
    samples: Int
) derives ConfigReader:
  /** `--limit`, clamped to `1 … max-limit`; absent or ≤ 0 = `default-limit`. */
  def limit(asked: Option[Int]): Int =
    asked.filter(_ > 0).getOrElse(defaultLimit).max(1).min(maxLimit.max(1))
