package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
final case class GazDescription(text: GazDescription.Text, lang: GazDescription.Lang = GazName.Lang(""))

object GazDescription:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  export GazName.Lang
