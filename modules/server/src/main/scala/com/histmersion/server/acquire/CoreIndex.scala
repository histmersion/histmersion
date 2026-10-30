package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate

/** CORE: only with a key (its search refuses anonymous use) — the `oa-core` plugin's resolved `api-key`, else
  * `acquire.core.api-key` — and through the server's CORE budget, at most `acquire.core.search-daily-tokens` a day.
  */
final class CoreIndex(api: CoreApi, key: Option[String]) extends ScholarlyIndex:
  val name = "core"
  override def unavailable: Option[String] = api.unavailable(CoreQuota.Purpose.Search)
  def search(query: String, since: LocalDate, limit: Int) =
    key match
      case None    => Left("no CORE key (plugins.oa-core.api-key or acquire.core.api-key): CORE's search needs one")
      case Some(k) =>
        api
          .searchWorks(
            k,
            s"($query) AND yearPublished>=${since.getYear}",
            math.max(1, math.min(limit, 100)),
            CoreQuota.Purpose.Search
          )
          .left
          .map(_.message)
          .map(Candidates.coreWorks)
