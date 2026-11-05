package com.histmersion.domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.EventDating
import com.augustnagro.magnum.DbCodec

/** A source's dating of an event. */
final private[events] case class EventDatingRow(
    eventId: com.histmersion.domain.catalogue.Event.Id,
    citekey: com.histmersion.domain.library.Citekey,
    notBefore: Option[EventDatingRow.NotBefore],
    beginBy: Option[EventDatingRow.BeginBy],
    endFrom: Option[EventDatingRow.EndFrom],
    notAfter: Option[EventDatingRow.NotAfter],
    method: Option[EventDatingRow.Method],
    note: Option[EventDatingRow.Note]
) derives DbCodec:
  def toDto: EventDating =
    EventDating(com.histmersion.api.catalogue.Event.Id(eventId), com.histmersion.api.library.Citekey(citekey), TimeSpan(notBefore, beginBy, endFrom, notAfter), method, note)

object EventDatingRow:
  type NotBefore = NotBefore.T
  object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Method = Method.T
  object Method extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
