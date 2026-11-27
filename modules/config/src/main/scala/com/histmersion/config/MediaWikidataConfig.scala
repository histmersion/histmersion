package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.Media)
final case class MediaWikidataConfig(
    apiUrl: MediaWikidataConfig.ApiUrl,
    batch: MediaWikidataConfig.Batch,
    maxlag: MediaWikidataConfig.Maxlag,
    placeProperties: MediaWikidataConfig.PlaceProperties,
    eventProperties: MediaWikidataConfig.EventProperties
) derives ConfigReader

object MediaWikidataConfig:
  type ApiUrl = ApiUrl.T
  object ApiUrl extends Newtype[String, Pure]
  type Batch = Batch.T
  object Batch extends Newtype[Int, Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends Newtype[Int, Pure]
  type PlaceProperties = PlaceProperties.T
  object PlaceProperties extends Newtype[String, Pure]
  type EventProperties = EventProperties.T
  object EventProperties extends Newtype[String, Pure]
