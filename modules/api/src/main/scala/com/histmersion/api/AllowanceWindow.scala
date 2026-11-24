package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Kernel)
object AllowanceWindow:
  type Minutes = Minutes.T
  object Minutes extends Newtype[Long, Pure]
