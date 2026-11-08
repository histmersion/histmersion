package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.acquisition.CoreQuotaLend

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.pluginhost.upstreamSettings
import com.histmersion.upstreams.core.CoreApi
import com.histmersion.util.plugins.Plugins

object Indexes:
  /** The indexes `acquire.candidates.indexes` names, in its order (an unknown name is left out). `coreKey` is CORE's
    * key as the plugins resolved it ([[com.histmersion.util.plugins.Plugins.coreKey]]), else `acquire.core.api-key`;
    * `core` the server's CORE budget.
    */


  def of(
      cfg: AcquireConfig,
      http: Http,
      coreKey: Option[Plugins.CoreKey] = None,
      core: CoreBudget
  ): (List[ScholarlyIndex], CitationAlerts) =
    val key = coreKey.map(k => ApiKey(k.value)).orElse(cfg.core.key)
    val openalex = OpenAlexIndex(cfg, http)
    val all: Map[ScholarlyIndex.Name, ScholarlyIndex] = Map(
      ScholarlyIndex.Name("openalex") -> openalex,
      ScholarlyIndex.Name("crossref") -> CrossrefIndex(cfg, http),
      ScholarlyIndex.Name("europepmc") -> EuropePmcIndex(cfg, http),
      ScholarlyIndex.Name("arxiv") -> ArxivIndex(cfg, http),
      ScholarlyIndex.Name("core") -> {
        val limits = CoreBudget.limits(cfg.core)
        CoreIndex(CoreApi(cfg.upstreamSettings.core, HttpPlugin(http), CoreQuotaLend(core, limits)), key, core, limits)
      }
    )
    // CORE only with its key (its search refuses anonymous use): without one it is not asked at all
    (
      cfg.candidates.indexes
        .map(_.value.trim.toLowerCase)
        .distinct
        .filter(n => n != "core" || key.isDefined)
        .flatMap(n => all.get(ScholarlyIndex.Name(n))),
      openalex
    )
