package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

/** Pure helpers for `lint --file`. */
object FileFormat:

  /** `(frontmatter, body)`: a note file opens with its OWN `---` YAML block (what `article put` sends as metadata) —
    * that is not pasted frontmatter, so only the body is checked. A file without one is all body.
    */
  type SplitFrontmatterResultPart1 = SplitFrontmatterResultPart1.T
  object SplitFrontmatterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitFrontmatterResultPart2 = SplitFrontmatterResultPart2.T
  object SplitFrontmatterResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitFrontmatterText = SplitFrontmatterText.T
  object SplitFrontmatterText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def splitFrontmatter(text: com.histmersion.cli.FileFormat.SplitFrontmatterText): (com.histmersion.cli.FileFormat.SplitFrontmatterResultPart1, com.histmersion.cli.FileFormat.SplitFrontmatterResultPart2) =
    val lines = text.split("\n", -1).toList
    if lines.headOption.map(_.trim).contains("---") then
      lines.drop(1).indexWhere(_.trim == "---") match
        case -1 => ("", text)
        case i  =>
          val head = lines.take(i + 2).mkString("\n") + "\n"
          (head, text.drop(head.length))
    else ("", text)
