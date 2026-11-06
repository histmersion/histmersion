package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSize = PageSize.T
  object PageSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Datasets = Datasets.T
  object Datasets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionMinMagnitude = RegionMinMagnitude.T
  object RegionMinMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutsideMinMagnitude = OutsideMinMagnitude.T
  object OutsideMinMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
