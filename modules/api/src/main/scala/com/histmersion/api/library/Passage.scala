package com.histmersion
package api.library

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object Passage:
  type Excerpt = Excerpt.T
  object Excerpt extends RefinedType[String, Pure]
