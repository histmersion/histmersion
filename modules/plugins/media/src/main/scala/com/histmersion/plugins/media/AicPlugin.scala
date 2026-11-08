package com.histmersion
package plugins.media

/** `media-aic`: `histmersion media import aic` — The Art Institute of Chicago's public-domain works. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class AicPlugin extends MediaPlugin("media-aic"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add(Registry.AddId("aic"), ArtInstituteCollection(c.media.aic, c.media.thumbPx))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
