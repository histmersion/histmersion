package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `floorYear`: the earliest year a GVP dating may reach (the list is of HOLOCENE eruptions: a ± that runs before it is
  * held there).
  */
final case class EventsGvpConfig(
    url: EventsGvpConfig.Url,
    pageSize: EventsGvpConfig.PageSize,
    windowYears: EventsGvpConfig.WindowYears,
    requestIntervalMs: EventsGvpConfig.RequestIntervalMs,
    regionMinVei: EventsGvpConfig.RegionMinVei,
    outsideMinVei: EventsGvpConfig.OutsideMinVei,
    floorYear: EventsGvpConfig.FloorYear
) derives ConfigReader

object EventsGvpConfig:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type PageSize = PageSize.T
  object PageSize extends Newtype[Int, Pure]
  type WindowYears = WindowYears.T
  object WindowYears extends Newtype[Long, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type RegionMinVei = RegionMinVei.T
  object RegionMinVei extends Newtype[Int, Pure]
  type OutsideMinVei = OutsideMinVei.T
  object OutsideMinVei extends Newtype[Int, Pure]
  type FloorYear = FloorYear.T
  object FloorYear extends Newtype[Long, Pure]
