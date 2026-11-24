package com.histmersion
package api.text

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
object SafeName:
  type Sanitized = Sanitized.T
  object Sanitized extends Newtype[String, Pure]

  /** The safe name of `raw`; `image.png` when nothing is left. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(raw: String): Sanitized =
    val base = (raw).replace('\\', '/').split('/').lastOption.getOrElse("")
    val cleaned = base
      .map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '-')
      .mkString
      .replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then Sanitized("image.png") else Sanitized(cleaned)
