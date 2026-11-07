package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{RefRow, UpdateNotice, WorkRef}

/** OpenAlex + Crossref: search, metadata, deposited reference lists and amendment notices. */
trait DiscoveryClient:
  def search(query: com.histmersion.domain.library.search.Search.Query, since: Option[com.histmersion.Year], sort: Option[com.histmersion.domain.library.acquisition.DiscoveryClient.SearchSort], limit: com.histmersion.domain.library.acquisition.DiscoveryClient.SearchLimit): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.Problem, List[WorkRef]]
  def meta(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.Problem, WorkRef]

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.Problem, (com.histmersion.domain.library.acquisition.DiscoveryClient.ReferencesResultPart2Part1, List[RefRow])]

  /** Crossref `update-to` notices (retraction / correction / expression of concern). */
  def updates(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.DiscoveryClient.Problem, List[UpdateNotice]]

object DiscoveryClient:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Crossref's notices as the wire shows them. */
  type SearchSort = SearchSort.T
  object SearchSort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencesResultPart2Part1 = ReferencesResultPart2Part1.T
  object ReferencesResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def notices(found: List[com.histmersion.domain.library.acquisition.UpdateNotice]): List[UpdateNotice] =
    found.map(n => UpdateNotice(n.kind, n.label, n.doi))
