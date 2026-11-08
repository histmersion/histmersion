package com.histmersion
package plugins.events

import hearth.kindlings.sconfigderivation.ConfigReader

/** `events-wikidata`'s settings: Histmersion's own `events.wikidata.*`, which the host supplies. */
final case class WikidataConfig(events: WikidataEventsSettings) derives ConfigReader
