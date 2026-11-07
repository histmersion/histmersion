package com.histmersion.plugins.events

/** `events-wikidata`: `histmersion events import wikidata` — Wikidata's battles, sieges, wars, treaties … (the
  * configured classes).
  */
import com.histmersion.plugin.Plugin
final class WikidataPlugin extends EventsPlugin("events-wikidata"):
  type Config = WikidataConfig
  def register(c: WikidataConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.events.WikidataPlugin.Problem, Unit] =
    ctx.eventCatalogues.add("wikidata", WikidataCatalogue(c.events.wikidata))

object WikidataPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
