package com.histmersion.domain.library.findings

import com.histmersion.api.*
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.search.SearchFilters

import hearth.kindlings.optics.syntax.*

/** The map feed over REST and MCP: the same query parameters as the search; `limit <= 0` means the configured
  * `limits.findings`, which is both the default and the ceiling.
  */
final class FindingsControllers(findings: FindingService, filters: SearchFilters, cfg: () => AppConfig):

  /** `GET /api/findings`. */
  def feed(q: Api.FindingsQuery): List[GeoHit] =
    val f = q.toFilter
    val limits = cfg().limits
    findings.feed(
      f.modify(_.limit).setTo(limits.page(Some(f.limit), limits.findings).min(limits.findings)),
      filters.scope(f)
    )

  /** `GET /api/findings/page` — [[feed]] with its total, where the page starts and the next offset. */
  def page(q: Api.FindingsQuery): GeoHitPage =
    val f = q.toFilter
    GeoHitPage.of(findings.count(f, filters.scope(f)), feed(q), f.offset.max(0))
