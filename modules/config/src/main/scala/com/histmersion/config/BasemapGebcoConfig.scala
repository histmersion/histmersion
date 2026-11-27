package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** The GEBCO grid as a sea-floor source: `url`, one GeoTIFF of the grid's tiling (`tileDegrees` square) with `{north}`,
  * `{south}`, `{west}`, `{east}`; the extract's zooms 0…`maxZoom`; its credit line.
  */
@movesTo(FeaturePlace.UtilBasemap)
final case class BasemapGebcoConfig(
    url: BasemapGebcoConfig.Url,
    tileDegrees: BasemapGebcoConfig.TileDegrees,
    maxZoom: BasemapGebcoConfig.MaxZoom,
    attribution: BasemapGebcoConfig.Attribution
) derives ConfigReader

object BasemapGebcoConfig:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type TileDegrees = TileDegrees.T
  object TileDegrees extends Newtype[Int, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
  type Attribution = Attribution.T
  object Attribution extends Newtype[String, Pure]
