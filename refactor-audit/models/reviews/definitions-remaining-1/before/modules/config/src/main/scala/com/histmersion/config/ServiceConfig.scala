package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A keyless upstream service. */
final case class ServiceConfig(url: String) derives ConfigReader
