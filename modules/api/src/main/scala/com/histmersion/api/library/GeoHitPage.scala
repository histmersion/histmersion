package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class GeoHitPage(
    total: com.histmersion.api.RowCount,
    hits: List[GeoHit],
    offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
    next: Option[com.histmersion.api.Offset] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(hits.size))
object GeoHitPage:


  def of(total: com.histmersion.api.library.SearchResult.Total, hits: List[GeoHit], offset: com.histmersion.api.personal.UnreadPage.Offset): GeoHitPage =
    GeoHitPage(com.histmersion.api.RowCount((total).value), hits, com.histmersion.api.Offset((offset).value), OffsetPage(com.histmersion.api.RowCount((total).value), com.histmersion.api.Offset((offset).value), com.histmersion.api.RowCount(hits.size)).next)
