package com.histmersion.web.ui

opaque type CssClass = String
object CssClass:
  def apply(raw: String): CssClass = raw
  extension (c: CssClass) def value: String = c

opaque type Px = Int
object Px:
  def apply(v: Int): Px = v
  extension (p: Px)
    def value: Int = p
    def css: String = s"${p}px"
