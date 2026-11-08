package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
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
  object Url extends Newtype[String, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
  type Attribution = Attribution.T
  object Attribution extends Newtype[String, Pure]
  type FocusRegions = FocusRegions.T
  object FocusRegions extends Newtype[String, Pure]
