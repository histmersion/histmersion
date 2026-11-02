package com.histmersion.plugins.media

/** `media-met`: `histmersion media import met` — The Metropolitan Museum of Art's Open Access objects. */
import com.histmersion.plugin.Plugin
final class MetPlugin extends MediaPlugin("media-met"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[String, Unit] =
    ctx.mediaCollections.add("met", MetCollection(c.media.met))
