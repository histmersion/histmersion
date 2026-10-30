package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TocEntry(heading: String, ord: Int, chars: Int) derives KindlingsCodecAsObject, KindlingsSchema
object TocEntry:
  given Schema[TocEntry] = summon[KindlingsSchema[TocEntry]].schema
