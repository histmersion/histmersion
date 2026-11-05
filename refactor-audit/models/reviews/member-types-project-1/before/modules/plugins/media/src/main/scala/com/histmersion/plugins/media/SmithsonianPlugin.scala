package com.histmersion.plugins.media

/** `media-smithsonian`: `histmersion media import smithsonian` — Smithsonian Open Access objects, with an api.data.gov
  * key.
  */
import com.histmersion.plugin.Plugin
final class SmithsonianPlugin extends KeyedPlugin("media-smithsonian", "smithsonian"):
  protected def collection(c: KeyedPlugin.Config, key: Option[String], ctx: Plugin.Context) =
    SmithsonianCollection(c.media.smithsonian, key)
