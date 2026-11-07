package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.DiscoveryClient
import com.histmersion.upstreams
import com.histmersion.api.library.{RefRow, WorkRef}

/** Discovery over OpenAlex and Crossref ([[upstreams.OpenAlexCrossref]]), its records as the wire shows them. */
final class OpenAlexDiscovery(client: upstreams.openalex.OpenAlexCrossref) extends DiscoveryClient:
  private def problem(p: WorkLookup.Problem) = DiscoveryClient.Problem(p.value)

  def search(query: com.histmersion.domain.library.search.Search.Query, since: Option[com.histmersion.Year], sort: Option[DiscoveryClient.SearchSort], limit: DiscoveryClient.SearchLimit): Either[DiscoveryClient.Problem, List[WorkRef]] =
    client
      .search(
        upstreams.openalex.OpenAlexCrossref.SearchQuery(query.value),
        since.map(y => com.histmersion.plugin.Year(y.value)),
        sort.map(s => upstreams.openalex.OpenAlexCrossref.SearchSort(s.value)),
        upstreams.PageSize(limit.value)
      )
      .left
      .map(problem)
      .map(_.map(_.wire))
  def meta(doi: com.histmersion.domain.library.Doi): Either[DiscoveryClient.Problem, WorkRef] =
    client.meta(com.histmersion.plugin.Doi(doi.value)).left.map(problem).map(_.wire)
  def references(doi: com.histmersion.domain.library.Doi): Either[DiscoveryClient.Problem, (DiscoveryClient.Backend, List[RefRow])] =
    client
      .references(com.histmersion.plugin.Doi(doi.value))
      .left
      .map(problem)
      .map((backend, rows) => (DiscoveryClient.Backend(backend.value), rows.map(_.wire)))
  def updates(doi: com.histmersion.domain.library.Doi) =
    client.updates(com.histmersion.plugin.Doi(doi.value)).left.map(problem).map(DiscoveryClient.notices)
