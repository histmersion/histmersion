package com.histmersion.plugins.media

/** `media-europeana`: `histmersion media import europeana` — Europeana's openly licensed images, with a personal API
  * key.
  */
import com.histmersion.plugin.Plugin
final class EuropeanaPlugin extends KeyedPlugin("media-europeana", "europeana"):
  protected def collection(c: KeyedPlugin.Config, key: Option[com.histmersion.plugins.media.EuropeanaCollection.Key], ctx: Plugin.Context) =
    EuropeanaCollection(c.media.europeana, key, ctx.licences)

object EuropeanaPlugin:
