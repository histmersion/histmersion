package com.histmersion.web.ui

opaque type CssClass = String
object CssClass:
  type ApplyRaw = ApplyRaw.T
  object ApplyRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueResult = ValueResult.T
  object ValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(raw: com.histmersion.web.ui.CssClass.ApplyRaw): CssClass = raw
  extension (c: CssClass) def value: com.histmersion.web.ui.CssClass.ValueResult = c
