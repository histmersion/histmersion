package com.histmersion.plugins.media

/** `media-loc`: `histmersion media import loc` — the Library of Congress's public-domain maps. */
import com.histmersion.plugin.Plugin
final class LocPlugin extends MediaPlugin("media-loc"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[String, Unit] =
    ctx.mediaCollections.add("loc", LocCollection(c.media.loc, c.media.thumbPx))
