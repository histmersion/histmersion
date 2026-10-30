package com.histmersion.server.acquire.fetchers

import com.histmersion.plugin.*

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given
import com.histmersion.api.VocabularyCodecs.given

/** `oa-core`'s settings. `api-key`: CORE's API key as a secret reference (`env:<NAME>`, a vault's); `null` =
  * Histmersion's own `acquire.core.api-key`; without a key CORE is not asked. The key it resolves is the new-source
  * search's `core` index's too ([[CoreFetcher.key]]).
  */
final case class CoreConfig(apiKey: Option[Secret]) derives ConfigReader

object CoreConfig:
  private[fetchers] def nonBlank(v: String): Option[String] = Option(v).map(_.trim).filter(_.nonEmpty)
