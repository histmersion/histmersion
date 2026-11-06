package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
final case class KeyedServiceConfig(url: KeyedServiceConfig.Url, apiKey: ApiKey) derives ConfigReader:
  def key: Option[ApiKey] = Option(apiKey).map(_.value.trim).filter(_.nonEmpty).map(ApiKey(_))

object KeyedServiceConfig:

  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
