package com.histmersion.plugins.events

import com.histmersion.plugin.PluginContext

/** `events-wikidata`: `histmersion events import wikidata` — Wikidata's battles, sieges, wars, treaties … (the
  * configured classes).
  */
final class WikidataPlugin extends EventsPlugin("events-wikidata"):
  type Config = WikidataConfig
  def register(c: WikidataConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.eventCatalogues.add("wikidata", WikidataCatalogue(c.events.wikidata))
