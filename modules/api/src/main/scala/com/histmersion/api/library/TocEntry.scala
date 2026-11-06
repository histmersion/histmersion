package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class TocEntry(heading: TocEntry.Heading, ord: TocEntry.Ord, chars: TocEntry.Chars) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
object TocEntry:
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  /** A source's table of contents as `get_source` shows it. */

  type Heading = Heading.T
  object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ord = Ord.T
  object Ord extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Chars = Chars.T
  object Chars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def markdown(entries: List[TocEntry]): com.histmersion.api.library.TocEntry.Markdown =
    if entries.isEmpty then _root_.com.histmersion.api.library.TocEntry.Markdown("_(no headings in body)_")
    else
      val items = entries.map(e => s"- **${e.heading}** — ord ${e.ord}, ${e.chars} chars")
      _root_.com.histmersion.api.library.TocEntry.Markdown(s"### Table of Contents (${entries.size} heading(s))\n\n${items.mkString("\n")}")
