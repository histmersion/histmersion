package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Media from outside providers (#1446): the polite-client settings, the stored sizes and, per provider, its API and
  * the licence families it may be stored under ([[com.histmersion.domain.Licence]]).
  */
final case class MediaConfig(
    userAgent: MediaConfig.UserAgent,
    timeoutSeconds: MediaConfig.TimeoutSeconds,
    requestIntervalMs: MediaConfig.RequestIntervalMs,
    maxRetries: MediaConfig.MaxRetries,
    maxRetryAfterSeconds: MediaConfig.MaxRetryAfterSeconds,
    defaultLimit: MediaConfig.Limit,
    maxLimit: MediaConfig.Limit,
    displayPx: MediaConfig.DisplayPx,
    thumbPx: MediaConfig.ThumbPx,
    maxBytes: MediaConfig.MaxBytes,
    perProperty: MediaConfig.PerProperty,
    importBatch: MediaConfig.ImportBatch,
    sameSiteKm: MediaConfig.SameSiteKm,
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
    samples: MediaConfig.Samples
) derives ConfigReader:
  /** `--limit`, clamped to `1 … max-limit`; absent or ≤ 0 = `default-limit`. */
  def limit(asked: Option[MediaConfig.Limit]): MediaConfig.Limit =
    MediaConfig.Limit(asked.filter(_.value > 0).getOrElse(defaultLimit).value.max(1).min(maxLimit.value.max(1)))

object MediaConfig:
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]

  type UserAgent = UserAgent.T
  object UserAgent extends RefinedType[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends RefinedType[Int, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends RefinedType[Int, Pure]
  type MaxRetries = MaxRetries.T
  object MaxRetries extends RefinedType[Int, Pure]
  type MaxRetryAfterSeconds = MaxRetryAfterSeconds.T
  object MaxRetryAfterSeconds extends RefinedType[Int, Pure]
  type DisplayPx = DisplayPx.T
  object DisplayPx extends RefinedType[Int, Pure]
  type ThumbPx = ThumbPx.T
  object ThumbPx extends RefinedType[Int, Pure]
  type MaxBytes = MaxBytes.T
  object MaxBytes extends RefinedType[Long, Pure]
  type PerProperty = PerProperty.T
  object PerProperty extends RefinedType[Int, Pure]
  type ImportBatch = ImportBatch.T
  object ImportBatch extends RefinedType[Int, Pure]
  type SameSiteKm = SameSiteKm.T
  object SameSiteKm extends RefinedType[Double, Pure]
  type Samples = Samples.T
  object Samples extends RefinedType[Int, Pure]
