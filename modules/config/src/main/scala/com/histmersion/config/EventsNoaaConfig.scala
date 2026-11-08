package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Url extends RefinedType[String, Pure]
  type PageSize = PageSize.T
  object PageSize extends RefinedType[Int, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends RefinedType[Int, Pure]
  type Datasets = Datasets.T
  object Datasets extends RefinedType[String, Pure]
  type RegionMinMagnitude = RegionMinMagnitude.T
  object RegionMinMagnitude extends RefinedType[Double, Pure]
  type OutsideMinMagnitude = OutsideMinMagnitude.T
  object OutsideMinMagnitude extends RefinedType[Double, Pure]
