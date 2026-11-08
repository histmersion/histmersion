package com.histmersion
package api.catalogue

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object Event:
  type Uid = Uid.T
  object Uid extends RefinedType[String, Pure]
  type Id = Id.T
  object Id extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.constraint.any.Pure]
