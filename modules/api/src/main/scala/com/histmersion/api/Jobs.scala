package com.histmersion
package api

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object Jobs:
  type Filter = Filter.T
  object Filter extends RefinedType[String, Pure]
