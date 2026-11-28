package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.corequota.Ceilings
import com.histmersion.domain.corequota.Purpose
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.config.ApiKey
import com.histmersion.domain.*
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.upstreams.PageSize
import com.histmersion.upstreams.core.CoreApi

import java.time.LocalDate
import com.histmersion.api.library.Candidates
import com.histmersion.domain.library.search.Search
import com.histmersion.lint.{tolerated, LintRule}

/** CORE: only with a key (its search refuses anonymous use) — the `oa-core` plugin's resolved `api-key`, else
  * `acquire.core.api-key` — and through the server's CORE budget, at most `acquire.core.search-daily-tokens` a day.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class CoreIndex(api: CoreApi, key: Option[ApiKey], budget: CoreBudget, limits: Ceilings) extends ScholarlyIndex:
  val name = ScholarlyIndex.Name("core")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  override def unavailable: Option[Problem] =
    budget.unavailable(limits, Purpose.Search).map(r => Problem.Unclassified(Legacy.Text(r.describe.value)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) =
    key match
      case None =>
        Left(
          Problem.Unclassified(
            Legacy.Text("no CORE key (plugins.oa-core.api-key or acquire.core.api-key): CORE's search needs one")
          )
        )
      case Some(k) =>
        api
          .searchWorks(
            CoreApi.SearchWorksKey(k.value),
            CoreApi.SearchWorksQuery(s"(${query.value}) AND yearPublished>=${since.getYear}"),
            PageSize(math.max(1, math.min(limit.value, 100))),
            UpstreamQuota.TakePurpose(Purpose.Search.wire.value)
          )
          .left
          .map(f => f.message)
          .map(Candidates.coreWorks)
