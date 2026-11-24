package com.histmersion
package api

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Labels:
  type Text = Text.T
  object Text extends RefinedType[String, Pure]:
    given Display[T] = Display(_.value)

  type Title = Title.T
  object Title extends RefinedType[String, Pure]:
    given Display[T] = Display(_.value)

    extension (title: Title) def isBlank: Boolean = title.value.isBlank

  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
