package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class Note(
    id: NoteId,
    target: Target,
    text: NoteText,
    title: String,
    createdAt: String = "",
    updatedAt: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema

object Note:
  given Schema[Note] = summon[KindlingsSchema[Note]].schema
