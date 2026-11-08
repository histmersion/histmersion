package com.histmersion
package util

import io.github.iltotore.iron.{Constraint, RefinedType}
import io.github.iltotore.iron.constraint.any.Pure

object Pair:
  def constraint[A, C](using c: Constraint[A, C]): Constraint[A, C] = c
  type Left = Left.T
  object Left extends RefinedType[String, Pure]
