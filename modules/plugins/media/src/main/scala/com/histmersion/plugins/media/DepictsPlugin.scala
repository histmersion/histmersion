package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-depicts`: `histmersion media import depicts` — Commons files that depict events. */
final class DepictsPlugin extends MediaPlugin("media-depicts"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("depicts", CommonsDepictions(c.media, ctx.licences))
