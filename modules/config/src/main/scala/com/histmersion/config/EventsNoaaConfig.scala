package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.CatalogueEvents)
final case class EventsNoaaConfig(
    url: EventsNoaaConfig.Url,
    pageSize: EventsNoaaConfig.PageSize,
    requestIntervalMs: EventsNoaaConfig.RequestIntervalMs,
    datasets: EventsNoaaConfig.Datasets,
    regionMinMagnitude: EventsNoaaConfig.RegionMinMagnitude,
    outsideMinMagnitude: EventsNoaaConfig.OutsideMinMagnitude
) derives ConfigReader

object EventsNoaaConfig:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type PageSize = PageSize.T
  object PageSize extends Newtype[Int, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type Datasets = Datasets.T
  object Datasets extends Newtype[String, Pure]
  type RegionMinMagnitude = RegionMinMagnitude.T
  object RegionMinMagnitude extends Newtype[Double, Pure]
  type OutsideMinMagnitude = OutsideMinMagnitude.T
  object OutsideMinMagnitude extends Newtype[Double, Pure]
