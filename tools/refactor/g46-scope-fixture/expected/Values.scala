package com.histmersion
package api.library

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Values:
  type Doi = Doi.T
  object Doi extends RefinedType[String, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
