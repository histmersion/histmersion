package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A keyless upstream service. */
final case class ServiceConfig(url: ServiceConfig.Url) derives ConfigReader

object ServiceConfig:
  type Url = com.histmersion.config.ServiceUrl
  val Url: _root_.com.histmersion.config.ServiceUrl.type = _root_.com.histmersion.config.ServiceUrl
