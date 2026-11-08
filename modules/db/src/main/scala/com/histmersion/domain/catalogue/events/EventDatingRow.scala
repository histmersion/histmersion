package com.histmersion
package domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.EventDating
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.Year

/** A source's dating of an event. */
final private[events] case class EventDatingRow(
    eventId: Event.Id,
    citekey: Citekey,
    notBefore: Option[DeepTime.SignedYear],
    beginBy: Option[DeepTime.SignedYear],
    endFrom: Option[DeepTime.SignedYear],
    notAfter: Option[DeepTime.SignedYear],
    method: Option[EventDating.Method],
    note: Option[EventDating.Note]
) derives DbCodec:
  def toDto: EventDating =
    def year(y: Option[DeepTime.SignedYear]) = y.map(v => Year(v.value))
    EventDating(
      com.histmersion.api.catalogue.Event.Id(eventId.value),
      com.histmersion.api.library.Citekey(citekey.value),
      TimeSpan(year(notBefore), year(beginBy), year(endFrom), year(notAfter)),
      method,
      note
    )
