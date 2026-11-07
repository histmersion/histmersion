package com.histmersion.plugins.media

/** `media-opencontext`: `histmersion media import opencontext` — Open Context's excavation media. */
import com.histmersion.plugin.Plugin
final class OpenContextPlugin extends MediaPlugin("media-opencontext"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("opencontext"), OpenContextCollection(c.media.openContext, ctx.licences)).left.map(e => Plugin.RegisterResultPart1(e.value))

