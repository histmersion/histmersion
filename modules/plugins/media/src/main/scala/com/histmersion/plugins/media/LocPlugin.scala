package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-loc`: `histmersion media import loc` — the Library of Congress's public-domain maps. */
final class LocPlugin extends MediaPlugin("media-loc"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("loc", LocCollection(c.media.loc, c.media.thumbPx))
