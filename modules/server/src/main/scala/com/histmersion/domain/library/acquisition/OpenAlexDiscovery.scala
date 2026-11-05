package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.DiscoveryClient
import com.histmersion.upstreams
import com.histmersion.api.library.{RefRow, WorkRef}

/** Discovery over OpenAlex and Crossref ([[upstreams.OpenAlexCrossref]]), its records as the wire shows them. */
final class OpenAlexDiscovery(client: upstreams.openalex.OpenAlexCrossref) extends DiscoveryClient:
  def search(query: com.histmersion.domain.library.acquisition.OpenAlexDiscovery.SearchQuery, since: Option[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.SearchSince], sort: Option[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.SearchSort], limit: com.histmersion.domain.library.acquisition.OpenAlexDiscovery.SearchLimit): Either[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.SearchResultPart1, List[WorkRef]] =
    client.search(query, since, sort, limit).map(_.map(_.wire))
  def meta(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.MetaResultPart1, WorkRef] = client.meta(doi).map(_.wire)
  def references(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.ReferencesResultPart1, (com.histmersion.domain.library.acquisition.OpenAlexDiscovery.ReferencesResultPart2Part1, List[RefRow])] =
    client.references(doi).map((backend, rows) => (backend, rows.map(_.wire)))
  def updates(doi: com.histmersion.domain.library.Doi) = client.updates(doi).map(DiscoveryClient.notices)

object OpenAlexDiscovery:
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchSince = SearchSince.T
  object SearchSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchSort = SearchSort.T
  object SearchSort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaResultPart1 = MetaResultPart1.T
  object MetaResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencesResultPart1 = ReferencesResultPart1.T
  object ReferencesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencesResultPart2Part1 = ReferencesResultPart2Part1.T
  object ReferencesResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
