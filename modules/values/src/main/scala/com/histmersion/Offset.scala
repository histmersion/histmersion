package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.numeric.Negative
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
type Offset = Offset.T

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
object Offset extends Newtype[Int, Not[Negative]]:
  val Start: Offset = Offset(0)
