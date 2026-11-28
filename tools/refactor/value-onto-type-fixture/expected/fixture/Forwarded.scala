package com.histmersion
package fixture

import io.github.iltotore.iron.constraint.any.Pure

object Canon:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

object Alias:
  export Canon.Name as Label

object Forwarded:
  def again(n: Canon.Name): Alias.Label = n
  def assumed(n: Alias.Label): Canon.Name = n
