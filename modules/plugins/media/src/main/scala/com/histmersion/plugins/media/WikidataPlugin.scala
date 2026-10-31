package com.histmersion.plugins.media

import com.histmersion.plugin.PluginContext

/** `media-wikidata`: `histmersion media import wikidata` — pictures of places and events from their Wikidata items. */
final class WikidataPlugin extends MediaPlugin("media-wikidata"):
  type Config = MediaPluginConfig
  def register(c: MediaPluginConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("wikidata", WikidataPictures(c.media, ctx.licences))
