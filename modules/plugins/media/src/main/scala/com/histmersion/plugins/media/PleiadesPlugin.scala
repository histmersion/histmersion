package com.histmersion.plugins.media

/** `media-pleiades`: `histmersion media import pleiades` — the Commons files Pleiades place records link. */
import com.histmersion.plugin.Plugin
final class PleiadesPlugin extends MediaPlugin("media-pleiades"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugin.Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("pleiades"), PleiadesPictures(c.media, ctx.licences))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
