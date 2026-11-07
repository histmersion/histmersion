package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.DiscoveryClient
import com.histmersion.upstreams
import com.histmersion.api.library.{RefRow, WorkRef}

/** Discovery over OpenAlex and Crossref ([[upstreams.OpenAlexCrossref]]), its records as the wire shows them. */
final class OpenAlexDiscovery(client: upstreams.openalex.OpenAlexCrossref) extends DiscoveryClient:
  def search(query: com.histmersion.domain.library.search.Search.Query, since: Option[com.histmersion.Year], sort: Option[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.Sort], limit: com.histmersion.domain.library.acquisition.OpenAlexDiscovery.Limit): Either[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.Problem, List[WorkRef]] =
    client.search(query, since, sort, limit).map(_.map(_.wire))
  def meta(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.Problem, WorkRef] = client.meta(doi).map(_.wire)
  def references(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.OpenAlexDiscovery.Problem, (com.histmersion.domain.library.acquisition.OpenAlexDiscovery.ReferencesResultPart2Part1, List[RefRow])] =
    client.references(doi).map((backend, rows) => (backend, rows.map(_.wire)))
  def updates(doi: com.histmersion.domain.library.Doi) = client.updates(doi).map(DiscoveryClient.notices)

object OpenAlexDiscovery:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sort = Sort.T
  object Sort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencesResultPart2Part1 = ReferencesResultPart2Part1.T
  object ReferencesResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
