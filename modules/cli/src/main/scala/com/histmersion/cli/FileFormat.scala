package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

/** Pure helpers for `lint --file`. */
object FileFormat:
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Frontmatter = Frontmatter.T
  object Frontmatter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `(frontmatter, body)`: a note file opens with its OWN `---` YAML block (what `article put` sends as metadata) —
    * that is not pasted frontmatter, so only the body is checked. A file without one is all body.
    */

  def splitFrontmatter(text: com.histmersion.cli.FileFormat.Text): (com.histmersion.cli.FileFormat.Frontmatter, com.histmersion.cli.FileFormat.Body) =
    val raw = text.value
    val lines = raw.split("\n", -1).toList
    if lines.headOption.map(_.trim).contains("---") then
      lines.drop(1).indexWhere(_.trim == "---") match
        case -1 => (com.histmersion.cli.FileFormat.Frontmatter(""), com.histmersion.cli.FileFormat.Body(raw))
        case i  =>
          val head = lines.take(i + 2).mkString("\n") + "\n"
          (com.histmersion.cli.FileFormat.Frontmatter(head), com.histmersion.cli.FileFormat.Body(raw.drop(head.length)))
    else (com.histmersion.cli.FileFormat.Frontmatter(""), com.histmersion.cli.FileFormat.Body(raw))
