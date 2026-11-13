package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.OffsetPage
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.personal.UnreadPage

final case class EventList(
    total: RowCount,
    events: List[EventSummary],
    offset: Offset = Offset(0),
    next: Option[Offset] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def page: OffsetPage = OffsetPage(total, offset, RowCount(events.size))
object EventList:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]

  def of(total: Total, rows: List[EventSummary], offset: UnreadPage.Offset): EventList =
    EventList(
      RowCount((total).value),
      rows,
      Offset((offset).value),
      OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(rows.size)).next
    )
