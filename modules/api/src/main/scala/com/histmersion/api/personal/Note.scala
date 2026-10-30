package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A note: what it was left on (and opens back to), what the person wrote (Markdown), and what the item is called now
  * (`title`, as for a [[Bookmark]]). `updatedAt` is what an edit names to be sure it changes what the person saw.
  */
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
