package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-pleiades`: `histmersion media import pleiades` — the Commons files Pleiades place records link. */
final class PleiadesPlugin extends MediaPlugin("media-pleiades"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("pleiades", PleiadesPictures(c.media, ctx.licences))
