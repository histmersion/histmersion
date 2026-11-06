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
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def of(total: com.histmersion.api.catalogue.EventList.Total, rows: List[EventSummary], offset: com.histmersion.api.personal.UnreadPage.Offset): EventList =
    EventList(com.histmersion.api.RowCount((total).value), rows, com.histmersion.api.Offset((offset).value), OffsetPage(com.histmersion.api.RowCount((total).value), com.histmersion.api.Offset((offset).value), com.histmersion.api.RowCount(rows.size)).next)
