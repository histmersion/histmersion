package com.histmersion
package api

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object AllowanceWindow:
  type Minutes = Minutes.T
  object Minutes extends RefinedType[Long, Pure]
