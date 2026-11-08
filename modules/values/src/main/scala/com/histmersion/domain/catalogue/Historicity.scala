package com.histmersion
package domain.catalogue

import io.github.iltotore.iron.constraint.any.Pure
object Historicity:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  val all: List[Wire] = List(Wire(Attested), Wire(Inferred), Wire(Contested), Wire(Legendary), Wire(Mythic))
