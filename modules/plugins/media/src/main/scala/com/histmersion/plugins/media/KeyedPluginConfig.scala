package com.histmersion.plugins.media

import com.histmersion.plugin.Secret
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A key-gated collection's settings: `api-key`, the key as a SECRET REFERENCE (`env:<NAME>`, a vault's), never the
  * key; `null` = none, and the collection is then not asked. Its other settings are Histmersion's own `media.*`.
  */
final case class KeyedPluginConfig(apiKey: Option[Secret], media: MediaSettings) derives ConfigReader
