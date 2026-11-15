package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The sea floor: terrarium tiles at `url` (`{z}/{x}/{y}`), zooms 0…`maxZoom` of the region, one GET a tile,
  * `requestGapMs` apart; `onDemand`: the tiles outside the region, read as a page needs them (#3760). `source` picks
  * what the extract is made from: those tiles (ETOPO1), or the GEBCO grid (`gebco`).
  */
final case class BasemapBathymetryConfig(
    url: BasemapBathymetryConfig.Url,
    maxZoom: BasemapGebcoConfig.MaxZoom,
    requestGapMs: BasemapBathymetryConfig.RequestGapMs,
    attribution: BasemapBathymetryConfig.Attribution,
    onDemand: BasemapOnDemandConfig,
    source: SeaFloorSource,
    gebco: BasemapGebcoConfig
) derives ConfigReader:
  def extractMaxZoom: BasemapGebcoConfig.MaxZoom = source match
    case SeaFloorSource.Etopo1 => maxZoom
    case SeaFloorSource.Gebco  => gebco.maxZoom

object BasemapBathymetryConfig:

  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type RequestGapMs = RequestGapMs.T
  object RequestGapMs extends Newtype[Int, Pure]
  type Attribution = Attribution.T
  object Attribution extends Newtype[String, Pure]
