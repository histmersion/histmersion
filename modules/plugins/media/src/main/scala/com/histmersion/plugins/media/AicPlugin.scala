package com.histmersion.plugins.media

/** `media-aic`: `histmersion media import aic` — The Art Institute of Chicago's public-domain works. */
import com.histmersion.plugin.Plugin
final class AicPlugin extends MediaPlugin("media-aic"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugin.Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("aic"), ArtInstituteCollection(c.media.aic, c.media.thumbPx))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
