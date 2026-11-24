package com.histmersion
package domain.personal

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The shape of a bookmark's or a note's id, which the server gives them: a UUID in its lower-case text form. */
object PersonalUuid:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  private val Shape = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matches(s: Text): Boolean = Shape.matches(s.value)
