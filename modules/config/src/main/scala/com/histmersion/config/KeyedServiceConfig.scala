package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
final case class KeyedServiceConfig(url: String, apiKey: String) derives ConfigReader:
  def key: Option[String] = Option(apiKey).map(_.trim).filter(_.nonEmpty)
