package com.histmersion
package plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion.events.gvp`: the WFS, the most eruptions one window may hold, a window's years, the pace, and the
  * Holocene floor no GVP dating reaches before.
  */
final case class GvpSettings(url: GvpSettings.Url, pageSize: GvpSettings.PageSize, windowYears: GvpSettings.WindowYears, requestIntervalMs: GvpSettings.RequestIntervalMs, floorYear: GvpSettings.FloorYear)
    derives ConfigReader

object GvpSettings:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type PageSize = PageSize.T
  object PageSize extends Newtype[Int, Pure]
  type WindowYears = WindowYears.T
  object WindowYears extends Newtype[Long, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type FloorYear = FloorYear.T
  object FloorYear extends Newtype[Long, Pure]
