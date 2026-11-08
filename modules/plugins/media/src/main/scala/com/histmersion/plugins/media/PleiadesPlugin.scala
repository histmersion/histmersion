package com.histmersion
package plugins.media

/** `media-pleiades`: `histmersion media import pleiades` — the Commons files Pleiades place records link. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class PleiadesPlugin extends MediaPlugin("media-pleiades"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections
      .add(Registry.AddId("pleiades"), PleiadesPictures(c.media, ctx.licences))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
