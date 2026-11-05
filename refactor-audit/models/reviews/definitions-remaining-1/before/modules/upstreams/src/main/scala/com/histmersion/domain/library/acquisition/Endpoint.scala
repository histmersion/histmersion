package com.histmersion.domain.library.acquisition
import hearth.kindlings.sconfigderivation.ConfigReader

/** A keyless upstream's endpoint (`histmersion.acquire.<upstream>.url`). */
final case class Endpoint(url: String) derives ConfigReader
