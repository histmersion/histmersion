package com.histmersion
package api

import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
@movesTo(FeaturePlace.Kernel)
type Sha256 = Sha256.T
object Sha256 extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]:
  given Display[T] = Display(_.value)
