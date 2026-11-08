package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem

/** `media-wikidata`: `histmersion media import wikidata` — pictures of places and events from their Wikidata items. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class WikidataPlugin extends MediaPlugin("media-wikidata"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.mediaCollections
      .add(Registry.AddId("wikidata"), WikidataPictures(c.media, ctx.licences))
