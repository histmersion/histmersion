package com.histmersion.api.library
import com.histmersion.api.given
object Label:
  type DisplayResult = DisplayResult.T
  object DisplayResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayS = DisplayS.T
  object DisplayS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def display(s: com.histmersion.api.library.Label.DisplayS): com.histmersion.api.library.Label.DisplayResult = s.replace("_", " ")
