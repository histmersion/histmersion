package com.histmersion.plugins.media

/** `media-smithsonian`: `histmersion media import smithsonian` — Smithsonian Open Access objects, with an api.data.gov
  * key.
  */
import com.histmersion.plugin.Plugin
final class SmithsonianPlugin extends KeyedPlugin(com.histmersion.plugins.media.KeyedPlugin.Id("media-smithsonian"), com.histmersion.plugins.media.KeyedPlugin.Name("smithsonian")):
  protected def collection(c: KeyedPlugin.Config, key: Option[com.histmersion.plugins.media.KeyedCollection.Key], ctx: Plugin.Context) =
    SmithsonianCollection(c.media.smithsonian, key)

