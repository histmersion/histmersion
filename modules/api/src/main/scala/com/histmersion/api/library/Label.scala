package com.histmersion.api.library
import com.histmersion.api.given
object Label:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def display(s: com.histmersion.api.library.Label.Text): com.histmersion.api.library.Label.Text = _root_.com.histmersion.api.library.Label.Text((s).value.replace("_", " "))
