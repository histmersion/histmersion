package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{RefRow, UpdateNotice, WorkRef}

/** OpenAlex + Crossref: search, metadata, deposited reference lists and amendment notices. */
trait DiscoveryClient:
  def search(query: com.histmersion.domain.library.acquisition.DiscoveryClient.SearchQuery, since: Option[com.histmersion.domain.library.acquisition.DiscoveryClient.SearchSince], sort: Option[com.histmersion.domain.library.acquisition.DiscoveryClient.SearchSort], limit: com.histmersion.domain.library.acquisition.DiscoveryClient.SearchLimit): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.SearchResultPart1, List[WorkRef]]
  def meta(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.MetaResultPart1, WorkRef]

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.ReferencesResultPart1, (com.histmersion.domain.library.acquisition.DiscoveryClient.ReferencesResultPart2Part1, List[RefRow])]

  /** Crossref `update-to` notices (retraction / correction / expression of concern). */
  def updates(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.UpdatesResultPart1, List[UpdateNotice]]

object DiscoveryClient:
  /** Crossref's notices as the wire shows them. */
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
  type UpdatesResultPart1 = UpdatesResultPart1.T
  object UpdatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def notices(found: List[com.histmersion.domain.library.acquisition.UpdateNotice]): List[UpdateNotice] =
    found.map(n => UpdateNotice(n.kind, n.label, n.doi))
