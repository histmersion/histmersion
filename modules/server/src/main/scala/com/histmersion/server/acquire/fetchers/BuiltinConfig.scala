package com.histmersion.server.acquire.fetchers

import com.histmersion.plugin.*

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given
import com.histmersion.api.VocabularyCodecs.given

/** A built-in plugin's settings: none of its own beyond the host's `enabled`. */
final case class BuiltinConfig() derives ConfigReader
