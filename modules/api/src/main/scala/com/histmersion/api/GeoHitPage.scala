package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class GeoHitPage(
    total: Int,
    hits: List[GeoHit],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, hits.size)
object GeoHitPage:
  given Schema[GeoHitPage] = summon[KindlingsSchema[GeoHitPage]].schema
  def of(total: Int, hits: List[GeoHit], offset: Int): GeoHitPage =
    GeoHitPage(total, hits, offset, OffsetPage(total, offset, hits.size).next)
