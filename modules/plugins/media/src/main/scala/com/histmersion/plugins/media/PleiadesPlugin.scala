package com.histmersion.plugins.media

/** `media-pleiades`: `histmersion media import pleiades` — the Commons files Pleiades place records link. */
import com.histmersion.plugin.Plugin
final class PleiadesPlugin extends MediaPlugin("media-pleiades"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[String, Unit] =
    ctx.mediaCollections.add("pleiades", PleiadesPictures(c.media, ctx.licences))
