package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Media from outside providers (#1446): the polite-client settings, the stored sizes and, per provider, its API and
  * the licence families it may be stored under ([[com.histmersion.domain.Licence]]).
  */
final case class MediaConfig(
    userAgent: MediaConfig.UserAgent,
    timeoutSeconds: MediaConfig.TimeoutSeconds,
    requestIntervalMs: MediaConfig.RequestIntervalMs,
    maxRetries: MediaConfig.MaxRetries,
    maxRetryAfterSeconds: MediaConfig.MaxRetryAfterSeconds,
    defaultLimit: MediaConfig.DefaultLimit,
    maxLimit: MediaConfig.MaxLimit,
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
  def limit(asked: Option[com.histmersion.config.MediaConfig.LimitAsked]): com.histmersion.config.MediaConfig.LimitResult =
    asked.filter(_ > 0).getOrElse(defaultLimit).max(1).min(maxLimit.max(1))

object MediaConfig:
  type LimitResult = LimitResult.T
  object LimitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LimitAsked = LimitAsked.T
  object LimitAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type UserAgent = UserAgent.T
  object UserAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxRetries = MaxRetries.T
  object MaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxRetryAfterSeconds = MaxRetryAfterSeconds.T
  object MaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultLimit = DefaultLimit.T
  object DefaultLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxLimit = MaxLimit.T
  object MaxLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayPx = DisplayPx.T
  object DisplayPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbPx = ThumbPx.T
  object ThumbPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxBytes = MaxBytes.T
  object MaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerProperty = PerProperty.T
  object PerProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportBatch = ImportBatch.T
  object ImportBatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameSiteKm = SameSiteKm.T
  object SameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Samples = Samples.T
  object Samples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
