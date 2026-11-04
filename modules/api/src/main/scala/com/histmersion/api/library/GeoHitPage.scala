package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given

final case class GeoHitPage(
    total: Int,
    hits: List[GeoHit],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, hits.size)
object GeoHitPage:

  def of(total: Int, hits: List[GeoHit], offset: Int): GeoHitPage =
    GeoHitPage(total, hits, offset, OffsetPage(total, offset, hits.size).next)
