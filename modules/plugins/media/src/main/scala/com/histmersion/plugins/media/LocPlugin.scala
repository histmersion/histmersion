package com.histmersion.plugins.media

/** `media-loc`: `histmersion media import loc` — the Library of Congress's public-domain maps. */
import com.histmersion.plugin.Plugin
final class LocPlugin extends MediaPlugin("media-loc"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugin.Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("loc"), LocCollection(c.media.loc, c.media.thumbPx))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
