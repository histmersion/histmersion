package com.histmersion.domain.catalogue.events

import com.histmersion.api.{EventDating, TimeSpan}
import com.augustnagro.magnum.DbCodec

/** A source's dating of an event. */
final private[events] case class EventDatingRow(
    eventId: String,
    citekey: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    method: Option[String],
    note: Option[String]
) derives DbCodec:
  def toDto: EventDating =
    EventDating(eventId, citekey, TimeSpan(notBefore, beginBy, endFrom, notAfter), method, note)
