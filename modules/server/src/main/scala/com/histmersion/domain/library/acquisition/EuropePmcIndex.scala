package com.histmersion.domain.library.acquisition

import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.Candidates

/** Europe PMC: first publication since, newest first. */
final class EuropePmcIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = com.histmersion.domain.library.acquisition.ScholarlyIndex.Name("europepmc")
  def search(query: com.histmersion.domain.library.search.Search.Query, since: LocalDate, limit: com.histmersion.domain.library.acquisition.EuropePmcIndex.SearchLimit) =
    http
      .json(
        Url(
          cfg.europepmc.url,
          com.histmersion.upstreams.http.Url.UrlPath("/search"),
          "query" -> s"($query) AND FIRST_PDATE:[$since TO 3000-12-31]",
          "format" -> "json",
          "resultType" -> "core",
          "pageSize" -> math.max(1, math.min(limit, 100)).toString,
          "sort" -> "FIRST_PDATE desc"
        )
      )
      .map(Candidates.europepmcWorks)

object EuropePmcIndex:
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
