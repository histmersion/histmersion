package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Year = Year.T
object Year extends RefinedType[Int, Pure]

object Literals:
  val bce: Year = Year(-3300)
  val ce: Year = Year.apply(2026)
  val checked: Citekey = Citekey.applyUnsafe("checked") // assert: GuidelineInventory.G44
  def computed(n: Int): Year = Year(n + 1) // assert: GuidelineInventory.G44
