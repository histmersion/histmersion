package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A keyless upstream service. */
final case class ServiceConfig(url: ServiceConfig.Url) derives ConfigReader

object ServiceConfig:
  type Url = ServiceUrl
  val Url: ServiceUrl.type = ServiceUrl
