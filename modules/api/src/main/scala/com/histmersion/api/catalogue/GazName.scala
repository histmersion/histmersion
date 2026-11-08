package com.histmersion
package api.catalogue

import com.histmersion.api.Span
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class GazName(name: GazName.Name, lang: GazName.Lang = GazName.Lang(""), span: Span = Span.always, citation: Option[GazName.Citation] = None)

object GazName:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Lang = Lang.T
  object Lang extends RefinedType[String, Pure]
  type Citation = Citation.T
  object Citation extends RefinedType[String, Pure]
