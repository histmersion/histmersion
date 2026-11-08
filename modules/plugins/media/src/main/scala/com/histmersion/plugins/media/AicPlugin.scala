package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem

/** `media-aic`: `histmersion media import aic` — The Art Institute of Chicago's public-domain works. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class AicPlugin extends MediaPlugin("media-aic"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.mediaCollections
      .add(Registry.AddId("aic"), ArtInstituteCollection(c.media.aic, c.media.thumbPx))
      .left
      .map(e => Problem(e.value))
