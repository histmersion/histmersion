package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.CoreQuotaLend

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.pluginhost.upstreamSettings
import com.histmersion.upstreams.CoreApi

object Indexes:
  /** The indexes `acquire.candidates.indexes` names, in its order (an unknown name is left out). `coreKey` is CORE's
    * key as the plugins resolved it ([[com.histmersion.util.plugins.Plugins.coreKey]]), else `acquire.core.api-key`;
    * `core` the server's CORE budget.
    */
  def of(
      cfg: AcquireConfig,
      http: Http,
      coreKey: Option[String] = None,
      core: CoreBudget = CoreBudget()
  ): (List[ScholarlyIndex], CitationAlerts) =
    val key = coreKey.orElse(cfg.core.key)
    val openalex = OpenAlexIndex(cfg, http)
    val all: Map[String, ScholarlyIndex] = Map(
      "openalex" -> openalex,
      "crossref" -> CrossrefIndex(cfg, http),
      "europepmc" -> EuropePmcIndex(cfg, http),
      "arxiv" -> ArxivIndex(cfg, http),
      "core" -> {
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
        .flatMap(all.get),
      openalex
    )
