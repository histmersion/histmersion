package com.histmersion.domain.library.acquisition

import com.histmersion.pluginhost.OaFetcher

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** The acquisition clients of one config and one plugin generation (the server's `acquisition` hot node): rebuilt
  * together when `acquire.*` or the plugins change.
  */
final case class AcquireClients(
    discovery: DiscoveryClient,
    citations: CitationsClient,
    oa: OaFetcher,
    wiki: WikiClient,
    retractionWatch: RetractionWatchClient,
    /** The indexes a goal's watches read, and the citation/author alerts (#3761). */
    indexes: List[ScholarlyIndex] = Nil,
    alerts: CitationAlerts = NoAlerts,
    /** The legal chain's generation ([[FetcherGeneration]]): what makes the acquisition list due again. */
    generation: String = ""
)
