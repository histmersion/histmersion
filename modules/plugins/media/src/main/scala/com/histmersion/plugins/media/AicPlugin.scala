package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-aic`: `histmersion media import aic` — The Art Institute of Chicago's public-domain works. */
final class AicPlugin extends MediaPlugin("media-aic"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("aic", ArtInstituteCollection(c.media.aic, c.media.thumbPx))
