package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class GeoHitPage(
    total: RowCount,
    hits: List[GeoHit],
    offset: Offset = Offset(0),
    next: Option[Offset] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page: OffsetPage = OffsetPage(total, offset, RowCount(hits.size))
object GeoHitPage:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(total: SearchResult.Total, hits: List[GeoHit], offset: UnreadPage.Offset): GeoHitPage =
    GeoHitPage(
      total.rows,
      hits,
      Offset((offset).value),
      OffsetPage(total.rows, Offset((offset).value), RowCount(hits.size)).next
    )
