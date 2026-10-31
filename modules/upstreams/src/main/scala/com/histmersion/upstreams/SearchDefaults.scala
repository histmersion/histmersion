package com.histmersion.upstreams

import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.acquire.defaults`: the oldest publication year a discovery search considers. */
final case class SearchDefaults(minYear: Int) derives ConfigReader
