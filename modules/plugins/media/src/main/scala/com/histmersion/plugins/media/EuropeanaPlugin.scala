package com.histmersion
package plugins.media

/** `media-europeana`: `histmersion media import europeana` — Europeana's openly licensed images, with a personal API
  * key.
  */
import com.histmersion.plugin.Plugin
final class EuropeanaPlugin extends KeyedPlugin(KeyedPlugin.Id("media-europeana"), KeyedPlugin.Name("europeana")):
  protected def collection(c: KeyedPlugin.Config, key: Option[KeyedCollection.Key], ctx: Plugin.Context) =
    EuropeanaCollection(c.media.europeana, key, ctx.licences)

