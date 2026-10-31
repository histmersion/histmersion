package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-cleveland`: `histmersion media import cleveland` — The Cleveland Museum of Art's Open Access objects. */
final class ClevelandPlugin extends MediaPlugin("media-cleveland"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("cleveland", ClevelandCollection(c.media.cleveland))
