package com.histmersion.plugins.media

/** `media-depicts`: `histmersion media import depicts` — Commons files that depict events. */
import com.histmersion.plugin.Plugin
final class DepictsPlugin extends MediaPlugin("media-depicts"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[String, Unit] =
    ctx.mediaCollections.add("depicts", Commons.Depictions(c.media, ctx.licences))
