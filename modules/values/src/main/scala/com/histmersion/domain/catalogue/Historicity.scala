package com.histmersion
package domain.catalogue

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
object Historicity:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[Wire] = List(Wire(Attested), Wire(Inferred), Wire(Contested), Wire(Legendary), Wire(Mythic))
