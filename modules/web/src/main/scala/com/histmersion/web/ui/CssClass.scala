package com.histmersion.web.ui

opaque type CssClass = String
object CssClass:
  def apply(raw: String): CssClass = raw
  extension (c: CssClass) def value: String = c
