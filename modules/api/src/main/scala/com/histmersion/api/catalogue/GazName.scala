package com.histmersion.api.catalogue

import com.histmersion.api.Span
import com.histmersion.api.given
final case class GazName(name: GazName.Name, lang: GazName.Lang = "", span: Span = Span.always, citation: Option[GazName.Citation] = None)

object GazName:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lang = Lang.T
  object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citation = Citation.T
  object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
