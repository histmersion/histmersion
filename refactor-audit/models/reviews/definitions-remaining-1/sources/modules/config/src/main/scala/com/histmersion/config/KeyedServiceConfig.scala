package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
final case class KeyedServiceConfig(url: KeyedServiceConfig.Url, apiKey: KeyedServiceConfig.ApiKey) derives ConfigReader:
  def key: Option[String] = Option(apiKey).map(_.trim).filter(_.nonEmpty)

object KeyedServiceConfig:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiKey = ApiKey.T
  object ApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
