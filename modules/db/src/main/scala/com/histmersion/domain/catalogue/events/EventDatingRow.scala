package com.histmersion.domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.EventDating
import com.augustnagro.magnum.DbCodec

/** A source's dating of an event. */
final private[events] case class EventDatingRow(
    eventId: com.histmersion.domain.catalogue.Event.Id,
    citekey: com.histmersion.domain.library.Citekey,
    notBefore: Option[com.histmersion.domain.DeepTime.SignedYear],
    beginBy: Option[com.histmersion.domain.DeepTime.SignedYear],
    endFrom: Option[com.histmersion.domain.DeepTime.SignedYear],
    notAfter: Option[com.histmersion.domain.DeepTime.SignedYear],
    method: Option[EventDating.Method],
    note: Option[EventDating.Note]
) derives DbCodec:
  def toDto: EventDating =
    def year(y: Option[com.histmersion.domain.DeepTime.SignedYear]) = y.map(v => com.histmersion.api.catalogue.Year(v.value))
    EventDating(com.histmersion.api.catalogue.Event.Id(eventId.value), com.histmersion.api.library.Citekey(citekey.value), TimeSpan(year(notBefore), year(beginBy), year(endFrom), year(notAfter)), method, note)
