package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate

/** Crossref: what was DEPOSITED since (`from-created-date`), newest first — a watermark on deposits. */
final class CrossrefIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = "crossref"
  def search(query: String, since: LocalDate, limit: Int) =
    http
      .json(
        Url(
          cfg.crossref.url,
          "/works",
          "query" -> query,
          "rows" -> math.max(1, math.min(limit, 50)).toString,
          "sort" -> "created",
          "order" -> "desc",
          "filter" -> s"from-created-date:$since,type:journal-article,type:book,type:book-chapter,type:monograph,type:edited-book",
          "select" -> "DOI,title,author,issued,type,container-title,is-referenced-by-count,abstract",
          "mailto" -> cfg.mailto
        )
      )
      .map(AcquireParse.crossrefWorks(_).map(_.wire))
