package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class DbConfig(url: String, user: String, password: String, pool: DbPoolConfig) derives ConfigReader
