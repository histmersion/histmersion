package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventParticipant(
    eventId: com.histmersion.api.catalogue.Event.Id,
    actor: String,
    role: String = "participant",
    citekey: Option[com.histmersion.api.library.Citekey] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
