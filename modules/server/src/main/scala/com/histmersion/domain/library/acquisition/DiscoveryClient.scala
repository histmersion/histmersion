package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{RefRow, UpdateNotice, WorkRef}
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** OpenAlex + Crossref: search, metadata, deposited reference lists and amendment notices. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait DiscoveryClient:
  def search(
      query: Search.Query,
      since: Option[Year],
      sort: Option[DiscoveryClient.SearchSort],
      limit: DiscoveryClient.SearchLimit
  ): Either[Problems, List[WorkRef]]
  def meta(doi: Doi): Either[Problem, WorkRef]

  /** `(backend, references)` — Crossref's deposited list, falling back to OpenAlex's `referenced_works` resolved to
    * titles when the publisher deposited none.
    */
  def references(doi: Doi): Either[Problem, (DiscoveryClient.Backend, List[RefRow])]

  /** Crossref `update-to` notices (retraction / correction / expression of concern). */
  def updates(doi: Doi): Either[Problem, List[UpdateNotice]]

object DiscoveryClient:
  type SearchSort = SearchSort.T
  object SearchSort extends Newtype[String, Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends Newtype[Int, Pure]
  type Backend = Backend.T
  object Backend extends Newtype[String, Pure]

  /** Crossref's notices as the wire shows them. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def notices(found: List[com.histmersion.domain.library.acquisition.UpdateNotice]): List[UpdateNotice] =
    found.map(n =>
      UpdateNotice(
        n.kind.map(k => UpdateNotice.Kind(k.value)),
        n.label.map(l => UpdateNotice.Label(l.value)),
        n.doi.map(d => com.histmersion.api.library.Doi(d.value))
      )
    )
