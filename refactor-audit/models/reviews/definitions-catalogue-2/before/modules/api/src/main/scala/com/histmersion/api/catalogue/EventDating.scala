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
    method: Option[String] = None,
    note: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
