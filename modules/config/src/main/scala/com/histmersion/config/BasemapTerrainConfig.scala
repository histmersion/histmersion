package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attribution = Attribution.T
  object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusRegions = FocusRegions.T
  object FocusRegions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
