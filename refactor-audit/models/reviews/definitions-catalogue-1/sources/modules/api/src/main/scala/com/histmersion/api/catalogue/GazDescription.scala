package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class GazDescription(text: GazDescription.Text, lang: GazDescription.Lang = "")

object GazDescription:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lang = Lang.T
  object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
