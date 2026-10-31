package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-met`: `histmersion media import met` — The Metropolitan Museum of Art's Open Access objects. */
final class MetPlugin extends MediaPlugin("media-met"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("met", MetCollection(c.media.met))
