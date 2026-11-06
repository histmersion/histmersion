package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class GazDescription(text: GazDescription.Text, lang: GazDescription.Lang = _root_.com.histmersion.api.catalogue.GazName.Lang(""))

object GazDescription:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lang = com.histmersion.api.catalogue.GazName.Lang
  val Lang: _root_.com.histmersion.api.catalogue.GazName.Lang.type = _root_.com.histmersion.api.catalogue.GazName.Lang
