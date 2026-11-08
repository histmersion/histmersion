package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaWikidataConfig(
    apiUrl: MediaWikidataConfig.ApiUrl,
    batch: MediaWikidataConfig.Batch,
    maxlag: MediaWikidataConfig.Maxlag,
    placeProperties: MediaWikidataConfig.PlaceProperties,
    eventProperties: MediaWikidataConfig.EventProperties
) derives ConfigReader

object MediaWikidataConfig:
  type ApiUrl = ApiUrl.T
  object ApiUrl extends RefinedType[String, Pure]
  type Batch = Batch.T
  object Batch extends RefinedType[Int, Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends RefinedType[Int, Pure]
  type PlaceProperties = PlaceProperties.T
  object PlaceProperties extends RefinedType[String, Pure]
  type EventProperties = EventProperties.T
  object EventProperties extends RefinedType[String, Pure]
