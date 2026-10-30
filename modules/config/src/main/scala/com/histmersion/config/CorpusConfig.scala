package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class CorpusConfig(dir: String) derives ConfigReader
