package com.histmersion
package domain.library.acquisition

import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.Candidates
import com.histmersion.domain.library.search.Search
import com.histmersion.lint.{tolerated, LintRule}

/** arXiv: newest submissions first; the Atom feed has no date filter, so what is older than `since`'s year is dropped
  * here (the stored candidates keep a work found twice from counting as new).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class ArxivIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = ScholarlyIndex.Name("arxiv")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) =
    http
      .text(
        Url(
          Http.Url(cfg.candidates.arxivApiUrl.value),
          Url.UrlPath(""),
          "search_query" -> s"all:\"$query\"",
          "sortBy" -> "submittedDate",
          "sortOrder" -> "descending",
          "max_results" -> math.max(1, math.min(limit.value, 100)).toString
        )
      )
      .map(t => Candidates.arxivWorks(t.value).filter(_.year.forall(_.value >= since.getYear)))
