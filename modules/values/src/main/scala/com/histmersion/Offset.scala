package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.numeric.Negative

type Offset = Offset.T

object Offset extends Newtype[Int, Not[Negative]]:
  val Start: Offset = Offset(0)
