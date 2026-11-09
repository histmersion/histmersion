package com.histmersion
package plugins.events
import com.histmersion.util.problem.Problem

/** `events-wikidata`: `histmersion events import wikidata` — Wikidata's battles, sieges, wars, treaties … (the
  * configured classes).
  */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class WikidataPlugin extends EventsPlugin("events-wikidata"):
  type Config = WikidataConfig
  def register(c: WikidataConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.eventCatalogues
      .add(Registry.AddId("wikidata"), WikidataCatalogue(c.events.wikidata))
