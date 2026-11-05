package com.histmersion.web.ui

opaque type Px = Int
object Px:
  def apply(v: Int): Px = v
  extension (p: Px)
    def value: Int = p
    def css: String = s"${p}px"
