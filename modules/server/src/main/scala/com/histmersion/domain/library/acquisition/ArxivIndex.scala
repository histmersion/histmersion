package com.histmersion.domain.library.acquisition

import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.Candidates

/** arXiv: newest submissions first; the Atom feed has no date filter, so what is older than `since`'s year is dropped
  * here (the stored candidates keep a work found twice from counting as new).
  */
final class ArxivIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = com.histmersion.domain.library.acquisition.ScholarlyIndex.Name("arxiv")
  def search(query: com.histmersion.domain.library.search.Search.Query, since: LocalDate, limit: com.histmersion.domain.library.acquisition.ArxivIndex.SearchLimit) =
    http
      .text(
        Url(
          cfg.candidates.arxivApiUrl.value,
          com.histmersion.upstreams.http.Url.UrlPath(""),
          "search_query" -> s"all:\"$query\"",
          "sortBy" -> "submittedDate",
          "sortOrder" -> "descending",
          "max_results" -> math.max(1, math.min(limit, 100)).toString
        )
      )
      .map(Candidates.arxivWorks(_).filter(_.year.forall(_ >= since.getYear)))

object ArxivIndex:
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
