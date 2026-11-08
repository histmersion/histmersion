package com.histmersion
package api.library
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
object Label:
  type Text = Text.T
  object Text extends RefinedType[String, Pure]

  def display(s: Text): Text = Text((s).value.replace("_", " "))
