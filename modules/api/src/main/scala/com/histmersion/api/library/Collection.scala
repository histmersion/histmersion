package com.histmersion
package api.library

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object Collection:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
