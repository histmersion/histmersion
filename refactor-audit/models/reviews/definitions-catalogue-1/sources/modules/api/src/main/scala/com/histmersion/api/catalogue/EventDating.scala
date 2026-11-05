package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventDating(
    eventId: com.histmersion.api.catalogue.Event.Id,
    citekey: com.histmersion.api.library.Citekey,
    timeSpan: TimeSpan,
    method: Option[EventDating.Method] = None,
    note: Option[EventDating.Note] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventDating:
  type Method = Method.T
  object Method extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
