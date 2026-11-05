package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CacheGb = CacheGb.T
  object CacheGb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Concurrency = Concurrency.T
  object Concurrency extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestsPerSecond = RequestsPerSecond.T
  object RequestsPerSecond extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryAfterSeconds = RetryAfterSeconds.T
  object RetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
