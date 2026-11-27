package com.histmersion
package cli

import com.histmersion.config.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Pure helpers for `lint --file`. */
@atBoundary(BoundaryKind.CliArgs, "splits a note file into frontmatter and body for `histmersion lint --file`")
object FileFormat:
  type Body = Body.T
  object Body extends Newtype[String, Pure]
  type Frontmatter = Frontmatter.T
  object Frontmatter extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** `(frontmatter, body)`: a note file opens with its OWN `---` YAML block (what `article put` sends as metadata) —
    * that is not pasted frontmatter, so only the body is checked. A file without one is all body.
    */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def splitFrontmatter(text: Text): (Frontmatter, Body) =
    val raw = text.value
    val lines = raw.split("\n", -1).toList
    if lines.headOption.map(_.trim).contains("---") then
      lines.drop(1).indexWhere(_.trim == "---") match
        case -1 => (Frontmatter(""), Body(raw))
        case i  =>
          val head = lines.take(i + 2).mkString("\n") + "\n"
          (Frontmatter(head), Body(raw.drop(head.length)))
    else (Frontmatter(""), Body(raw))
