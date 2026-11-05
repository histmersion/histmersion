package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.acquisition.wire

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate

/** Crossref: what was DEPOSITED since (`from-created-date`), newest first — a watermark on deposits. */
final class CrossrefIndex(cfg: AcquireConfig, http: Http) extends ScholarlyIndex:
  val name = "crossref"
  def search(query: com.histmersion.domain.library.acquisition.CrossrefIndex.SearchQuery, since: LocalDate, limit: com.histmersion.domain.library.acquisition.CrossrefIndex.SearchLimit) =
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

object CrossrefIndex:
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
