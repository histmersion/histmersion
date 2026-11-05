package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
  object BuildsUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Build = Build.T
  object Build extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Drop = Drop.T
  object Drop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attribution = Attribution.T
  object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
