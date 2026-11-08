package com.histmersion
package plugins.events

import hearth.kindlings.sconfigderivation.ConfigReader

/** What `events-wikidata` reads of `histmersion.events`: its own block. */
final case class WikidataEventsSettings(wikidata: WikidataSettings) derives ConfigReader
