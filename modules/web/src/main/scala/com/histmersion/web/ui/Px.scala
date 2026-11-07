package com.histmersion.web.ui

opaque type Px = Int
object Px:
  type Css = Css.T
  object Css extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyV = ApplyV.T
  object ApplyV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueResult = ValueResult.T
  object ValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(v: com.histmersion.web.ui.Px.ApplyV): Px = v
  extension (p: Px)
    def value: com.histmersion.web.ui.Px.ValueResult = p
    def css: com.histmersion.web.ui.Px.Css = s"${p}px"
