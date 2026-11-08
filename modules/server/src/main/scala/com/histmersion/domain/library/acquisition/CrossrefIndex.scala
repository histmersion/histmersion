package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.domain.library.search.Search

/** Crossref: what was DEPOSITED since (`from-created-date`), newest first — a watermark on deposits. */
final class CrossrefIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = ScholarlyIndex.Name("crossref")
  def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) =
    http
      .json(
        Url(
          Http.Url(cfg.crossref.url.value),
          Url.UrlPath("/works"),
          "query" -> query.value,
          "rows" -> math.max(1, math.min(limit.value, 50)).toString,
          "sort" -> "created",
          "order" -> "desc",
          "filter" -> s"from-created-date:$since,type:journal-article,type:book,type:book-chapter,type:monograph,type:edited-book",
          "select" -> "DOI,title,author,issued,type,container-title,is-referenced-by-count,abstract",
          "mailto" -> cfg.mailto.value
        )
      )
      .left
      .map(p => ScholarlyIndex.Problem(p.value))
      .map(AcquireParse.crossrefWorks(_).map(_.wire))
