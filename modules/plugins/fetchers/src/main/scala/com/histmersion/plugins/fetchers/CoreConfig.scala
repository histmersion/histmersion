package com.histmersion.plugins.fetchers

import com.histmersion.plugin.Secret
import com.histmersion.upstreams.settings.UpstreamSettings
import hearth.kindlings.sconfigderivation.ConfigReader

/** `oa-core`'s settings. `api-key`: CORE's API key as a secret reference (`env:<NAME>`, a vault's); `null` =
  * Histmersion's own `acquire.core.api-key`; without a key CORE is not asked. The host reads the key this plugin
  * resolved for the new-source search's `core` index too: ONE setting enables both.
  */
final case class CoreConfig(apiKey: Option[Secret], acquire: UpstreamSettings) derives ConfigReader
