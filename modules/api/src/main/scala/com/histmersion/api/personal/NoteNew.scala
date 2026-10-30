package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class NoteNew(target: Target, text: NoteText, title: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object NoteNew:
  given Schema[NoteNew] = summon[KindlingsSchema[NoteNew]].schema
