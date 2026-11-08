package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The GEBCO grid as a sea-floor source: `url`, one GeoTIFF of the grid's tiling (`tileDegrees` square) with `{north}`,
  * `{south}`, `{west}`, `{east}`; the extract's zooms 0…`maxZoom`; its credit line.
  */
final case class BasemapGebcoConfig(url: BasemapGebcoConfig.Url, tileDegrees: BasemapGebcoConfig.TileDegrees, maxZoom: BasemapGebcoConfig.MaxZoom, attribution: BasemapGebcoConfig.Attribution)
    derives ConfigReader

object BasemapGebcoConfig:
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type TileDegrees = TileDegrees.T
  object TileDegrees extends RefinedType[Int, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends RefinedType[Int, Pure]
  type Attribution = Attribution.T
  object Attribution extends RefinedType[String, Pure]
