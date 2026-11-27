package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Media from outside providers (#1446): the polite-client settings, the stored sizes and, per provider, its API and
  * the licence families it may be stored under ([[com.histmersion.domain.Licence]]).
  */
@movesTo(FeaturePlace.Media)
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def limit(asked: Option[MediaConfig.Limit]): MediaConfig.Limit =
    MediaConfig.Limit(asked.filter(_.value > 0).getOrElse(defaultLimit).value.max(1).min(maxLimit.value.max(1)))

object MediaConfig:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  type UserAgent = UserAgent.T
  object UserAgent extends Newtype[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type MaxRetries = MaxRetries.T
  object MaxRetries extends Newtype[Int, Pure]
  type MaxRetryAfterSeconds = MaxRetryAfterSeconds.T
  object MaxRetryAfterSeconds extends Newtype[Int, Pure]
  type DisplayPx = DisplayPx.T
  object DisplayPx extends Newtype[Int, Pure]
  type ThumbPx = ThumbPx.T
  object ThumbPx extends Newtype[Int, Pure]
  type MaxBytes = MaxBytes.T
  object MaxBytes extends Newtype[Long, Pure]
  type PerProperty = PerProperty.T
  object PerProperty extends Newtype[Int, Pure]
  type ImportBatch = ImportBatch.T
  object ImportBatch extends Newtype[Int, Pure]
  type SameSiteKm = SameSiteKm.T
  object SameSiteKm extends Newtype[Double, Pure]
  type Samples = Samples.T
  object Samples extends Newtype[Int, Pure]
