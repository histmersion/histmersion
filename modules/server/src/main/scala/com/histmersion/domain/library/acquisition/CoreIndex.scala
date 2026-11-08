package com.histmersion
package domain.library.acquisition

import com.histmersion.api.Problem
import com.histmersion.config.ApiKey
import com.histmersion.domain.*
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.upstreams.PageSize
import com.histmersion.upstreams.core.CoreApi

import java.time.LocalDate
import com.histmersion.api.library.Candidates
import com.histmersion.domain.library.search.Search

/** CORE: only with a key (its search refuses anonymous use) — the `oa-core` plugin's resolved `api-key`, else
  * `acquire.core.api-key` — and through the server's CORE budget, at most `acquire.core.search-daily-tokens` a day.
  */
final class CoreIndex(api: CoreApi, key: Option[ApiKey], budget: CoreBudget, limits: CoreQuota.Limits)
    extends ScholarlyIndex:
  val name = ScholarlyIndex.Name("core")
  override def unavailable: Option[Problem] =
    budget.unavailable(limits, CoreQuota.Purpose.Search).map(r => Problem(r.describe.value))
  def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) =
    key match
      case None =>
        Left(
          Problem(
            "no CORE key (plugins.oa-core.api-key or acquire.core.api-key): CORE's search needs one"
          )
        )
      case Some(k) =>
        api
          .searchWorks(
            CoreApi.SearchWorksKey(k.value),
            CoreApi.SearchWorksQuery(s"(${query.value}) AND yearPublished>=${since.getYear}"),
            PageSize(math.max(1, math.min(limit.value, 100))),
            UpstreamQuota.TakePurpose(CoreQuota.Purpose.Search.wire.value)
          )
          .left
          .map(f => Problem(f.message.value))
          .map(Candidates.coreWorks)
