package com.histmersion
package config

import io.github.iltotore.iron.{Constraint, RefinedType}
import io.github.iltotore.iron.constraint.any.Pure

/** A reader that names the Mirror keeps its import. */
trait Reader[T]:
  def read(s: String): Option[T]

object Readers:
  given reader: [T] => (m: RefinedType.Mirror[T]) => Reader[T] = s => None
  def checked[A, C](using c: Constraint[A, C]): Constraint[A, C] = c
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
