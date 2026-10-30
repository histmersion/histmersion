package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class MediaCommonsConfig(apiUrl: String, batch: Int, maxlag: Int, licences: String) derives ConfigReader
