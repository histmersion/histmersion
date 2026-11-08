package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `onDemand`: the deeper relief read from `url` as a page needs it ([[BasemapOnDemandConfig]]); `focusRegions`: "name
  * minLon,minLat,maxLon,maxLat" — the regions `fetch --focus <name>` keeps to that depth.
  */
final case class BasemapTerrainConfig(
    url: BasemapTerrainConfig.Url,
    maxZoom: BasemapTerrainConfig.MaxZoom,
    attribution: BasemapTerrainConfig.Attribution,
    onDemand: BasemapOnDemandConfig,
    focusRegions: List[BasemapTerrainConfig.FocusRegions]
) derives ConfigReader

object BasemapTerrainConfig:
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends RefinedType[Int, Pure]
  type Attribution = Attribution.T
  object Attribution extends RefinedType[String, Pure]
  type FocusRegions = FocusRegions.T
  object FocusRegions extends RefinedType[String, Pure]
