package com.histmersion.server.media

import com.histmersion.domain.{MediaLicence, MediaMatch, MediaRecord, MediaRights as CoreRights}
import com.histmersion.plugin.*

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given

/** A key-gated collection's plugin settings. `api-key`: the key as a SECRET REFERENCE (`env:<NAME>`, a vault's), never
  * the key; `null` = none, and the collection is then not asked.
  */
final case class KeyedConfig(apiKey: Option[Secret]) derives ConfigReader

object KeyedConfig:
  private[media] def nonBlank(v: String): Option[String] = Option(v).map(_.trim).filter(_.nonEmpty)
