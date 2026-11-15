package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `drop`: the vector layers (or their kinds) never stored — [[Basemap.parseDrop]]; everything else is kept and the
  * style decides what is drawn. `onDemand`: the map outside the extract (or deeper), read as a page needs it (#3760).
  */
final case class BasemapVectorConfig(
    buildsUrl: BasemapVectorConfig.BuildsUrl,
    url: BasemapVectorConfig.Url,
    build: BasemapVectorConfig.Build,
    maxZoom: BasemapVectorConfig.MaxZoom,
    drop: BasemapVectorConfig.Drop,
    attribution: BasemapVectorConfig.Attribution,
    onDemand: BasemapOnDemandConfig
) derives ConfigReader

object BasemapVectorConfig:
  type BuildsUrl = BuildsUrl.T
  object BuildsUrl extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Build = Build.T
  object Build extends Newtype[String, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
  type Drop = Drop.T
  object Drop extends Newtype[String, Pure]
  type Attribution = Attribution.T
  object Attribution extends Newtype[String, Pure]
