package com.histmersion
package api.library

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object Search:
  type Snippet = Snippet.T
  object Snippet extends RefinedType[String, Pure]
