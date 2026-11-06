package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** The GEBCO grid as a sea-floor source: `url`, one GeoTIFF of the grid's tiling (`tileDegrees` square) with `{north}`,
  * `{south}`, `{west}`, `{east}`; the extract's zooms 0…`maxZoom`; its credit line.
  */
final case class BasemapGebcoConfig(url: BasemapGebcoConfig.Url, tileDegrees: BasemapGebcoConfig.TileDegrees, maxZoom: BasemapGebcoConfig.MaxZoom, attribution: BasemapGebcoConfig.Attribution)
    derives ConfigReader

object BasemapGebcoConfig:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileDegrees = TileDegrees.T
  object TileDegrees extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attribution = Attribution.T
  object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
