package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventList(
    total: Int,
    events: List[EventSummary],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, events.size)
object EventList:
  given Schema[EventList] = summon[KindlingsSchema[EventList]].schema
  def of(total: Int, rows: List[EventSummary], offset: Int): EventList =
    EventList(total, rows, offset, OffsetPage(total, offset, rows.size).next)
