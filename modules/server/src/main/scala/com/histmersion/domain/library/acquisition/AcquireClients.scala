package com.histmersion
package domain.library.acquisition

import com.histmersion.pluginhost.OaFetcher

import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure

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
    generation: AcquireClients.Generation = AcquireClients.Generation("")
)

object AcquireClients:
  type Generation = Generation.T
  object Generation extends Newtype[String, Pure]
