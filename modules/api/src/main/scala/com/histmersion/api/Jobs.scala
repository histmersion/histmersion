package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Kernel)
object Jobs:
  type Filter = Filter.T
  object Filter extends Newtype[String, Pure]
