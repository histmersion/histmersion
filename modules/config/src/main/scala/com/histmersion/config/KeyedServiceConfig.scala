package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
final case class KeyedServiceConfig(url: KeyedServiceConfig.Url, apiKey: KeyedServiceConfig.ApiKey) derives ConfigReader:
  def key: Option[com.histmersion.config.KeyedServiceConfig.KeyResult] = Option(apiKey).map(_.trim).filter(_.nonEmpty)

object KeyedServiceConfig:
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiKey = ApiKey.T
  object ApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
