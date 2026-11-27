package com.histmersion
package domain.library.findings

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.search.SearchFilters

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.{Filter, GeoHit, GeoHitPage, SearchResult}
import com.histmersion.api.personal.UnreadPage
import com.histmersion.lint.{tolerated, LintRule}

/** The map feed over REST and MCP: the same query parameters as the search; `limit <= 0` means the configured
  * `limits.findings`, which is both the default and the ceiling.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class FindingsControllers(findings: FindingService, filters: SearchFilters, cfg: () => AppConfig):

  /** `GET /api/findings`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def feed(q: Api.FindingsQuery): List[GeoHit] =
    val f = q.toFilter
    val limits = cfg().limits
    val ceiling = limits.findings.value
    findings.feed(
      f.modify(_.limit)
        .setTo(
          Filter.Limit(
            limits.page(PageSize.option(f.limit.value), PageSize.applyUnsafe(math.max(1, ceiling))).value.min(ceiling)
          )
        ),
      filters.scope(f)
    )

  /** `GET /api/findings/page` — [[feed]] with its total, where the page starts and the next offset. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def page(q: Api.FindingsQuery): GeoHitPage =
    val f = q.toFilter
    GeoHitPage.of(
      SearchResult.Total(findings.count(f, filters.scope(f)).value),
      feed(q),
      UnreadPage.Offset(f.offset.value.max(0))
    )
