package com.histmersion
package plugins.media

/** `media-smithsonian`: `histmersion media import smithsonian` — Smithsonian Open Access objects, with an api.data.gov
  * key.
  */
import com.histmersion.plugin.Plugin
final class SmithsonianPlugin extends KeyedPlugin(KeyedPlugin.Id("media-smithsonian"), KeyedPlugin.Name("smithsonian")):
  protected def collection(c: KeyedPlugin.Config, key: Option[KeyedCollection.Key], ctx: Plugin.Context) =
    SmithsonianCollection(c.media.smithsonian, key)

