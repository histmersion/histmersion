package com.histmersion
package web.ui

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

opaque type Px = Int
object Px:
  type Css = Css.T
  object Css extends Newtype[String, Pure]

  def apply(v: Int): Px = v
  extension (p: Px)
    def value: Int = p
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def css: Css = Css(s"${p}px")
