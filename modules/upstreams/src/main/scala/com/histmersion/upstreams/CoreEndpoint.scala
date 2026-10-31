package com.histmersion.upstreams

import hearth.kindlings.sconfigderivation.ConfigReader

/** CORE's endpoint and its plain key (`histmersion.acquire.core.url`, `.api-key`); the budget's limits are the host's.
  */
final case class CoreEndpoint(url: String, apiKey: String) derives ConfigReader:
  /** The key, when one is configured. */
  def key: Option[String] = Option(apiKey).map(_.trim).filter(_.nonEmpty)
