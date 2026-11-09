package com.histmersion
package util.problem

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

// G60external: outside text is read only at a render edge (Edge.scala) or in its own companion
object External:
  type Reported = Reported.T
  object Reported extends RefinedType[String, Pure]:
    def length(r: Reported): Int = r.value.length // no finding: its own companion

  def leaked(r: Reported): String = r.value // assert: GuidelineInventory (G60external, also G44 and G3misplaced)
