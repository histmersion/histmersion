package com.histmersion
package domain.library.acquisition

import com.histmersion.pluginhost.upstreamSettings
import com.histmersion.upstreams.core.CoreApi
import com.histmersion.util.plugins.Plugins
import com.histmersion.util.problem.Problem

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.WorkRef
import com.histmersion.domain.library.search.Search
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait ScholarlyIndex:
  def name: ScholarlyIndex.Name
  def search(
      query: Search.Query,
      since: LocalDate,
      limit: ScholarlyIndex.Limit
  ): Either[Problem, List[WorkRef]]

  def unavailable: Option[Problem] = None

object ScholarlyIndex:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(
      cfg: AcquireConfig,
      http: Http,
      coreKey: Option[Plugins.CoreKey] = None,
      core: CoreBudget
  ): (List[ScholarlyIndex], CitationAlerts) =
    val key = coreKey.map(k => ApiKey(k.value)).orElse(cfg.core.key)
    val openalex = OpenAlexIndex(cfg, http)
    val all: Map[Name, ScholarlyIndex] = Map(
      Name("openalex") -> openalex,
      Name("crossref") -> CrossrefIndex(cfg, http),
      Name("europepmc") -> EuropePmcIndex(cfg, http),
      Name("arxiv") -> ArxivIndex(cfg, http),
      Name("core") -> {
        val limits = CoreBudget.limits(cfg.core)
        CoreIndex(CoreApi(cfg.upstreamSettings.core, HttpPlugin(http), CoreQuotaLend(core, limits)), key, core, limits)
      }
    )
    (
      cfg.candidates.indexes
        .map(_.value.trim.toLowerCase)
        .distinct
        .filter(n => n != "core" || key.isDefined)
        .flatMap(n => all.get(Name(n))),
      openalex
    )
