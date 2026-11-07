package com.histmersion.domain.library.findings

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.search.SearchFilters

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.{Filter, GeoHit, GeoHitPage, SearchResult}
import com.histmersion.api.personal.UnreadPage

/** The map feed over REST and MCP: the same query parameters as the search; `limit <= 0` means the configured
  * `limits.findings`, which is both the default and the ceiling.
  */
final class FindingsControllers(findings: FindingService, filters: SearchFilters, cfg: () => AppConfig):

  /** `GET /api/findings`. */
  def feed(q: Api.FindingsQuery): List[GeoHit] =
    val f = q.toFilter
    val limits = cfg().limits
    val ceiling = limits.findings.value
    findings.feed(
      f.modify(_.limit)
        .setTo(Filter.Limit(limits.page(com.histmersion.PageSize.option(f.limit.value), com.histmersion.PageSize.applyUnsafe(math.max(1, ceiling))).value.min(ceiling))),
      filters.scope(f)
    )

  /** `GET /api/findings/page` — [[feed]] with its total, where the page starts and the next offset. */
  def page(q: Api.FindingsQuery): GeoHitPage =
    val f = q.toFilter
    GeoHitPage.of(SearchResult.Total(findings.count(f, filters.scope(f)).value), feed(q), UnreadPage.Offset(f.offset.value.max(0)))
