package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage

final case class MediaList(
    total: RowCount,
    items: List[MediaItem],
    offset: Offset = Offset(0),
    next: Option[Offset] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, RowCount(items.size))
object MediaList:

  def of(total: RowCount, rows: List[MediaItem], offset: UnreadPage.Offset): MediaList =
    MediaList(
      RowCount((total).value),
      rows,
      Offset((offset).value),
      OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(rows.size)).next
    )
