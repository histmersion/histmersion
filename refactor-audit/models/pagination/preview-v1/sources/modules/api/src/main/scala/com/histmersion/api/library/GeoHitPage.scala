package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given

final case class GeoHitPage(
    total: com.histmersion.api.RowCount,
    hits: List[GeoHit],
    offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
    next: Option[com.histmersion.api.Offset] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(hits.size))
object GeoHitPage:

  def of(total: Int, hits: List[GeoHit], offset: Int): GeoHitPage =
    GeoHitPage(com.histmersion.api.RowCount(total), hits, com.histmersion.api.Offset(offset), OffsetPage(com.histmersion.api.RowCount(total), com.histmersion.api.Offset(offset), com.histmersion.api.RowCount(hits.size)).next)
