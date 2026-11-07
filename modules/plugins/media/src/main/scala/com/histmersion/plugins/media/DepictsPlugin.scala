package com.histmersion.plugins.media

/** `media-depicts`: `histmersion media import depicts` — Commons files that depict events. */
import com.histmersion.plugin.Plugin
final class DepictsPlugin extends MediaPlugin("media-depicts"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("depicts"), Commons.Depictions(c.media, ctx.licences)).left.map(e => Plugin.RegisterResultPart1(e.value))

