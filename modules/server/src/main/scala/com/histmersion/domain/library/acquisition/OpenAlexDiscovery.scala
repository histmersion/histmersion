package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.domain.library.acquisition.DiscoveryClient
import com.histmersion.upstreams
import com.histmersion.api.library.{RefRow, WorkRef}
import com.histmersion.upstreams.openalex.OpenAlexCrossref
import com.histmersion.domain.library.search.Search
import com.histmersion.upstreams.PageSize
import com.histmersion.domain.library.Doi
import com.histmersion.lint.{LintRule, tolerated}

/** Discovery over OpenAlex and Crossref ([[upstreams.OpenAlexCrossref]]), its records as the wire shows them. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class OpenAlexDiscovery(client: OpenAlexCrossref) extends DiscoveryClient:
  private def problem(p: Problem) = p

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def search(
      query: Search.Query,
      since: Option[Year],
      sort: Option[DiscoveryClient.SearchSort],
      limit: DiscoveryClient.SearchLimit
  ): Either[Problems, List[WorkRef]] =
    client
      .search(
        OpenAlexCrossref.SearchQuery(query.value),
        since.map(y => plugin.Year(y.value)),
        sort.map(s => OpenAlexCrossref.SearchSort(s.value)),
        PageSize(limit.value)
      )
      .map(_.map(_.wire))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def meta(doi: Doi): Either[Problem, WorkRef] =
    client.meta(plugin.Doi(doi.value)).left.map(problem).map(_.wire)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def references(doi: Doi): Either[Problem, (DiscoveryClient.Backend, List[RefRow])] =
    client
      .references(plugin.Doi(doi.value))
      .left
      .map(problem)
      .map((backend, rows) => (DiscoveryClient.Backend(backend.value), rows.map(_.wire)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def updates(doi: Doi) =
    client.updates(plugin.Doi(doi.value)).left.map(problem).map(DiscoveryClient.notices)
