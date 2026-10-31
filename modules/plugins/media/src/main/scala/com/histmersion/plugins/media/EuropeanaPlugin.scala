package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-europeana`: `histmersion media import europeana` — Europeana's openly licensed images, with a personal API
  * key.
  */
final class EuropeanaPlugin extends KeyedPlugin("media-europeana", "europeana"):
  protected def collection(c: KeyedPluginConfig, key: Option[String], ctx: PluginContext) =
    EuropeanaCollection(c.media.europeana, key, ctx.licences)
