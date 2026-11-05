package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventList(
    total: com.histmersion.api.RowCount,
    events: List[EventSummary],
    offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
    next: Option[com.histmersion.api.Offset] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(events.size))
object EventList:

  def of(total: Int, rows: List[EventSummary], offset: Int): EventList =
    EventList(com.histmersion.api.RowCount(total), rows, com.histmersion.api.Offset(offset), OffsetPage(com.histmersion.api.RowCount(total), com.histmersion.api.Offset(offset), com.histmersion.api.RowCount(rows.size)).next)
