package com.histmersion
package fixtures.hellolib

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "a test library standing in for a third-party one: it wraps and unwraps its values directly")
@tolerated(LintRule.G3Misplaced, "a test library standing in for a third-party one: Shout(text) is its public entry")
object Shout:
  type Shouted = Shouted.T
  object Shouted
      extends RefinedType[
        String,
        Pure
      ]
  type ApplyText = ApplyText.T
  object ApplyText
      extends RefinedType[
        String,
        Pure
      ]

  def apply(
      text: ApplyText
  ): Shouted =
    Shouted((text).value.toUpperCase(java.util.Locale.ROOT) + "!")
