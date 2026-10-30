package com.histmersion.server.media

import com.histmersion.domain.{MediaLicence, MediaMatch, MediaRecord, MediaRights as CoreRights}
import com.histmersion.plugin.*

import com.histmersion.plugin.PluginConfig.given

/** What a key-gated built-in collection is, before its key is known. `collection` builds it over the media settings and
  * the key; `enabled` reads its own `media.<provider>.enabled`.
  */
final case class KeyedMeta(
    plugin: String,
    name: String,
    title: String,
    needs: String,
    description: String,
    collection: (com.histmersion.config.MediaConfig, String) => Collection,
    settings: String,
    enabled: com.histmersion.config.MediaConfig => Boolean
)
