package com.histmersion
package web.ui

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

opaque type Px = Int
object Px:
  type Css = Css.T
  object Css extends RefinedType[String, Pure]

  def apply(v: Int): Px = v
  extension (p: Px)
    def value: Int = p
    def css: Css = Css(s"${p}px")
