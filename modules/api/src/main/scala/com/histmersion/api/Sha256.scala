package com.histmersion
package api

import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}
@movesTo(FeaturePlace.Kernel)
type Sha256 = Sha256.T
object Sha256 extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
