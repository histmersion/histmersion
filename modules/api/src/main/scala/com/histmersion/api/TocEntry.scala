package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── get_source sectioning (plan §6 A1/A7/A18) ─────────────────────────────────────────────────

/** One entry in the table of contents: a heading from `source_chunks`, its first chunk `ord` and the total characters
  * under it.
  */
final case class TocEntry(heading: String, ord: Int, chars: Int) derives KindlingsCodecAsObject, KindlingsSchema
object TocEntry:
  given Schema[TocEntry] = summon[KindlingsSchema[TocEntry]].schema
