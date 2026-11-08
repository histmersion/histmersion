package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class TocEntry(heading: TocEntry.Heading, ord: TocEntry.Ord, chars: TocEntry.Chars) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object TocEntry:
  type Markdown = Markdown.T
  object Markdown extends RefinedType[String, Pure]


  /** A source's table of contents as `get_source` shows it. */

  type Heading = Heading.T
  object Heading extends RefinedType[String, Pure]
  type Ord = Ord.T
  object Ord extends RefinedType[Int, Pure]
  type Chars = Chars.T
  object Chars extends RefinedType[Int, Pure]

  def markdown(entries: List[TocEntry]): Markdown =
    if entries.isEmpty then Markdown("_(no headings in body)_")
    else
      val items = entries.map(e => s"- **${e.heading}** — ord ${e.ord}, ${e.chars} chars")
      Markdown(s"### Table of Contents (${entries.size} heading(s))\n\n${items.mkString("\n")}")
