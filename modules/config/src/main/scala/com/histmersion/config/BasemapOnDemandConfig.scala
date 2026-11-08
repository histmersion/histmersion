package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** A layer read tile by tile when a page shows an area the download does not hold (the relief deeper than the extract;
  * the map and the sea floor outside it, #3760), cached to `cacheGb`.
  */
final case class BasemapOnDemandConfig(
    enabled: BasemapOnDemandConfig.Enabled,
    maxZoom: BasemapOnDemandConfig.MaxZoom,
    cacheGb: BasemapOnDemandConfig.CacheGb,
    concurrency: BasemapOnDemandConfig.Concurrency,
    requestsPerSecond: BasemapOnDemandConfig.RequestsPerSecond,
    retryAfterSeconds: BasemapOnDemandConfig.RetryAfterSeconds
) derives ConfigReader

object BasemapOnDemandConfig:
  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
  type CacheGb = CacheGb.T
  object CacheGb extends Newtype[Double, Pure]
  type Concurrency = Concurrency.T
  object Concurrency extends Newtype[Int, Pure]
  type RequestsPerSecond = RequestsPerSecond.T
  object RequestsPerSecond extends Newtype[Double, Pure]
  type RetryAfterSeconds = RetryAfterSeconds.T
  object RetryAfterSeconds extends Newtype[Int, Pure]
