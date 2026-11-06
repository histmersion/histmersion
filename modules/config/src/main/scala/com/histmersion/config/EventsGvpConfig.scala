package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSize = PageSize.T
  object PageSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowYears = WindowYears.T
  object WindowYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionMinVei = RegionMinVei.T
  object RegionMinVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutsideMinVei = OutsideMinVei.T
  object OutsideMinVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FloorYear = FloorYear.T
  object FloorYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
