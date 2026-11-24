package com.histmersion
package api.library
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
object Label:
  type Text = Text.T
  object Text extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def display(s: Text): Text = Text((s).value.replace("_", " "))
