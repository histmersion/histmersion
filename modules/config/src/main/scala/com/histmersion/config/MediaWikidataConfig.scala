package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

final case class MediaWikidataConfig(
    apiUrl: MediaWikidataConfig.ApiUrl,
    batch: MediaWikidataConfig.Batch,
    maxlag: MediaWikidataConfig.Maxlag,
    placeProperties: MediaWikidataConfig.PlaceProperties,
    eventProperties: MediaWikidataConfig.EventProperties
) derives ConfigReader

object MediaWikidataConfig:
  type ApiUrl = ApiUrl.T
  object ApiUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Batch = Batch.T
  object Batch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceProperties = PlaceProperties.T
  object PlaceProperties extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventProperties = EventProperties.T
  object EventProperties extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
