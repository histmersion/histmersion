package com.histmersion.plugins.events

/** `events-wikidata`: `histmersion events import wikidata` — Wikidata's battles, sieges, wars, treaties … (the
  * configured classes).
  */
import com.histmersion.plugin.Plugin
final class WikidataPlugin extends EventsPlugin("events-wikidata"):
  type Config = WikidataConfig
  def register(c: WikidataConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.eventCatalogues
      .add(com.histmersion.plugin.Registry.AddId("wikidata"), WikidataCatalogue(c.events.wikidata))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
