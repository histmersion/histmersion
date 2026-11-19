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

final case class GeoHitPage(
    total: RowCount,
    hits: List[GeoHit],
    offset: Offset = Offset(0),
    next: Option[Offset] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, RowCount(hits.size))
object GeoHitPage:

  def of(total: SearchResult.Total, hits: List[GeoHit], offset: UnreadPage.Offset): GeoHitPage =
    GeoHitPage(
      RowCount((total).value),
      hits,
      Offset((offset).value),
      OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(hits.size)).next
    )
