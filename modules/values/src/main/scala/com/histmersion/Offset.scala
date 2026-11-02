package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.numeric.Negative

/** The first row of a page, counted from 0. */
type Offset = Offset.T

object Offset extends RefinedType[Int, Not[Negative]]:
  val Start: Offset = Offset(0)
