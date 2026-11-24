package com.histmersion
package api

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Kernel)
type Offset = Offset.T
object Offset extends Newtype[Int, Pure]
