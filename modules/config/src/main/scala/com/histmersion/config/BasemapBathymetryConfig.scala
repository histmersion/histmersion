package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The sea floor: terrarium tiles at `url` (`{z}/{x}/{y}`), zooms 0…`maxZoom` of the region, one GET a tile,
  * `requestGapMs` apart; `onDemand`: the tiles outside the region, read as a page needs them (#3760). `source` picks
  * what the extract is made from: those tiles (ETOPO1), or the GEBCO grid (`gebco`).
  */
final case class BasemapBathymetryConfig(
    url: BasemapBathymetryConfig.Url,
    maxZoom: BasemapBathymetryConfig.MaxZoom,
    requestGapMs: BasemapBathymetryConfig.RequestGapMs,
    attribution: BasemapBathymetryConfig.Attribution,
    onDemand: BasemapOnDemandConfig,
    source: SeaFloorSource,
    gebco: BasemapGebcoConfig
) derives ConfigReader:
  def extractMaxZoom: com.histmersion.config.BasemapBathymetryConfig.ExtractMaxZoomResult = source match
    case SeaFloorSource.Etopo1 => maxZoom
    case SeaFloorSource.Gebco  => gebco.maxZoom

object BasemapBathymetryConfig:
  type ExtractMaxZoomResult = ExtractMaxZoomResult.T
  object ExtractMaxZoomResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestGapMs = RequestGapMs.T
  object RequestGapMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attribution = Attribution.T
  object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
