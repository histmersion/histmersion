package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Events from catalogues (#1449): `events` in reference.conf. The HTTP politeness (User-Agent, retries, Retry-After
  * cap) is `media`'s; each catalogue has its own pace.
  */
final case class EventsConfig(
    timeoutSeconds: Int,
    minYear: Long,
    maxYear: Long,
    region: String,
    mergeKm: Double,
    mergeYears: Long,
    defaultLimit: Int,
    /** Records `events rederive` writes in one transaction. */
    rederiveBatch: Int,
    gvp: EventsGvpConfig,
    noaa: EventsNoaaConfig,
    wikidata: EventsWikidataConfig,
    links: EventsLinksConfig,
    leads: EventsLeadsConfig,
    siteTypes: SiteTypesConfig
) derives ConfigReader
