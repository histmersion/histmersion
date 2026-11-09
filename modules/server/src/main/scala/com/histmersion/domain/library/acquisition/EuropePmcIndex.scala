package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.Problem
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.Candidates
import com.histmersion.domain.library.search.Search

/** Europe PMC: first publication since, newest first. */
final class EuropePmcIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = ScholarlyIndex.Name("europepmc")
  def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) =
    http
      .json(
        Url(
          Http.Url(cfg.europepmc.url.value),
          Url.UrlPath("/search"),
          "query" -> s"($query) AND FIRST_PDATE:[$since TO 3000-12-31]",
          "format" -> "json",
          "resultType" -> "core",
          "pageSize" -> math.max(1, math.min(limit.value, 100)).toString,
          "sort" -> "FIRST_PDATE desc"
        )
      )
      .left
      .map(_.problem)
      .map(Candidates.europepmcWorks)
