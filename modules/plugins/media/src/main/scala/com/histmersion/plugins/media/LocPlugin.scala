package com.histmersion
package plugins.media

/** `media-loc`: `histmersion media import loc` — the Library of Congress's public-domain maps. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class LocPlugin extends MediaPlugin("media-loc"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections
      .add(Registry.AddId("loc"), LocCollection(c.media.loc, c.media.thumbPx))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
