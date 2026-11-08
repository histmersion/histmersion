package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
final case class KeyedServiceConfig(url: KeyedServiceConfig.Url, apiKey: ApiKey) derives ConfigReader:
  def key: Option[ApiKey] = Option(apiKey).map(_.value.trim).filter(_.nonEmpty).map(ApiKey(_))

object KeyedServiceConfig:

  type Url = Url.T
  object Url extends RefinedType[String, Pure]
