package com.histmersion
package plugins.media

/** `media-wikidata`: `histmersion media import wikidata` — pictures of places and events from their Wikidata items. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class WikidataPlugin extends MediaPlugin("media-wikidata"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections
      .add(Registry.AddId("wikidata"), WikidataPictures(c.media, ctx.licences))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
