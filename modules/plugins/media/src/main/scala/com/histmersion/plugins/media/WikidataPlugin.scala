package com.histmersion.plugins.media

/** `media-wikidata`: `histmersion media import wikidata` — pictures of places and events from their Wikidata items. */
import com.histmersion.plugin.Plugin
final class WikidataPlugin extends MediaPlugin("media-wikidata"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[String, Unit] =
    ctx.mediaCollections.add("wikidata", WikidataPictures(c.media, ctx.licences))
