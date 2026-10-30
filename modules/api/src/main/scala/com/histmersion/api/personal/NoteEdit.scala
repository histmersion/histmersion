package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class NoteEdit(text: NoteText, ifUpdatedAt: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object NoteEdit:
  given Schema[NoteEdit] = summon[KindlingsSchema[NoteEdit]].schema
