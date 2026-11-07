package com.histmersion.domain.library.acquisition

import com.histmersion.domain.*
import com.histmersion.upstreams.core.CoreApi

import java.time.LocalDate
import com.histmersion.api.library.Candidates

/** CORE: only with a key (its search refuses anonymous use) — the `oa-core` plugin's resolved `api-key`, else
  * `acquire.core.api-key` — and through the server's CORE budget, at most `acquire.core.search-daily-tokens` a day.
  */
final class CoreIndex(api: CoreApi, key: Option[CoreIndex.Key], budget: CoreBudget, limits: CoreQuota.Limits)
    extends ScholarlyIndex:
  val name = "core"
  override def unavailable: Option[String] = budget.unavailable(limits, CoreQuota.Purpose.Search).map(_.describe)
  def search(query: com.histmersion.domain.library.search.Search.Query, since: LocalDate, limit: com.histmersion.domain.library.acquisition.CoreIndex.SearchLimit) =
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

object CoreIndex:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
