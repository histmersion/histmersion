package com.histmersion.domain.library.acquisition

import com.histmersion.api.*
import com.histmersion.domain.*
import com.histmersion.upstreams.core.CoreApi

import java.time.LocalDate

/** CORE: only with a key (its search refuses anonymous use) — the `oa-core` plugin's resolved `api-key`, else
  * `acquire.core.api-key` — and through the server's CORE budget, at most `acquire.core.search-daily-tokens` a day.
  */
final class CoreIndex(api: CoreApi, key: Option[String], budget: CoreBudget, limits: CoreQuota.Limits)
    extends ScholarlyIndex:
  val name = "core"
  override def unavailable: Option[String] = budget.unavailable(limits, CoreQuota.Purpose.Search).map(_.describe)
  def search(query: String, since: LocalDate, limit: Int) =
    key match
      case None    => Left("no CORE key (plugins.oa-core.api-key or acquire.core.api-key): CORE's search needs one")
      case Some(k) =>
        api
          .searchWorks(
            k,
            s"($query) AND yearPublished>=${since.getYear}",
            math.max(1, math.min(limit, 100)),
            CoreQuota.Purpose.Search.wire
          )
          .left
          .map(_.message)
          .map(Candidates.coreWorks)
