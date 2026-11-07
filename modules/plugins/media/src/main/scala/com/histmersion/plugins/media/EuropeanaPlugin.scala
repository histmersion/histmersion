package com.histmersion.plugins.media

/** `media-europeana`: `histmersion media import europeana` — Europeana's openly licensed images, with a personal API
  * key.
  */
import com.histmersion.plugin.Plugin
final class EuropeanaPlugin extends KeyedPlugin(com.histmersion.plugins.media.KeyedPlugin.Id("media-europeana"), com.histmersion.plugins.media.KeyedPlugin.Name("europeana")):
  protected def collection(c: KeyedPlugin.Config, key: Option[com.histmersion.plugins.media.KeyedCollection.Key], ctx: Plugin.Context) =
    EuropeanaCollection(c.media.europeana, key, ctx.licences)

