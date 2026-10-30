package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A note's new text. With `ifUpdatedAt` (the `updatedAt` the person saw) the change is refused (412) when the note
  * changed since — in another window, or from the command line.
  */
final case class NoteEdit(text: NoteText, ifUpdatedAt: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object NoteEdit:
  given Schema[NoteEdit] = summon[KindlingsSchema[NoteEdit]].schema
