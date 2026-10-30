package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One source's picture, done or (in a dry run) to do: its role (`cover`, `illustration`), where it comes from
  * (`calibre`, `open-library`, `source` — one of its own figures) and what (a figure's name, an ISBN, a Calibre id).
  */
final case class MediaSourceLine(citekey: String, role: String, provider: String, detail: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaSourceLine:
  given Schema[MediaSourceLine] = summon[KindlingsSchema[MediaSourceLine]].schema
