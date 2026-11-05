package com.histmersion.plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.events.gvp`: the WFS, the most eruptions one window may hold, a window's years, the pace, and the
  * Holocene floor no GVP dating reaches before.
  */
final case class GvpSettings(url: GvpSettings.Url, pageSize: GvpSettings.PageSize, windowYears: GvpSettings.WindowYears, requestIntervalMs: GvpSettings.RequestIntervalMs, floorYear: GvpSettings.FloorYear)
    derives ConfigReader

object GvpSettings:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSize = PageSize.T
  object PageSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowYears = WindowYears.T
  object WindowYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FloorYear = FloorYear.T
  object FloorYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
