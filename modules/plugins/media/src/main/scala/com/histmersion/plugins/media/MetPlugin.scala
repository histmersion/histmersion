package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem

/** `media-met`: `histmersion media import met` — The Metropolitan Museum of Art's Open Access objects. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class MetPlugin extends MediaPlugin("media-met"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.mediaCollections
      .add(Registry.AddId("met"), MetCollection(c.media.met))
