package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-smithsonian`: `histmersion media import smithsonian` — Smithsonian Open Access objects, with an api.data.gov
  * key.
  */
final class SmithsonianPlugin extends KeyedPlugin("media-smithsonian", "smithsonian"):
  protected def collection(c: KeyedPluginConfig, key: Option[String], ctx: PluginContext) =
    SmithsonianCollection(c.media.smithsonian, key)
