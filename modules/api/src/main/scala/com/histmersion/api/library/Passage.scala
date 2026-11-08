package com.histmersion
package api.library

import io.github.iltotore.iron.constraint.any.Pure

object Passage:
  type Excerpt = Excerpt.T
  object Excerpt extends Newtype[String, Pure]
