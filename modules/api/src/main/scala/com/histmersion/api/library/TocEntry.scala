package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

final case class TocEntry(heading: TocEntry.Heading, ord: TocEntry.Ord, chars: TocEntry.Chars)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object TocEntry:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  /** A source's table of contents as `get_source` shows it. */

  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Ord = Ord.T
  object Ord extends Newtype[Int, Pure]
  type Chars = Chars.T
  object Chars extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown(entries: List[TocEntry]): Markdown =
    if entries.isEmpty then Markdown("_(no headings in body)_")
    else
      val items = entries.map(e => s"- **${e.heading}** — ord ${e.ord}, ${e.chars} chars")
      Markdown(s"### Table of Contents (${entries.size} heading(s))\n\n${items.mkString("\n")}")
