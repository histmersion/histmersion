package com.histmersion.plugins.media

/** `media-cleveland`: `histmersion media import cleveland` — The Cleveland Museum of Art's Open Access objects. */
import com.histmersion.plugin.Plugin
final class ClevelandPlugin extends MediaPlugin("media-cleveland"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections
      .add(com.histmersion.plugin.Registry.AddId("cleveland"), ClevelandCollection(c.media.cleveland))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
