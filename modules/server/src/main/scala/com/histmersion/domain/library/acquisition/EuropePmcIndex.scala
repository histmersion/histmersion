package com.histmersion
package domain.library.acquisition

import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.Candidates
import com.histmersion.domain.library.search.Search
import com.histmersion.lint.{LintRule, tolerated}

/** Europe PMC: first publication since, newest first. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class EuropePmcIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = ScholarlyIndex.Name("europepmc")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
      .map(Candidates.europepmcWorks)
