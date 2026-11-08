package com.histmersion
package api.library

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object Refs:
  type ReferenceText = ReferenceText.T
  object ReferenceText extends RefinedType[String, Pure]
