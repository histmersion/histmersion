package com.histmersion.web.ui

opaque type Px = Int
object Px:
  type Css = Css.T
  object Css extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(v: Int): Px = v
  extension (p: Px)
    def value: Int = p
    def css: com.histmersion.web.ui.Px.Css = Css(s"${p}px")
