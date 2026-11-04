package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given

final case class MediaList(
    total: Int,
    items: List[MediaItem],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, items.size)
object MediaList:

  def of(total: Int, rows: List[MediaItem], offset: Int): MediaList =
    MediaList(total, rows, offset, OffsetPage(total, offset, rows.size).next)
