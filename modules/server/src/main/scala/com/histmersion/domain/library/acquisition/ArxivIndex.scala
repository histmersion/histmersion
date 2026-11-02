package com.histmersion.domain.library.acquisition

import com.histmersion.upstreams.http.Url

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate

/** arXiv: newest submissions first; the Atom feed has no date filter, so what is older than `since`'s year is dropped
  * here (the stored candidates keep a work found twice from counting as new).
  */
final class ArxivIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = "arxiv"
  def search(query: String, since: LocalDate, limit: Int) =
    http
      .text(
        Url(
          cfg.candidates.arxivApiUrl.value,
          "",
          "search_query" -> s"all:\"$query\"",
          "sortBy" -> "submittedDate",
          "sortOrder" -> "descending",
          "max_results" -> math.max(1, math.min(limit, 100)).toString
        )
      )
      .map(Candidates.arxivWorks(_).filter(_.year.forall(_ >= since.getYear)))
