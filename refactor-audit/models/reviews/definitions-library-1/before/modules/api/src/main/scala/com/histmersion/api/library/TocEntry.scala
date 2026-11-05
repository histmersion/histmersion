package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class TocEntry(heading: String, ord: Int, chars: Int) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object TocEntry:


  /** A source's table of contents as `get_source` shows it. */
  def markdown(entries: List[TocEntry]): String =
    if entries.isEmpty then "_(no headings in body)_"
    else
      val items = entries.map(e => s"- **${e.heading}** — ord ${e.ord}, ${e.chars} chars")
      s"### Table of Contents (${entries.size} heading(s))\n\n${items.mkString("\n")}"
