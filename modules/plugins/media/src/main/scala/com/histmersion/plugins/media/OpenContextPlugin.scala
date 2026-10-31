package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-opencontext`: `histmersion media import opencontext` — Open Context's excavation media. */
final class OpenContextPlugin extends MediaPlugin("media-opencontext"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("opencontext", OpenContextCollection(c.media.openContext, ctx.licences))
