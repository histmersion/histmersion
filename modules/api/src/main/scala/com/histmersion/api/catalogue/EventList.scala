package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given

final case class EventList(
    total: Int,
    events: List[EventSummary],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, events.size)
object EventList:

  def of(total: Int, rows: List[EventSummary], offset: Int): EventList =
    EventList(total, rows, offset, OffsetPage(total, offset, rows.size).next)
