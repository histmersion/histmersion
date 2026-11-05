package com.histmersion.domain.library.acquisition
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.acquire.defaults`: the oldest publication year a discovery search considers. */
final case class SearchDefaults(minYear: Int) derives ConfigReader
