package com.histmersion.plugins.media

/** `media-aic`: `histmersion media import aic` — The Art Institute of Chicago's public-domain works. */
import com.histmersion.plugin.Plugin
final class AicPlugin extends MediaPlugin("media-aic"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[String, Unit] =
    ctx.mediaCollections.add("aic", ArtInstituteCollection(c.media.aic, c.media.thumbPx))
