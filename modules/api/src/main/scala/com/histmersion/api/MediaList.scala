package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaList(
    total: Int,
    items: List[MediaItem],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, items.size)
object MediaList:
  given Schema[MediaList] = summon[KindlingsSchema[MediaList]].schema
  def of(total: Int, rows: List[MediaItem], offset: Int): MediaList =
    MediaList(total, rows, offset, OffsetPage(total, offset, rows.size).next)
